package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.user.SectorRoles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require a specific sector role for accessing a REST endpoint.
 *
 * Usage:
 * <pre>
 * {@code @RequireSectorRole(SectorRoles.ADMIN)}
 * {@code @GetMapping("/sector/admin")}
 * public ResponseEntity<?> adminFunction() {
 *     // Only accessible by sector admins
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSectorRole {
    /**
     * The required sector role.
     */
    SectorRoles value();
}
