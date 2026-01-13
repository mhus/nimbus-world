package de.mhus.nimbus.world.shared.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require session-based authentication for accessing a REST endpoint.
 * Session authentication is bound to a WebSocket session and includes character information.
 *
 * Usage:
 * <pre>
 * {@code @RequireSession}
 * {@code @PostMapping("/player/action")}
 * public ResponseEntity<?> playerAction() {
 *     // Only accessible by users with active session
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSession {
}
