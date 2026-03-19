package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.world.control.service.UniverseClientService;
import de.mhus.nimbus.world.shared.access.RequireSectorRole;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/control/universe")
@RequiredArgsConstructor
@Tag(name = "Universe", description = "Manage universe connection")
public class UniverseController extends BaseEditorController {

    private final UniverseClientService universeClientService;
    private final WWorldService worldService;

    // --- Admin endpoints (require SECTOR_ADMIN via session cookie) ---

    @Operation(summary = "Get universe status")
    @GetMapping("/status")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(universeClientService.getStatus());
    }

    @Operation(summary = "Get universe URL")
    @GetMapping("/url")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> getUrl() {
        return ResponseEntity.ok(Map.of("url", universeClientService.getUniverseUrl()));
    }

    @Operation(summary = "Save universe URL")
    @PutMapping("/url")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> saveUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return bad("url is required");
        }
        universeClientService.setUniverseUrl(url);
        return ResponseEntity.ok(Map.of("url", url.trim()));
    }

    @Operation(summary = "Ping universe")
    @PostMapping("/ping")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> ping() {
        var result = universeClientService.ping();
        if (result.ok()) {
            return ResponseEntity.ok(Map.of("ok", true, "name", "Universe", "status", result.status()));
        }
        return ResponseEntity.ok(Map.of("ok", false, "error", result.error()));
    }

    @Operation(summary = "Pair with universe")
    @PostMapping("/pair")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> pair(@RequestBody Map<String, String> body) {
        String inviteToken = body.get("inviteToken");
        if (inviteToken == null || inviteToken.isBlank()) {
            return bad("inviteToken is required");
        }
        var result = universeClientService.pair(inviteToken);
        if (result.ok()) {
            return ResponseEntity.ok(Map.of("ok", true, "name", result.name()));
        }
        return ResponseEntity.ok(Map.of("ok", false, "error", result.error()));
    }

    // --- Universe-to-Sector endpoints (authenticated via Universe Bearer token in ControlAccessFilter) ---

    public record WorldInfo(String worldId, String name, String description, boolean publicWorld, List<String> members) {}

    @Operation(summary = "List main worlds", description = "Returns enabled main worlds (no zones) for universe sync. Authenticated via Universe Bearer token.")
    @GetMapping("/worlds")
    public ResponseEntity<List<WorldInfo>> listMainWorlds() {
        List<WorldInfo> mainWorlds = worldService.findAll().stream()
                .filter(w -> {
                    var wid = WorldId.of(w.getWorldId());
                    return wid.get().isMain() && w.isEnabled();
                })
                .filter(w -> w.isUniverseSync())
                .map(w -> {
                    boolean isPublic = w.isPublicFlag();
                    List<String> members;
                    if (isPublic) {
                        members = List.of("*");
                    } else {
                        Set<String> all = new LinkedHashSet<>();
                        if (w.getOwner() != null) all.addAll(w.getOwner());
                        if (w.getEditor() != null) all.addAll(w.getEditor());
                        if (w.getSupporter() != null) all.addAll(w.getSupporter());
                        if (w.getPlayer() != null) all.addAll(w.getPlayer());
                        members = new ArrayList<>(all);
                    }
                    return new WorldInfo(
                            w.getWorldId(),
                            w.getPublicData() != null ? w.getPublicData().getTitle() : w.getWorldId(),
                            w.getDescription(),
                            isPublic,
                            members
                    );
                })
                .toList();
        return ResponseEntity.ok(mainWorlds);
    }
}
