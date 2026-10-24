package com.atrum.agrum.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class DynamicPermissionSetManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionChecker permissionChecker;

    public DynamicPermissionSetManager(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return new AuthorizationDecision(false); // Deny access
        }

        HttpServletRequest request = context.getRequest();
        return new AuthorizationDecision(
                permissionChecker.isGranted(auth.getName(), request.getMethod(), request.getRequestURI()));
    }
}
