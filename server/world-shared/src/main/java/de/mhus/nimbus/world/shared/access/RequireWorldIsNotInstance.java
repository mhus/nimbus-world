package de.mhus.nimbus.world.shared.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require that the world is NOT an instance.
 * Useful for operations that should only work on main worlds, zones, or branches.
 *
 * Usage:
 * <pre>
 * {@code @RequireWorldIsNotInstance}
 * {@code @PostMapping("/world/config")}
 * public ResponseEntity<?> updateConfig() {
 *     // Only accessible for non-instance worlds
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWorldIsNotInstance {
}
