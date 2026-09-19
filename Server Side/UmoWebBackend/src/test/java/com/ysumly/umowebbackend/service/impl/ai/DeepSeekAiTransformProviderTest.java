package com.ysumly.umowebbackend.service.impl.ai;

import com.ysumly.umowebbackend.config.AiProperties;
import com.ysumly.umowebbackend.service.ai.AiProviderErrorCategory;
import com.ysumly.umowebbackend.service.ai.AiProviderException;
import com.ysumly.umowebbackend.service.ai.AiProviderRequest;
import com.ysumly.umowebbackend.service.ai.AiProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekAiTransformProviderTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private DeepSeekAiTransformProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.deepseek.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new DeepSeekAiTransformProvider(
                builder.build(),
                "test-key",
                "test-model");
    }

    @Test
    void sendsSeparateSystemAndUserMessagesAndParsesResult() {
        server.expect(once(), requestTo("https://api.deepseek.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "test-model",
                          "messages": [
                            {"role": "system", "content": "SYSTEM"},
                            {"role": "user", "content": "CONTENT"}
                          ],
                          "stream": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id": "upstream-1",
                          "choices": [{
                            "message": {"content": "RESULT"},
                            "finish_reason": "stop"
                          }],
                          "usage": {
                            "prompt_tokens": 7,
                            "completion_tokens": 3,
                            "total_tokens": 10
                          }
                        }
                        """, MediaType.APPLICATION_JSON)
                        .header("x-request-id", "header-request-1"));

        AiProviderResult result = provider.transform(request());

        assertThat(result.content()).isEqualTo("RESULT");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.inputTokens()).isEqualTo(7);
        assertThat(result.outputTokens()).isEqualTo(3);
        assertThat(result.totalTokens()).isEqualTo(10);
        assertThat(result.upstreamRequestId()).isEqualTo("header-request-1");
        server.verify();
    }

    @Test
    void mapsHttpStatusesToProviderCategories() {
        assertHttpCategory(401, AiProviderErrorCategory.AUTHENTICATION);
        assertHttpCategory(402, AiProviderErrorCategory.INSUFFICIENT_BALANCE);
        assertHttpCategory(429, AiProviderErrorCategory.RATE_LIMITED);
        assertHttpCategory(400, AiProviderErrorCategory.INVALID_REQUEST);
        assertHttpCategory(500, AiProviderErrorCategory.UNAVAILABLE);
        assertHttpCategory(503, AiProviderErrorCategory.UNAVAILABLE);
        server.verify();
    }

    @Test
    void mapsTimeoutToTimeoutCategory() {
        server.expect(requestTo("https://api.deepseek.test/chat/completions"))
                .andRespond(withException(new HttpTimeoutException("timeout")));

        assertThatThrownBy(() -> provider.transform(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertThat(((AiProviderException) error).getCategory())
                        .isEqualTo(AiProviderErrorCategory.TIMEOUT));
    }

    @Test
    void rejectsMalformedJson() {
        server.expect(requestTo("https://api.deepseek.test/chat/completions"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertProviderCategory(AiProviderErrorCategory.INVALID_RESPONSE);
    }

    @Test
    void rejectsFinishReasonOtherThanStop() {
        server.expect(requestTo("https://api.deepseek.test/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {"content": "partial"},
                            "finish_reason": "length"
                          }],
                          "usage": {
                            "prompt_tokens": 1,
                            "completion_tokens": 1,
                            "total_tokens": 2
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertProviderCategory(AiProviderErrorCategory.INVALID_RESPONSE);
    }

    @Test
    void springCreatesProviderWhenDeepSeekBeansArePresent() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ProviderConstructionConfig.class)) {
            assertThat(context.getBean(DeepSeekAiTransformProvider.class)).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DeepSeekAiTransformProvider.class)
    static class ProviderConstructionConfig {

        @Bean
        AiProperties aiProperties() {
            return new AiProperties();
        }

        @Bean
        RestClient deepSeekRestClient() {
            return RestClient.create();
        }
    }

    private void assertHttpCategory(int status, AiProviderErrorCategory category) {
        RestClient.Builder statusBuilder = RestClient.builder()
                .baseUrl("https://api.deepseek.test");
        MockRestServiceServer statusServer =
                MockRestServiceServer.bindTo(statusBuilder).build();
        DeepSeekAiTransformProvider statusProvider = new DeepSeekAiTransformProvider(
                statusBuilder.build(),
                "test-key",
                "test-model");
        statusServer.expect(requestTo("https://api.deepseek.test/chat/completions"))
                .andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(status)));

        assertThatThrownBy(() -> statusProvider.transform(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertThat(((AiProviderException) error).getCategory())
                        .isEqualTo(category));
        statusServer.verify();
    }

    private void assertProviderCategory(AiProviderErrorCategory category) {
        assertThatThrownBy(() -> provider.transform(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertThat(((AiProviderException) error).getCategory())
                        .isEqualTo(category));
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "test-model",
                "SYSTEM",
                "CONTENT",
                60_000,
                Duration.ofSeconds(180));
    }
}
