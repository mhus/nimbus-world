package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.ActorRoles;
import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.shared.types.UserId;
import de.mhus.nimbus.world.control.service.UniverseClientService;
import de.mhus.nimbus.world.shared.access.AccessService;
import de.mhus.nimbus.world.shared.access.RequireSectorRole;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final RUserService userService;
    private final RCharacterService characterService;
    private final AccessService accessService;

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

    // --- Universe-to-Sector: User management ---

    public record CreateUserRequest(String username, String email) {}

    @Operation(summary = "Create user from universe", description = "Creates a sector user if not exists. Authenticated via Universe Bearer token.")
    @PostMapping("/user")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req) {
        if (req.username() == null || req.username().isBlank() || req.email() == null || req.email().isBlank()) {
            return bad("username and email are required");
        }
        if (userService.getByUsername(req.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "User already exists"));
        }
        var publicData = new de.mhus.nimbus.shared.types.PlayerUser();
        publicData.setUserId(req.username());
        publicData.setTitle(req.username());
        var user = userService.createUser(publicData, req.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", user.getUsername()));
    }

    // --- Universe-to-Sector: Prepare Login ---

    public record PrepareLoginRequest(String userId, String worldId) {}
    public record CharacterInfo(String name, String title, String portraitPath) {}
    public record PrepareLoginResponse(boolean userExists, List<String> actors, List<CharacterInfo> characters) {}

    @Operation(summary = "Prepare login for universe user",
            description = "Checks user existence, available actors, and lists characters. Authenticated via Universe Bearer token.")
    @PostMapping("/prepareLogin")
    public ResponseEntity<?> prepareLogin(@RequestBody PrepareLoginRequest req) {
        if (req.userId() == null || req.userId().isBlank() || req.worldId() == null || req.worldId().isBlank()) {
            return bad("userId and worldId are required");
        }

        // Check user exists
        var userOpt = userService.getByUsername(req.userId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(new PrepareLoginResponse(false, List.of(), List.of()));
        }

        // Get world
        var worldOpt = worldService.getByWorldId(req.worldId());
        if (worldOpt.isEmpty()) {
            return bad("World not found: " + req.worldId());
        }
        WWorld world = worldOpt.get();

        // Available actors
        UserId userId = UserId.of(req.userId()).orElse(null);
        List<String> actors = world.getActorRolesForUser(userId).stream()
                .map(ActorRoles::name)
                .toList();

        // Characters for this user in this region
        var worldId = WorldId.of(req.worldId());
        String regionId = worldId.get().getRegionId();
        var characters = characterService.listCharacters(req.userId(), regionId).stream()
                .map(c -> new CharacterInfo(
                        c.getName(),
                        c.getPublicData() != null ? c.getPublicData().getTitle() : c.getName(),
                        c.getPublicData() != null ? c.getPublicData().getPortraitPath() : null
                ))
                .toList();

        return ResponseEntity.ok(new PrepareLoginResponse(true, actors, characters));
    }

    // --- Universe-to-Sector: Agent Login ---

    public record AgentLoginRequest(String userId, String worldId) {}
    public record AgentLoginResponse(String accessToken, List<String> accessUrls, String jumpUrl) {}

    @Operation(summary = "Agent login from universe",
            description = "Creates an agent login for a universe user. Authenticated via Universe Bearer token.")
    @PostMapping("/agentLogin")
    public ResponseEntity<?> agentLogin(@RequestBody AgentLoginRequest req) {
        if (req.userId() == null || req.userId().isBlank() || req.worldId() == null || req.worldId().isBlank()) {
            return bad("userId and worldId are required");
        }
        try {
            var agentRequest = de.mhus.nimbus.world.shared.dto.DevAgentLoginRequest.builder()
                    .worldId(req.worldId())
                    .userId(req.userId())
                    .build();
            var response = accessService.devAgentLogin(agentRequest);
            return ResponseEntity.ok(new AgentLoginResponse(
                    response.getAccessToken(),
                    response.getAccessUrls(),
                    response.getJumpUrl()
            ));
        } catch (Exception e) {
            return bad("Agent login failed: " + e.getMessage());
        }
    }
}
