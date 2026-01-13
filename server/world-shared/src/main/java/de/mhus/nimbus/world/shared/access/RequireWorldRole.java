package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.user.WorldRoles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require a specific world role for accessing a REST endpoint.
 *
 * Usage:
 * <pre>
 * {@code @RequireWorldRole(WorldRoles.EDITOR)}
 * {@code @GetMapping("/admin/config")}
 * public ResponseEntity<?> getConfig() {
 *     // Only accessible by world editors
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWorldRole {
    /**
     * The required world role.
     */
    WorldRoles value();
}
