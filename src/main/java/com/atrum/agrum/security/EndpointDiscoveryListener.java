package com.atrum.agrum.security;

import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the projections table in step with the endpoints on every startup: new endpoints are
 * added, the description (@Operation summary) and window (@ProjectionGroup) of existing ones are
 * refreshed, and endpoints that no longer exist are removed together with their grants.
 */
@Component
public class EndpointDiscoveryListener implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EndpointDiscoveryListener.class);
    private static final String DEFAULT_GROUP = "Other";
    private static final String API_DOCS_GROUP = "API documentation (always allowed, no grant needed)";

    private final RequestMappingHandlerMapping handlerMapping;
    private final ProjectionRepository projectionRepository;

    public EndpointDiscoveryListener(RequestMappingHandlerMapping handlerMapping,
                                     ProjectionRepository projectionRepository) {
        this.handlerMapping = handlerMapping;
        this.projectionRepository = projectionRepository;
    }

    @Override
    @Transactional
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, Projection> existing = new HashMap<>();
        projectionRepository.findAll().forEach(projection -> existing.put(projection.getId(), projection));
        Set<String> discovered = new HashSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
            String description = describe(handler);
            String group = groupOf(handler);

            for (String path : mappingInfo.getPatternValues()) {
                if (path.startsWith("/error")) {
                    continue; // Skip Spring Boot internal error controllers
                }

                for (RequestMethod method : methods) {
                    String httpMethodStr = method.name();
                    String projectionId = httpMethodStr + ":" + path;
                    discovered.add(projectionId);

                    Projection projection = existing.get(projectionId);
                    if (projection == null) {
                        projection = new Projection(projectionId, httpMethodStr, path);
                    } else if (Objects.equals(projection.getDescription(), description)
                            && Objects.equals(projection.getGroupName(), group)) {
                        continue;
                    }
                    projection.setDescription(description);
                    projection.setGroupName(group);
                    projectionRepository.save(projection);
                }
            }
        }

        // Endpoints removed from the code: their grants can no longer do anything
        Set<String> stale = new HashSet<>(existing.keySet());
        stale.removeAll(discovered);
        if (!stale.isEmpty()) {
            projectionRepository.deleteGrantsOf(stale);
            projectionRepository.deleteAllById(stale);
            log.info("Removed {} projection(s) of endpoints that no longer exist: {}", stale.size(), stale);
        }
    }

    // The @Operation summary, else the handler name in words, e.g. "Get all projections"
    private static String describe(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        if (operation != null && StringUtils.hasText(operation.summary())) {
            return operation.summary();
        }
        String words = handler.getMethod().getName().replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String groupOf(HandlerMethod handler) {
        ProjectionGroup group = handler.getBeanType().getAnnotation(ProjectionGroup.class);
        if (group != null) {
            return group.value();
        }
        // springdoc's own endpoints; SecurityConfig permits the Swagger paths to everyone
        return handler.getBeanType().getName().startsWith("org.springdoc.") ? API_DOCS_GROUP : DEFAULT_GROUP;
    }
}
