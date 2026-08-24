package com.atrum.agrum.security;

import com.atrum.agrum.permission.PermissionSetRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.function.Supplier;

@Component
public class DynamicPermissionSetManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionSetRepository permissionSetRepository;
    private final PathPatternParser patternParser = new PathPatternParser();

    public DynamicPermissionSetManager(PermissionSetRepository permissionSetRepository) {
        this.permissionSetRepository = permissionSetRepository;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return new AuthorizationDecision(false); // Deny access
        }

        HttpServletRequest request = context.getRequest();
        String requestUrl = request.getRequestURI();
        String httpMethod = request.getMethod();
        String username = auth.getName();

        // 1. Fetch allowed URL patterns for this user and method from DB
        List<String> allowedPatterns = permissionSetRepository.findAllowedPathPatternsByUsernameAndHttpMethod(username, httpMethod);

        if (allowedPatterns.isEmpty()) {
            return new AuthorizationDecision(false);
        }

        // 2. Perform Spring REST path pattern matching
        PathContainer pathContainer = PathContainer.parsePath(requestUrl);
        boolean hasAccess = allowedPatterns.stream()
                .map(patternParser::parse)
                .anyMatch(pattern -> pattern.matches(pathContainer));

        return new AuthorizationDecision(hasAccess);
    }
}