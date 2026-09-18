package com.ysumly.umowebbackend.service.impl.ai;

import com.ysumly.umowebbackend.config.AiProperties;
import com.ysumly.umowebbackend.service.ai.AiProviderErrorCategory;
import com.ysumly.umowebbackend.service.ai.AiProviderException;
import com.ysumly.umowebbackend.service.ai.AiProviderRequest;
import com.ysumly.umowebbackend.service.ai.AiProviderResult;
import com.ysumly.umowebbackend.service.ai.AiTransformProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class DeepSeekAiTransformProvider implements AiTransformProvider {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String UPSTREAM_REQUEST_ID_HEADER = "x-request-id";

    private final RestClient restClient;
    private final String apiKey;
    private final String configuredModel;

    public DeepSeekAiTransformProvider(
            @Qualifier("deepSeekRestClient") RestClient restClient,
            AiProperties properties) {
        this(restClient,
                properties.getDeepseek().getApiKey(),
                properties.getDeepseek().getModel());
    }

    DeepSeekAiTransformProvider(RestClient restClient,
                                String apiKey,
                                String configuredModel) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.configuredModel = configuredModel;
    }

    @Override
    public AiProviderResult transform(AiProviderRequest request) {
        String model = request.model() == null || request.model().isBlank()
                ? configuredModel
                : request.model();
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userContent())),
                "stream", false);

        try {
            ResponseEntity<Map<String, Object>> response = restClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return parseResult(response);
        } catch (RestClientResponseException e) {
            throw new AiProviderException(
                    classifyStatus(e.getStatusCode().value()),
                    e.getStatusCode().value(),
                    e.getResponseHeaders() == null
                            ? null
                            : e.getResponseHeaders().getFirst(UPSTREAM_REQUEST_ID_HEADER));
        } catch (ResourceAccessException e) {
            throw new AiProviderException(isTimeout(e)
                    ? AiProviderErrorCategory.TIMEOUT
                    : AiProviderErrorCategory.UNAVAILABLE);
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }
    }

    private AiProviderResult parseResult(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        if (body == null || body.isEmpty()) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }

        Object choicesValue = body.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }

        String finishReason = stringValue(choice.get("finish_reason"));
        if (!"stop".equals(finishReason)) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }

        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }
        String content = stringValue(message.get("content"));
        if (content == null || content.isBlank()) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }

        Map<?, ?> usage = body.get("usage") instanceof Map<?, ?> value
                ? value
                : Map.of();
        String upstreamRequestId = response.getHeaders().getFirst(UPSTREAM_REQUEST_ID_HEADER);
        if (upstreamRequestId == null || upstreamRequestId.isBlank()) {
            upstreamRequestId = stringValue(body.get("id"));
        }

        return new AiProviderResult(
                content,
                finishReason,
                intValue(usage.get("prompt_tokens")),
                intValue(usage.get("completion_tokens")),
                intValue(usage.get("total_tokens")),
                upstreamRequestId);
    }

    private AiProviderErrorCategory classifyStatus(int status) {
        return switch (status) {
            case 401 -> AiProviderErrorCategory.AUTHENTICATION;
            case 402 -> AiProviderErrorCategory.INSUFFICIENT_BALANCE;
            case 429 -> AiProviderErrorCategory.RATE_LIMITED;
            case 400, 422 -> AiProviderErrorCategory.INVALID_REQUEST;
            case 500, 503 -> AiProviderErrorCategory.UNAVAILABLE;
            default -> status >= 500
                    ? AiProviderErrorCategory.UNAVAILABLE
                    : AiProviderErrorCategory.INVALID_REQUEST;
        };
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
