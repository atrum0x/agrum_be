package com.atrum.agrum.security;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    public RateLimitingFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String key;
        boolean isAuthenticated = false;

        // 1. Determine the key (Username or IP Address)
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            key = "user:" + auth.getName();
            isAuthenticated = true;
        } else {
            key = "ip:" + getClientIP(request);
        }

        // 2. Fetch the bucket for this key
        Bucket bucket = rateLimitingService.resolveBucket(key, isAuthenticated);


        // 3. Try to consume 1 token
        if (bucket.tryConsume(1)) {
            // Success: Let the request pass to the controller
            filterChain.doFilter(request, response);
        } else {
            // Failure: Bucket is empty. Block the request.
            response.setStatus(429); // 429 Too Many Requests
            response.getWriter().write("Too many requests. Please try again later.");
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

}