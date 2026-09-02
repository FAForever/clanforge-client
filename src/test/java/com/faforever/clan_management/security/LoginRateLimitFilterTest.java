package com.faforever.clan_management.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the login rate limiter. Deliberately drives the filter directly
 * rather than through the application, so these run without a database or a
 * servlet container and stay deterministic.
 */
class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
    }

    private MockHttpServletRequest loginAttempt(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletResponse send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("allows attempts up to the limit, then blocks with 429")
    void blocksAfterTooManyAttempts() throws Exception {
        for (int i = 1; i <= LoginRateLimitFilter.MAX_ATTEMPTS; i++) {
            assertThat(send(loginAttempt("10.0.0.1")).getStatus())
                    .as("attempt %d should be allowed through", i)
                    .isEqualTo(200);
        }

        MockHttpServletResponse blocked = send(loginAttempt("10.0.0.1"));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(blocked.getHeader("Retry-After"))).isBetween(1, 60);
        assertThat(blocked.getContentAsString()).contains("Too many login attempts");
    }

    @Test
    @DisplayName("rate limits each client address independently")
    void limitsPerIpAddress() throws Exception {
        for (int i = 0; i <= LoginRateLimitFilter.MAX_ATTEMPTS; i++) {
            send(loginAttempt("10.0.0.1"));
        }
        assertThat(send(loginAttempt("10.0.0.1")).getStatus()).isEqualTo(429);

        // a different client must not inherit the blocked counter
        assertThat(send(loginAttempt("10.0.0.2")).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ignores requests that are not login POSTs")
    void doesNotLimitOtherRequests() throws Exception {
        for (int i = 0; i < LoginRateLimitFilter.MAX_ATTEMPTS * 3; i++) {
            MockHttpServletRequest browse = new MockHttpServletRequest("GET", "/clans");
            browse.setServletPath("/clans");
            browse.setRemoteAddr("10.0.0.3");
            assertThat(send(browse).getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a successful sign-in clears that address's attempt history")
    void successfulLoginClearsTheCounter() throws Exception {
        // Four wrong guesses, then a correct one. SecurityConfig calls forget()
        // on success, so the budget resets and the next MAX_ATTEMPTS all get
        // through. Without this, someone who knows their password spends the
        // same quota as someone guessing, and the sixth sign-in in a minute from
        // one address is refused.
        for (int i = 0; i < LoginRateLimitFilter.MAX_ATTEMPTS - 1; i++) {
            assertThat(send(loginAttempt("10.0.0.5")).getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest correctPassword = loginAttempt("10.0.0.5");
        assertThat(send(correctPassword).getStatus()).isEqualTo(200);
        filter.forget(correctPassword);

        for (int i = 1; i <= LoginRateLimitFilter.MAX_ATTEMPTS; i++) {
            assertThat(send(loginAttempt("10.0.0.5")).getStatus())
                    .as("attempt %d after a successful sign-in", i)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("does not trust a client-supplied X-Forwarded-For header")
    void ignoresForwardedForSpoofing() throws Exception {
        // An attacker rotating X-Forwarded-For must not get a fresh quota each time.
        for (int i = 0; i <= LoginRateLimitFilter.MAX_ATTEMPTS; i++) {
            MockHttpServletRequest request = loginAttempt("10.0.0.4");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            send(request);
        }

        MockHttpServletRequest spoofed = loginAttempt("10.0.0.4");
        spoofed.addHeader("X-Forwarded-For", "203.0.113.250");
        assertThat(send(spoofed).getStatus()).isEqualTo(429);
    }
}
