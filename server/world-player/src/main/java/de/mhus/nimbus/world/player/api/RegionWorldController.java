package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.generated.types.WorldInfo;
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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/world/region/world")
@RequiredArgsConstructor
@Validated
@Tag(name = "RegionWorld", description = "Verwaltung von Main Worlds im world-provider")
public class RegionWorldController {

    private final WWorldService worldService;

    // DTOs
    public static class CreateWorldRequest { public String worldId; public WorldInfo info; public String getWorldId(){return worldId;} public WorldInfo getInfo(){return info;} }
    public static class WorldResponse { public String worldId; public boolean enabled; public String parent; public WorldInfo info; public WorldResponse(String w, boolean e, String p, WorldInfo i){worldId=w;enabled=e;parent=p;info=i;} }

    @GetMapping(produces = "application/json")
    @Operation(summary = "Main World abrufen", description = "Liefert Daten einer Main World per worldId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gefunden"),
        @ApiResponse(responseCode = "400", description = "Validierungsfehler"),
        @ApiResponse(responseCode = "404", description = "Nicht gefunden")
    })
    public ResponseEntity<?> getWorld(@RequestParam String worldId) {
        if (worldId == null || worldId.isBlank()) return ResponseEntity.badRequest().body(Map.of("error","worldId missing"));
        Optional<WWorld> opt = worldService.getByWorldId(worldId);
        return opt.<ResponseEntity<?>>map(w -> ResponseEntity.ok(toResponse(w)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","world not found")));
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Main World erstellen", description = "Erstellt eine neue Main World (nur worldId ohne Zone)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Erstellt"),
        @ApiResponse(responseCode = "400", description = "Validierungsfehler"),
        @ApiResponse(responseCode = "409", description = "Bereits vorhanden")
    })
    public ResponseEntity<?> createWorld(@RequestBody CreateWorldRequest req) {
        if (req.getWorldId() == null || req.getWorldId().isBlank()) return ResponseEntity.badRequest().body(Map.of("error","worldId blank"));
        WorldId worldId;
        try { worldId = WorldId.of(req.getWorldId()).get(); } catch (NoSuchElementException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        if (!worldId.isMain()) {
            return ResponseEntity.badRequest().body(Map.of("error","only main worlds can be created (no zone)", "worldId", req.getWorldId()));
        }
        try {
            WWorld created = worldService.createWorld(worldId, req.getInfo());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    private WorldResponse toResponse(WWorld w) { return new WorldResponse(w.getWorldId(), w.isEnabled(), w.getParent(), w.getPublicData()); }
}
