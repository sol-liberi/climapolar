package com.friopolar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rechaza cuerpos no autenticados antes de que CSRF o MVC intenten resolver
 * un multipart potencialmente grande. El unico POST anonimo es el login.
 */
public final class AdminRequestGuardFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/admin/login";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return esMetodoSeguro(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = rutaAplicacion(request);
        if (LOGIN_PATH.equals(path) && "POST".equals(request.getMethod())) {
            if (!esFormularioUrlEncoded(request.getContentType())) {
                rechazar(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            rechazar(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean esFormularioUrlEncoded(String contentType) {
        if (contentType == null || contentType.isBlank()) return false;
        try {
            return MediaType.APPLICATION_FORM_URLENCODED.includes(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean esMetodoSeguro(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method);
    }

    private String rutaAplicacion(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private void rechazar(HttpServletResponse response, int status) {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
