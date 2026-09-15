package com.faforever.clan_management.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks an IP after too many login attempts in a short window, to slow down
 * password guessing against the login form.
 *
 * Not a @Component - registered directly into Spring Security's own filter chain
 * in SecurityConfig via addFilterBefore(), so it runs before login processing.
 * A plain @Component filter gets registered by Spring Boot as a generic servlet
 * filter that runs AFTER Spring Security's whole chain, which is too late to
 * block a login attempt - confirmed empirically, it silently did nothing.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_MILLIS = 60_000; // 1 minute

    // Hard cap on how many IPs we track at once. Without this the map grows for
    // every distinct IP that ever posts to /login, which is an unbounded memory
    // leak - and a denial-of-service vector in the filter meant to prevent one.
    static final int MAX_TRACKED_IPS = 10_000;

    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isLoginAttempt = "POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(pathWithinApplication(request));

        if (!isLoginAttempt) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        if (attemptsByIp.size() >= MAX_TRACKED_IPS) {
            evictExpired(now);
        }

        Attempts attempts = attemptsByIp.computeIfAbsent(clientIp(request), ip -> new Attempts(now));

        long retryAfterSeconds;
        synchronized (attempts) {
            if (now - attempts.windowStart > WINDOW_MILLIS) {
                attempts.windowStart = now;
                attempts.count = 0;
            }
            attempts.count++;
            if (attempts.count <= MAX_ATTEMPTS) {
                filterChain.doFilter(request, response);
                return;
            }
            long elapsed = now - attempts.windowStart;
            retryAfterSeconds = Math.max(1, (WINDOW_MILLIS - elapsed + 999) / 1000);
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(
                "Too many login attempts. Please wait " + retryAfterSeconds + " seconds and try again.");
    }

    /**
     * Drop everything remembered about this request's address.
     *
     * Called from SecurityConfig when a sign-in succeeds. The counter exists to
     * slow down guessing, and someone who supplied the right password was not
     * guessing, so their correct attempts must not count towards the cap.
     * Without this a single address gets MAX_ATTEMPTS sign-ins per window no
     * matter how many of them were correct.
     */
    void forget(HttpServletRequest request) {
        attemptsByIp.remove(clientIp(request));
    }

    /**
     * Request path relative to the application root.
     *
     * getServletPath() is empty when the servlet is mapped to "/" in some
     * containers, so fall back to the request URI with the context path removed
     * rather than silently failing to match and letting every attempt through.
     */
    private String pathWithinApplication(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return (context != null && !context.isEmpty() && uri.startsWith(context))
                ? uri.substring(context.length())
                : uri;
    }

    /**
     * The address we rate-limit on.
     *
     * Deliberately uses the transport-level peer address only. X-Forwarded-For is
     * NOT trusted: any client can set it, so honouring it blindly would let an
     * attacker sidestep this filter entirely by sending a different value each
     * request. If this is ever deployed behind a reverse proxy or load balancer,
     * getRemoteAddr() becomes the proxy's address and every user shares one
     * counter - so the proxy must be configured to set the standard Forwarded
     * headers and the app must opt in via server.forward-headers-strategy,
     * which makes getRemoteAddr() return the real client address.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Attempts>> it = attemptsByIp.entrySet().iterator();
        while (it.hasNext()) {
            Attempts a = it.next().getValue();
            synchronized (a) {
                if (now - a.windowStart > WINDOW_MILLIS) {
                    it.remove();
                }
            }
        }
    }

    private static final class Attempts {
        int count;
        long windowStart;

        Attempts(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
