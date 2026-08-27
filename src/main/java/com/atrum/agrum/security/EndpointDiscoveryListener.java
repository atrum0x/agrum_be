package com.atrum.agrum.security;

import com.atrum.agrum.projection.Projection;
import com.atrum.agrum.projection.ProjectionRepository;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;

@Component
public class EndpointDiscoveryListener implements ApplicationListener<ContextRefreshedEvent> {

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
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
            Set<String> patterns = mappingInfo.getPatternValues();

            for (String path : patterns) {
                if (path.startsWith("/error")) {
                    continue; // Skip Spring Boot internal error controllers
                }

                for (RequestMethod method : methods) {
                    String httpMethodStr = method.name();
                    String projectionId = httpMethodStr + ":" + path;

                    if (!projectionRepository.existsById(projectionId)) {
                        Projection projection = new Projection(projectionId, httpMethodStr, path);
                        projection.setDescription("Auto-discovered endpoint: " + entry.getValue().getShortLogMessage());
                        projectionRepository.save(projection);
                    }
                }
            }
        }
    }
}