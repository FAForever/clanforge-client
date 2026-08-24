package com.faforever.clan_management;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Not a @Component - registered directly into Spring Security's own filter chain
// in SecurityConfig via addFilterBefore(), so it runs before login processing.
// A plain @Component filter gets registered by Spring Boot as a generic servlet
// filter that runs AFTER Spring Security's whole chain, which is too late to
// block a login attempt - confirmed empirically, it silently did nothing.
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60_000; // 1 minute

    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isLoginAttempt = "POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getServletPath());

        if (isLoginAttempt) {
            Attempts attempts = attemptsByIp.computeIfAbsent(request.getRemoteAddr(), ip -> new Attempts());

            synchronized (attempts) {
                long now = System.currentTimeMillis();
                if (now - attempts.windowStart > WINDOW_MILLIS) {
                    attempts.windowStart = now;
                    attempts.count.set(0);
                }
                if (attempts.count.incrementAndGet() > MAX_ATTEMPTS) {
                    response.setStatus(429);
                    response.getWriter().write("what r u doingg brooo stop spammingggg!!!");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class Attempts {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }
}