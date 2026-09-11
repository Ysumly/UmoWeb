package com.ysumly.umowebbackend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WebConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void corsOriginsCanBeConfiguredForProduction() {
        WebConfig config = new WebConfig(
                mock(AdminInterceptor.class),
                mock(RateLimitInterceptor.class),
                "https://blog.example.com, https://admin.example.com",
                tempDir.resolve("storage").toString());
        TestCorsRegistry registry = new TestCorsRegistry();

        config.addCorsMappings(registry);

        CorsConfiguration cors = registry.getCorsConfigurations().get("/api/**");
        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://blog.example.com", "https://admin.example.com");
    }

    @Test
    void blankCorsOriginsFailFast() {
        assertThatThrownBy(() -> new WebConfig(
                mock(AdminInterceptor.class),
                mock(RateLimitInterceptor.class),
                " ",
                tempDir.resolve("storage").toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void imageResourceLocationUsesConfiguredStorageRoot() {
        Path storageRoot = tempDir.resolve("storage");
        WebConfig config = new WebConfig(
                mock(AdminInterceptor.class),
                mock(RateLimitInterceptor.class),
                "http://localhost:5173",
                storageRoot.toString());
        String expected = storageRoot.resolve("images").toAbsolutePath().normalize().toUri().toString();
        expected = expected.endsWith("/") ? expected : expected + "/";

        assertThat(config.imageResourceLocation())
                .isEqualTo(expected);
    }

    private static final class TestCorsRegistry extends CorsRegistry {
        @Override
        public Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }
}
