package com.atrum.agrum.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The window (FSD section 2) a controller's endpoints belong to. EndpointDiscoveryListener saves it
 * on each Projection so administrators can build permission sets window by window. The
 * description of each endpoint comes from its @Operation summary.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectionGroup {
    String value();
}
