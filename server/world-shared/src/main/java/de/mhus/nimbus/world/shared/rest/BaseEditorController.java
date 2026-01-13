package de.mhus.nimbus.world.shared.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Base controller for world editor REST endpoints.
 * Provides common helper methods for error handling, validation, and response formatting.
 */
public abstract class BaseEditorController {

    // Error Response Helpers

    protected ResponseEntity<Map<String,String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    protected ResponseEntity<Map<String,String>> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", msg));
    }

    protected ResponseEntity<Map<String,String>> conflict(String msg) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
    }

    protected ResponseEntity<Map<String,String>> unauthorized(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", msg));
    }

    // Validation Helpers

    protected boolean blank(String s) {
        return s == null || s.isBlank();
    }

    protected ResponseEntity<?> validateId(String id, String fieldName) {
        if (blank(id)) {
            return bad(fieldName + " required");
        }
        return null;
    }

    protected ResponseEntity<?> validatePagination(int offset, int limit) {
        if (offset < 0) {
            return bad("offset must be >= 0");
        }
        if (limit <= 0 || limit > 1000) {
            return bad("limit must be between 1 and 1000");
        }
        return null;
    }
}
