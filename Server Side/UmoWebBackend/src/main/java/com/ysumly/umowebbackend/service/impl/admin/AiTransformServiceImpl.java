package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.config.AiProperties;
import com.ysumly.umowebbackend.model.dto.AiTransformRequest;
import com.ysumly.umowebbackend.model.vo.AiTokenUsageVO;
import com.ysumly.umowebbackend.model.vo.AiTransformResultVO;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import com.ysumly.umowebbackend.service.admin.AiModeRuntimeConfig;
import com.ysumly.umowebbackend.service.admin.AiTransformService;
import com.ysumly.umowebbackend.service.ai.AiProviderErrorCategory;
import com.ysumly.umowebbackend.service.ai.AiProviderException;
import com.ysumly.umowebbackend.service.ai.AiProviderRequest;
import com.ysumly.umowebbackend.service.ai.AiProviderResult;
import com.ysumly.umowebbackend.service.ai.AiResultValidator;
import com.ysumly.umowebbackend.service.ai.AiTransformProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AiTransformServiceImpl implements AiTransformService {

    private static final Logger log = LoggerFactory.getLogger(AiTransformServiceImpl.class);

    private final AiProperties properties;
    private final AiModeCatalogService modeCatalogService;
    private final AiTransformProvider provider;
    private final AiRequestGuard requestGuard;
    private final AiResultValidator resultValidator;

    public AiTransformServiceImpl(AiProperties properties,
                                  AiModeCatalogService modeCatalogService,
                                  AiTransformProvider provider,
                                  AiRequestGuard requestGuard,
                                  AiResultValidator resultValidator) {
        this.properties = properties;
        this.modeCatalogService = modeCatalogService;
        this.provider = provider;
        this.requestGuard = requestGuard;
        this.resultValidator = resultValidator;
    }

    @Override
    public AiTransformResultVO transform(AiTransformRequest request) {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        String modeKey = request == null || request.modeKey() == null
                ? ""
                : request.modeKey().trim();
        String content = request == null ? null : request.content();
        int inputChars = codePointLength(content);
        int modeVersion = 0;
        int outputChars = 0;
        int inputTokens = 0;
        int outputTokens = 0;
        String status = "error";
        String errorCategory = "";
        AiRequestGuard.Lease lease = null;

        try {
            if (!properties.isEnabled()) {
                errorCategory = "AI_DISABLED";
                throw new BusinessException(409, "AI 功能未启用");
            }
            validateInput(content);

            AiModeRuntimeConfig mode = modeCatalogService.findByKey(modeKey)
                    .orElseThrow(() -> new NotFoundException(
                            "AI 模式不存在（请求 ID: " + requestId + "）"));
            modeVersion = mode.versionNo();
            if (!mode.enabled()) {
                errorCategory = "AI_MODE_DISABLED";
                throw new BusinessException(409, "AI 模式已停用");
            }

            lease = requestGuard.acquire();
            AiProviderResult providerResult = provider.transform(new AiProviderRequest(
                    properties.getDeepseek().getModel(),
                    mode.systemPrompt(),
                    content,
                    properties.getMaxOutputChars(),
                    Duration.ofSeconds(properties.getTimeoutSeconds())));
            resultValidator.validate(
                    mode.validationProfile(),
                    content,
                    providerResult.content(),
                    properties.getMaxOutputChars());

            outputChars = codePointLength(providerResult.content());
            inputTokens = providerResult.inputTokens();
            outputTokens = providerResult.outputTokens();
            status = "success";

            return new AiTransformResultVO(
                    requestId,
                    mode.modeKey(),
                    mode.versionNo(),
                    providerResult.content(),
                    properties.getDeepseek().getModel(),
                    new AiTokenUsageVO(
                            providerResult.inputTokens(),
                            providerResult.outputTokens(),
                            providerResult.totalTokens()));
        } catch (AiProviderException e) {
            errorCategory = e.getCategory().name();
            throw providerFailure(requestId, e);
        } catch (BusinessException e) {
            if (errorCategory.isBlank()) {
                errorCategory = categoryForStatus(e.getCode());
            }
            throw withRequestId(requestId, e);
        } catch (RuntimeException e) {
            errorCategory = "UNEXPECTED";
            throw new BusinessException(
                    502,
                    "AI 服务返回无效响应（请求 ID: " + requestId + "）");
        } finally {
            if (lease != null) {
                lease.close();
            }
            log.info(
                    "requestId={} modeKey={} modeVersion={} inputChars={} outputChars={} "
                            + "inputTokens={} outputTokens={} durationMs={} status={} "
                            + "errorCategory={}",
                    requestId,
                    modeKey,
                    modeVersion,
                    inputChars,
                    outputChars,
                    inputTokens,
                    outputTokens,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    status,
                    errorCategory);
        }
    }

    private void validateInput(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "400 请先输入正文");
        }
        if (codePointLength(content) > properties.getMaxInputChars()) {
            throw new BusinessException(
                    400,
                    "400 正文不能超过 " + properties.getMaxInputChars() + " 字符");
        }
    }

    private BusinessException providerFailure(String requestId, AiProviderException error) {
        String message = switch (error.getCategory()) {
            case AUTHENTICATION, INSUFFICIENT_BALANCE, UNAVAILABLE ->
                    "AI 服务暂时不可用";
            case RATE_LIMITED -> "AI 服务请求过于频繁";
            case INVALID_REQUEST, INVALID_RESPONSE -> "AI 服务返回无效响应";
            case TIMEOUT -> "AI 服务响应超时";
        };
        int status = switch (error.getCategory()) {
            case AUTHENTICATION, INSUFFICIENT_BALANCE, UNAVAILABLE -> 503;
            case RATE_LIMITED -> 429;
            case INVALID_REQUEST, INVALID_RESPONSE -> 502;
            case TIMEOUT -> 504;
        };
        return new BusinessException(status, message + "（请求 ID: " + requestId + "）");
    }

    private BusinessException withRequestId(String requestId, BusinessException error) {
        if (error.getMessage() != null && error.getMessage().contains(requestId)) {
            return error;
        }
        return new BusinessException(
                error.getCode(),
                error.getMessage() + "（请求 ID: " + requestId + "）");
    }

    private String categoryForStatus(int status) {
        return switch (status) {
            case 400 -> "INVALID_INPUT";
            case 409 -> "AI_NOT_AVAILABLE";
            case 429 -> "LOCAL_RATE_LIMITED";
            case 502 -> "INVALID_RESPONSE";
            case 503 -> "UNAVAILABLE";
            case 504 -> "TIMEOUT";
            default -> "REJECTED";
        };
    }

    private int codePointLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }
}
