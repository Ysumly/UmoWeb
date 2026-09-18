package com.ysumly.umowebbackend.service.impl.admin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.config.AiProperties;
import com.ysumly.umowebbackend.model.dto.AiTransformRequest;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import com.ysumly.umowebbackend.service.admin.AiModeRuntimeConfig;
import com.ysumly.umowebbackend.service.ai.AiProviderErrorCategory;
import com.ysumly.umowebbackend.service.ai.AiProviderException;
import com.ysumly.umowebbackend.service.ai.AiProviderRequest;
import com.ysumly.umowebbackend.service.ai.AiProviderResult;
import com.ysumly.umowebbackend.service.ai.AiResultValidator;
import com.ysumly.umowebbackend.service.ai.AiTransformProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiTransformServiceImplTest {

    private final AiProperties properties = enabledProperties();
    private final AiModeCatalogService modeCatalogService =
            mock(AiModeCatalogService.class);
    private final AiTransformProvider provider = mock(AiTransformProvider.class);
    private final AiRequestGuard requestGuard = mock(AiRequestGuard.class);
    private final AiRequestGuard.Lease lease = mock(AiRequestGuard.Lease.class);
    private final AiResultValidator resultValidator = mock(AiResultValidator.class);
    private final AiTransformServiceImpl service = new AiTransformServiceImpl(
            properties,
            modeCatalogService,
            provider,
            requestGuard,
            resultValidator);

    @Test
    void transformReturnsFrozenResultShape() {
        when(modeCatalogService.findByKey("STRUCTURE_CLEANUP"))
                .thenReturn(Optional.of(mode(true)));
        when(requestGuard.acquire()).thenReturn(lease);
        when(provider.transform(any(AiProviderRequest.class)))
                .thenReturn(new AiProviderResult(
                        "RESULT",
                        "stop",
                        11,
                        7,
                        18,
                        "upstream-1"));

        var result = service.transform(
                new AiTransformRequest("STRUCTURE_CLEANUP", "SOURCE"));

        assertThat(result.requestId()).isNotBlank();
        assertThat(result.modeKey()).isEqualTo("STRUCTURE_CLEANUP");
        assertThat(result.modeVersion()).isEqualTo(2);
        assertThat(result.content()).isEqualTo("RESULT");
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.usage().inputTokens()).isEqualTo(11);
        assertThat(result.usage().outputTokens()).isEqualTo(7);
        assertThat(result.usage().totalTokens()).isEqualTo(18);
        verify(resultValidator).validate(
                AiValidationProfile.NONE,
                "SOURCE",
                "RESULT",
                60_000);
        verify(lease).close();
    }

    @Test
    void unknownModeReturnsNotFound() {
        when(modeCatalogService.findByKey("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transform(
                new AiTransformRequest("UNKNOWN", "SOURCE")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("请求 ID");

        verifyNoInteractions(provider, requestGuard, resultValidator);
    }

    @Test
    void disabledModeReturnsConflict() {
        when(modeCatalogService.findByKey("DISABLED"))
                .thenReturn(Optional.of(mode(false)));

        assertThatThrownBy(() -> service.transform(
                new AiTransformRequest("DISABLED", "SOURCE")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(409));

        verify(requestGuard, never()).acquire();
        verifyNoInteractions(provider, resultValidator);
    }

    @Test
    void blankAndOversizedInputReturnBadRequest() {
        assertBadRequest(new AiTransformRequest("MODE", " "), "请先输入正文");
        assertBadRequest(
                new AiTransformRequest("MODE", "a".repeat(20_001)),
                "20000");

        verifyNoInteractions(modeCatalogService, provider, requestGuard, resultValidator);
    }

    @Test
    void disabledRuntimeReturnsConflict() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.transform(
                new AiTransformRequest("MODE", "SOURCE")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(409))
                .hasMessageContaining("AI 功能未启用");

        verifyNoInteractions(modeCatalogService, provider, requestGuard, resultValidator);
    }

    @Test
    void mapsProviderErrorsToFrozenHttpStatuses() {
        when(modeCatalogService.findByKey("MODE"))
                .thenReturn(Optional.of(mode(true)));
        when(requestGuard.acquire()).thenReturn(lease);

        assertProviderMapping(AiProviderErrorCategory.AUTHENTICATION, 503);
        assertProviderMapping(AiProviderErrorCategory.INSUFFICIENT_BALANCE, 503);
        assertProviderMapping(AiProviderErrorCategory.UNAVAILABLE, 503);
        assertProviderMapping(AiProviderErrorCategory.RATE_LIMITED, 429);
        assertProviderMapping(AiProviderErrorCategory.INVALID_REQUEST, 502);
        assertProviderMapping(AiProviderErrorCategory.INVALID_RESPONSE, 502);
        assertProviderMapping(AiProviderErrorCategory.TIMEOUT, 504);
    }

    @Test
    void releasesLeaseWhenProviderFails() {
        when(modeCatalogService.findByKey("MODE"))
                .thenReturn(Optional.of(mode(true)));
        when(requestGuard.acquire()).thenReturn(lease);
        when(provider.transform(any(AiProviderRequest.class)))
                .thenThrow(new AiProviderException(AiProviderErrorCategory.TIMEOUT));

        assertThatThrownBy(() -> service.transform(
                new AiTransformRequest("MODE", "SOURCE")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(504));

        verify(lease).close();
    }

    @Test
    void logsMetadataWithoutContentPromptResultOrKey() {
        properties.setEnabled(true);
        properties.getDeepseek().setApiKey("KEY-SECRET");
        when(modeCatalogService.findByKey("STRUCTURE_CLEANUP"))
                .thenReturn(Optional.of(mode(
                        true,
                        "TOP SECRET SYSTEM")));
        when(requestGuard.acquire()).thenReturn(lease);
        when(provider.transform(any(AiProviderRequest.class)))
                .thenReturn(new AiProviderResult(
                        "PRIVATE RESULT",
                        "stop",
                        1,
                        2,
                        3,
                        null));

        Logger logger = (Logger) LoggerFactory.getLogger(AiTransformServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.transform(new AiTransformRequest(
                    "STRUCTURE_CLEANUP",
                    "PRIVATE CONTENT"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(logs)
                .contains("requestId=")
                .contains("modeKey=STRUCTURE_CLEANUP")
                .contains("status=success")
                .doesNotContain("TOP SECRET SYSTEM")
                .doesNotContain("PRIVATE CONTENT")
                .doesNotContain("PRIVATE RESULT")
                .doesNotContain("KEY-SECRET");
    }

    private void assertBadRequest(AiTransformRequest request, String message) {
        assertThatThrownBy(() -> service.transform(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(400))
                .hasMessageContaining(message);
    }

    private void assertProviderMapping(AiProviderErrorCategory category, int status) {
        resetProviderFailure(category);
        assertThatThrownBy(() -> service.transform(
                new AiTransformRequest("MODE", "SOURCE")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(status))
                .hasMessageContaining("请求 ID");
    }

    private void resetProviderFailure(AiProviderErrorCategory category) {
        when(provider.transform(any(AiProviderRequest.class)))
                .thenThrow(new AiProviderException(category));
    }

    private AiModeRuntimeConfig mode(boolean enabled) {
        return mode(enabled, "prompt");
    }

    private AiModeRuntimeConfig mode(boolean enabled, String prompt) {
        return new AiModeRuntimeConfig(
                1L,
                "STRUCTURE_CLEANUP",
                "结构整理",
                2,
                prompt,
                AiValidationProfile.NONE,
                enabled);
    }

    private static AiProperties enabledProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setMaxInputChars(20_000);
        properties.setMaxOutputChars(60_000);
        properties.setTimeoutSeconds(180);
        properties.getDeepseek().setApiKey("test-key");
        properties.getDeepseek().setModel("deepseek-chat");
        return properties;
    }
}
