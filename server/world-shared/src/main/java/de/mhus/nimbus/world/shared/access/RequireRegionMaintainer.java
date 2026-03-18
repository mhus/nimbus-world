package de.mhus.nimbus.world.shared.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require that the current user is a maintainer of the region
 * identified by the {regionId} path variable.
 *
 * Usage:
 * <pre>
 * {@code @RequireRegionMaintainer}
 * {@code @PostMapping("/regions/{regionId}/worlds")}
 * public ResponseEntity<?> createWorld() {
 *     // Only accessible by region maintainers
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRegionMaintainer {
}
