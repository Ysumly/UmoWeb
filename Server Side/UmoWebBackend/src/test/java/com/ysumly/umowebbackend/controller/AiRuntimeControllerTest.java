package com.ysumly.umowebbackend.controller;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.GlobalExceptionHandler;
import com.ysumly.umowebbackend.common.util.JwtUtil;
import com.ysumly.umowebbackend.config.AdminInterceptor;
import com.ysumly.umowebbackend.config.AiProperties;
import com.ysumly.umowebbackend.controller.admin.AiRuntimeController;
import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.dto.AiTransformRequest;
import com.ysumly.umowebbackend.model.vo.AiTokenUsageVO;
import com.ysumly.umowebbackend.model.vo.AiTransformResultVO;
import com.ysumly.umowebbackend.model.vo.AiModeSettingsVO;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import com.ysumly.umowebbackend.service.admin.AiTransformService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiRuntimeControllerTest {

    private final AiModeCatalogService modeCatalogService = mock(AiModeCatalogService.class);
    private final AiTransformService transformService = mock(AiTransformService.class);
    private final AiProperties properties = new AiProperties();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AiRuntimeController(
                    properties,
                    modeCatalogService,
                    transformService))
            .build();
    private final MockMvc securedMvc = MockMvcBuilders
            .standaloneSetup(new AiRuntimeController(
                    properties,
                    modeCatalogService,
                    transformService))
            .addInterceptors(new AdminInterceptor(
                    new JwtUtil("a-test-secret-that-is-not-production", 24),
                    mock(UserMapper.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void missingTokenReturnsUnauthorizedForTransform() throws Exception {
        securedMvc.perform(post("/api/admin/ai/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modeKey":"MODE","content":"SOURCE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void settingsReturnsRuntimeLimitsWithoutSecrets() throws Exception {
        properties.setEnabled(true);
        properties.setMaxInputChars(20_000);
        properties.setMaxOutputChars(60_000);
        properties.getDeepseek().setApiKey("must-not-leak");
        properties.getDeepseek().setModel("deepseek-chat");
        when(modeCatalogService.list()).thenReturn(List.of(settings(true), settings(false)));

        mvc.perform(get("/api/admin/ai/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.provider").value("deepseek"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.maxInputChars").value(20_000))
                .andExpect(jsonPath("$.maxOutputChars").value(60_000))
                .andExpect(jsonPath("$.modeCount").value(2))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void capabilitiesOnlyReturnsEnabledModeSummaries() throws Exception {
        properties.setEnabled(false);
        properties.setMaxInputChars(12_345);
        properties.getDeepseek().setModel("must-not-leak");
        when(modeCatalogService.list()).thenReturn(List.of(settings(true), settings(false)));

        mvc.perform(get("/api/admin/ai/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.maxInputChars").value(12_345))
                .andExpect(jsonPath("$.modes.length()").value(1))
                .andExpect(jsonPath("$.modes[0].modeKey").value("STRUCTURE_CLEANUP"))
                .andExpect(jsonPath("$.model").doesNotExist())
                .andExpect(jsonPath("$.modes[0].systemPrompt").doesNotExist());
    }

    @Test
    void transformReturnsFrozenResultShape() throws Exception {
        when(transformService.transform(any(AiTransformRequest.class)))
                .thenReturn(new AiTransformResultVO(
                        "request-1",
                        "STRUCTURE_CLEANUP",
                        2,
                        "RESULT",
                        "deepseek-chat",
                        new AiTokenUsageVO(11, 7, 18)));

        mvc.perform(post("/api/admin/ai/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modeKey": "STRUCTURE_CLEANUP",
                                  "content": "SOURCE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("request-1"))
                .andExpect(jsonPath("$.modeKey").value("STRUCTURE_CLEANUP"))
                .andExpect(jsonPath("$.modeVersion").value(2))
                .andExpect(jsonPath("$.content").value("RESULT"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.usage.inputTokens").value(11))
                .andExpect(jsonPath("$.usage.outputTokens").value(7))
                .andExpect(jsonPath("$.usage.totalTokens").value(18));
    }

    private AiModeSettingsVO settings(boolean enabled) {
        return new AiModeSettingsVO(
                enabled ? 1L : 2L,
                enabled ? "STRUCTURE_CLEANUP" : "DISABLED_MODE",
                enabled ? "结构整理" : "停用模式",
                "",
                enabled,
                1,
                1,
                "prompt",
                AiValidationProfile.NONE,
                LocalDateTime.of(2026, 9, 18, 12, 0),
                LocalDateTime.of(2026, 9, 18, 12, 0));
    }
}
