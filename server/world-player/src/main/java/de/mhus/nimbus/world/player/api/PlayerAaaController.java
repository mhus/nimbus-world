package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.world.shared.access.AccessService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Authentication, Authorization, and Access (AAA) in world-player.
 * Provides authorization endpoint for setting session cookies.
 */
@RestController
@RequestMapping("/player/aaa")
@RequiredArgsConstructor
@Slf4j
public class PlayerAaaController {

    private final AccessService accessService;

    // ===== GET /player/aaa/authorize =====

    /**
     * Authorize with access token and set session cookies.
     * Validates the access token and creates long-lived session cookies.
     *
     * @param token Access token from /devlogin
     * @param response HttpServletResponse for cookie setting
     * @return 200 OK if successful
     */
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam String token,
            HttpServletResponse response
    ) {
        log.debug("GET /player/aaa/authorize - validating token");

        try {
            accessService.authorizeWithToken(token, response);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            // Token validation failures
            log.warn("Token validation failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid or expired token"));

        } catch (IllegalStateException e) {
            // Session/access validation failures
            log.warn("Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            // Unexpected errors
            log.error("Authorization failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    // ===== DELETE /control/aaa/authorize =====

    /**
     * Logout by removing session cookies (DELETE login).
     * Clears the sessionToken cookie by setting MaxAge=0.
     *
     * @param response HttpServletResponse for cookie clearing
     * @return 200 OK
     */
    @DeleteMapping("/authorize")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        log.debug("DELETE /control/aaa/authorize");

        try {
            accessService.logout(response);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));

        } catch (Exception e) {
            log.error("Logout failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

}
