package com.ysumly.umowebbackend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {

    private final List<TrustedProxy> trustedProxies;

    @Autowired
    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this(parseTrustedProxies(trustedProxies));
    }

    ClientIpResolver(Set<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream()
                .map(TrustedProxy::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrusted(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = hops[i].trim();
            if (!candidate.isBlank() && !isTrusted(candidate)) {
                return candidate;
            }
        }
        return remoteAddress;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.contains(address));
    }

    private static Set<String> parseTrustedProxies(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private record TrustedProxy(byte[] network, int prefixLength) {

        private static TrustedProxy parse(String value) {
            String entry = value.trim();
            int separator = entry.indexOf('/');
            String addressPart = separator >= 0 ? entry.substring(0, separator) : entry;
            byte[] network = parseAddress(addressPart);
            int prefixLength = separator >= 0
                    ? parsePrefixLength(entry, separator + 1, network.length * 8)
                    : network.length * 8;
            return new TrustedProxy(network, prefixLength);
        }

        private boolean contains(String candidate) {
            byte[] address;
            try {
                address = parseAddress(candidate);
            } catch (IllegalArgumentException exception) {
                return false;
            }
            if (address.length != network.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        private static byte[] parseAddress(String value) {
            if (value == null || value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")) {
                throw new IllegalArgumentException("Invalid trusted proxy entry: " + value);
            }
            try {
                return InetAddress.getByName(value).getAddress();
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy entry: " + value, exception);
            }
        }

        private static int parsePrefixLength(String value, int start, int maxLength) {
            try {
                int prefixLength = Integer.parseInt(value.substring(start));
                if (prefixLength < 0 || prefixLength > maxLength) {
                    throw new IllegalArgumentException("Invalid trusted proxy entry: " + value);
                }
                return prefixLength;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy entry: " + value, exception);
            }
        }
    }
}
