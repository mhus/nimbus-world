package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing RUser entities.
 * Provides CRUD operations, role management, and settings management.
 */
@RestController
@RequestMapping("/control/users")
@RequiredArgsConstructor
public class RUserController extends BaseEditorController {

    private final RUserService userService;

    /**
     * List all users
     * GET /control/users
     */
    @GetMapping
    public ResponseEntity<List<RUser>> list() {
        List<RUser> result = userService.listAll();
        return ResponseEntity.ok(result);
    }

    /**
     * Get user by username
     * GET /control/user/{username}
     */
    @GetMapping("/{username}")
    public ResponseEntity<RUser> get(@PathVariable String username) {
        var error = validateId(username, "username");
        if (error != null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        return userService.getByUsername(username)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Update user
     * PUT /control/user/{username}
     */
    @PutMapping("/{username}")
    public ResponseEntity<RUser> update(
            @PathVariable String username,
            @RequestBody RUser user) {

        var error = validateId(username, "username");
        if (error != null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        try {
            RUser saved = userService.save(user);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Delete user (disable)
     * DELETE /control/user/{username}
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<?> delete(@PathVariable String username) {
        var error = validateId(username, "username");
        if (error != null) return error;

        try {
            userService.disableUser(username);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

}
