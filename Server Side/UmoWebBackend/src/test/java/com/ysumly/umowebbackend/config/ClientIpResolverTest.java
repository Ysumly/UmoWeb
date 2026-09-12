package com.ysumly.umowebbackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClientIpResolverTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ClientIpResolver.class)
            .withPropertyValues("app.security.trusted-proxies=");

    @Test
    void canBeCreatedBySpringContainer() {
        contextRunner.run(context ->
                assertThat(context.getBean(ClientIpResolver.class)).isNotNull());
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedClient() {
        ClientIpResolver resolver = new ClientIpResolver(Set.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void usesForwardedHeaderOnlyFromConfiguredProxy() {
        ClientIpResolver resolver = new ClientIpResolver(Set.of("10.0.0.2"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 198.51.100.20, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void usesForwardedHeaderFromConfiguredIpv4Cidr() {
        ClientIpResolver resolver = new ClientIpResolver(Set.of("10.0.0.0/24"));
        MockHttpServletRequest request = requestFrom("10.0.0.42", "198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void treatsSingleAddressCidrAsExactAddress() {
        ClientIpResolver resolver = new ClientIpResolver(Set.of("10.0.0.2/32"));

        assertThat(resolver.resolve(requestFrom("10.0.0.2", "198.51.100.20")))
                .isEqualTo("198.51.100.20");
        assertThat(resolver.resolve(requestFrom("10.0.0.3", "198.51.100.20")))
                .isEqualTo("10.0.0.3");
    }

    @Test
    void usesForwardedHeaderFromConfiguredIpv6Cidr() {
        ClientIpResolver resolver = new ClientIpResolver(Set.of("2001:db8:abcd::/48"));
        MockHttpServletRequest request = requestFrom("2001:db8:abcd::10", "198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void rejectsInvalidTrustedProxyEntry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientIpResolver(Set.of("10.0.0.0/33")))
                .withMessage("Invalid trusted proxy entry: 10.0.0.0/33");
    }

    private MockHttpServletRequest requestFrom(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
