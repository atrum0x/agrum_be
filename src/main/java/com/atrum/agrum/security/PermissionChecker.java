package com.atrum.agrum.security;

import com.atrum.agrum.permission.PermissionSetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * Whether a user is granted an endpoint through their permission sets. Used for request
 * authorization and for rights that are granted as an endpoint but also checked elsewhere,
 * e.g. CL-03: a phone record entered manually is only accepted from a user granted the
 * manual-entry endpoint.
 */
@Component
public class PermissionChecker {

    private final PermissionSetRepository permissionSetRepository;
    private final PathPatternParser patternParser = new PathPatternParser();

    @Value("${SUPERUSER_USERNAME}")
    private String superUsername;

    public PermissionChecker(PermissionSetRepository permissionSetRepository) {
        this.permissionSetRepository = permissionSetRepository;
    }

    public boolean isSuperuser(String username) {
        return superUsername.equals(username);
    }

    public boolean isGranted(String username, String httpMethod, String path) {
        if (isSuperuser(username)) {
            return true; // Instant access to any projection/endpoint!
        }
        // Allowed URL patterns for this user and method, matched like Spring MVC does
        List<String> allowedPatterns =
                permissionSetRepository.findAllowedPathPatternsByUsernameAndHttpMethod(username, httpMethod);
        PathContainer pathContainer = PathContainer.parsePath(path);
        return allowedPatterns.stream()
                .map(patternParser::parse)
                .anyMatch(pattern -> pattern.matches(pathContainer));
    }

    /** Projection ids ("METHOD:/path") granted to the user; empty for the superuser, who has every one. */
    public List<String> grantedProjectionIds(String username) {
        return isSuperuser(username) ? List.of() : permissionSetRepository.findGrantedProjectionIdsByUsername(username);
    }

    /** The logged-in user; false when nobody is logged in. */
    public boolean isCurrentUserGranted(String httpMethod, String path) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        return isGranted(auth.getName(), httpMethod, path);
    }
}
