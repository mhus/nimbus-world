package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/world/user/world")
@RequiredArgsConstructor
@Validated
@Tag(name = "UserWorldEditor", description = "Bearbeitung von Child Worlds durch Nutzer")
public class UserWorldEditorController {

    private final WWorldService worldService;

    public record CreateChildWorldRequest(String worldId, Boolean enabled, Boolean publicFlag) {}
    public record UpdateChildWorldRequest(Boolean enabled, Boolean publicFlag, Set<String> editor, Set<String> player, Set<String> supporter, Set<String> owners) {}

    private String currentUserId(HttpServletRequest req) {
        Object attr = req.getAttribute("currentUserId");
        return attr instanceof String s ? s : null;
    }

    @GetMapping
    @Operation(summary = "Child/Main World abrufen", description = "Lädt Welt falls Berechtigung (owner oder publicFlag)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gefunden"),
        @ApiResponse(responseCode = "400", description = "Validierungsfehler"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Kein Zugriff"),
        @ApiResponse(responseCode = "404", description = "Nicht gefunden")
    })
    public ResponseEntity<?> get(@RequestParam String worldIdStr, HttpServletRequest req) {
        String userId = currentUserId(req);
        if (userId == null) return unauthorized();
        Optional<WWorld> opt = worldService.getByWorldId(worldIdStr);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","world not found"));
        WWorld w = opt.get();
        // Zugriff: Owner darf immer; publicFlag erlaubt Zugriff; andere nicht
        if (!w.getOwner().contains(userId) && !w.isPublicFlag()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","access denied"));
        }
        return ResponseEntity.ok(worldToMap(w));
    }

    @PostMapping
    @Operation(summary = "Child World erstellen", description = "Erstellt eine Child World unter einer Main World (owner-Recht erforderlich)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Erstellt"),
        @ApiResponse(responseCode = "400", description = "Validierungsfehler"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Kein Zugriff"),
        @ApiResponse(responseCode = "409", description = "Bereits vorhanden")
    })
    public ResponseEntity<?> create(@RequestBody CreateChildWorldRequest req, HttpServletRequest http) {
        String userId = currentUserId(http);
        if (userId == null) return unauthorized();
        if (req.worldId() == null || req.worldId().isBlank()) return bad("worldId blank");
        WorldId worldId;
        try { worldId = WorldId.of(req.worldId()).get(); } catch (NoSuchElementException e) { return bad(e.getMessage()); }
        if (worldId.isMain()) return bad("world must not be main (needs zone)");
        // Haupt-Welt laden
        Optional<WWorld> mainOpt = worldService.getByWorldId(worldId);
        if (mainOpt.isEmpty()) return bad("main world not found: " + worldId);
        WWorld main = mainOpt.get();
        if (!main.getOwner().contains(userId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","not an owner of main world"));
        try {
            worldId = WorldId.of(req.worldId()).get(); // nochmal parsen um sicherzugehen
            WWorld created = worldService.createWorld(worldId, main.getPublicData(), main.getWorldId(), req.enabled());
            // publicFlag separat updaten falls gesetzt
            if (req.publicFlag() != null || req.enabled() != null) {
                worldService.updateWorld(worldId, w -> {
                    if (req.publicFlag() != null) w.setPublicFlag(req.publicFlag());
                    if (req.enabled() != null) w.setEnabled(req.enabled());
                });
                created = worldService.getByWorldId(created.getWorldId()).orElse(created);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(worldToMap(created));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping
    @Operation(summary = "Child World aktualisieren", description = "Ändert Attribute einer existierenden Child World")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aktualisiert"),
        @ApiResponse(responseCode = "400", description = "Validierungsfehler"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Kein Zugriff"),
        @ApiResponse(responseCode = "404", description = "Nicht gefunden")
    })
    public ResponseEntity<?> update(@RequestParam String worldIdStr, @RequestBody UpdateChildWorldRequest req, HttpServletRequest http) {
        String userId = currentUserId(http);
        if (userId == null) return unauthorized();
        WorldId worldId;
        try { worldId = WorldId.of(worldIdStr).get(); } catch (NoSuchElementException e) { return bad(e.getMessage()); }
        if (worldId.isMain()) return bad("cannot update main world here");
        Optional<WWorld> opt = worldService.getByWorldId(worldIdStr);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","world not found"));
        WWorld existing = opt.get();
        // parent prüfen (sollte worldId des main sein)
        if (existing.getParent() == null || !existing.getParent().equals(worldId.getId())) return bad("world has invalid parent");
        Optional<WWorld> mainOpt = worldService.getByWorldId(worldId.getId());
        if (mainOpt.isEmpty()) return bad("main world not found: " + worldId);
        if (!mainOpt.get().getOwner().contains(userId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","not owner of main world"));
        worldService.updateWorld(worldId, w -> {
            if (req.enabled() != null) w.setEnabled(req.enabled());
            if (req.publicFlag() != null) w.setPublicFlag(req.publicFlag());
            if (req.editor() != null) w.setEditor(req.editor());
            if (req.player() != null) w.setPlayer(req.player());
            if (req.owners() != null) w.setOwner(req.owners());
            if (req.supporter() != null) w.setSupporter(req.supporter());
        });
        WWorld updated = worldService.getByWorldId(worldIdStr).orElse(existing);
        return ResponseEntity.ok(worldToMap(updated));
    }

    @DeleteMapping
    @Operation(summary = "Child World löschen", description = "Löscht eine Child World (owner der Main World erforderlich)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Gelöscht"),
        @ApiResponse(responseCode = "400", description = "Validierungsfehler"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Kein Zugriff"),
        @ApiResponse(responseCode = "404", description = "Nicht gefunden"),
        @ApiResponse(responseCode = "500", description = "Löschfehler")
    })
    public ResponseEntity<?> delete(@RequestParam String worldIdStr, HttpServletRequest http) {
        String userId = currentUserId(http);
        if (userId == null) return unauthorized();
        WorldId worldId;
        try { worldId = WorldId.of(worldIdStr).get(); } catch (NoSuchElementException e) { return bad(e.getMessage()); }
        if (worldId.isMain()) return bad("cannot delete main world here");
        Optional<WWorld> opt = worldService.getByWorldId(worldIdStr);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","world not found"));
        Optional<WWorld> mainOpt = worldService.getByWorldId(worldId.getId());
        if (mainOpt.isEmpty()) return bad("main world not found: " + worldId);
        if (!mainOpt.get().getOwner().contains(userId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","not owner of main world"));
        boolean deleted = worldService.deleteWorld(worldId);
        if (deleted) return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error","delete failed"));
    }

    private Map<String,Object> worldToMap(WWorld w) {
        return Map.of(
                "worldId", w.getWorldId(),
                "enabled", w.isEnabled(),
                "parent", w.getParent(),
                "publicFlag", w.isPublicFlag(),
                "owner", w.getOwner(),
                "editor", w.getEditor(),
                "player", w.getPlayer()
        );
    }

    private ResponseEntity<?> bad(String msg) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg)); }
    private ResponseEntity<?> unauthorized() { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","unauthorized")); }
}
