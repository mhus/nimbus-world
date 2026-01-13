package de.mhus.nimbus.world.shared.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require agent authentication for accessing a REST endpoint.
 * Agent authentication is non-session based and used for bots or server access.
 *
 * Usage:
 * <pre>
 * {@code @RequireAgent}
 * {@code @PostMapping("/bot/command")}
 * public ResponseEntity<?> botCommand() {
 *     // Only accessible by agents
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAgent {
}
