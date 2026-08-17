package com.friopolar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/admin/login";

    private final LoginAttemptLimiter limiter;
    private final ClientAddressResolver clientAddressResolver;

    public LoginRateLimitFilter(LoginAttemptLimiter limiter, ClientAddressResolver clientAddressResolver) {
        this.limiter = limiter;
        this.clientAddressResolver = clientAddressResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return !LOGIN_PATH.equals(requestPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var decision = limiter.check(clientAddressResolver.resolve(request), request.getParameter("username"));
        if (!decision.blocked()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write("Demasiados intentos de acceso. Esperá unos segundos y volvé a intentar.");
    }
}
