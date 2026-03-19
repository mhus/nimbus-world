package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.ActorRoles;
import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.shared.types.UserId;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
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
    private final WPlayerSessionService playerSessionService;
    private final WHexGridService hexGridService;
    private final WProgressService progressService;

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

    // --- Universe-to-Sector: Instances ---

    public record InstancesRequest(String worldId, String playerId) {}
    public record InstanceInfo(String instanceId, String title, String creator, List<String> players, java.time.Instant createdAt) {}

    @Operation(summary = "List instances for player",
            description = "Returns instances accessible by the player. Authenticated via Universe Bearer token.")
    @PostMapping("/instances")
    public ResponseEntity<?> listInstances(@RequestBody InstancesRequest req) {
        if (req.worldId() == null || req.worldId().isBlank()) {
            return bad("worldId is required");
        }
        try {
            var instances = accessService.getInstancesForPlayer(req.worldId(), req.playerId(), false);
            var result = instances.stream()
                    .map(i -> new InstanceInfo(
                            i.getInstanceId(),
                            i.getTitle(),
                            i.getCreator(),
                            i.getPlayers(),
                            i.getCreatedAt()
                    ))
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad("Failed to list instances: " + e.getMessage());
        }
    }

    // --- Universe-to-Sector: Session Login ---

    public record SessionLoginRequest(String userId, String worldId, String characterId, String actor, String instanceId, String entryPoint) {}

    @Operation(summary = "Session login from universe",
            description = "Creates a session login for a universe user. Authenticated via Universe Bearer token.")
    @PostMapping("/sessionLogin")
    public ResponseEntity<?> sessionLogin(@RequestBody SessionLoginRequest req) {
        if (req.userId() == null || req.worldId() == null || req.characterId() == null || req.actor() == null) {
            return bad("userId, worldId, characterId and actor are required");
        }
        try {
            var sessionRequest = de.mhus.nimbus.world.shared.dto.DevSessionLoginRequest.builder()
                    .worldId(req.worldId())
                    .userId(req.userId())
                    .characterId(req.characterId())
                    .actor(ActorRoles.valueOf(req.actor()))
                    .entryPoint(req.entryPoint())
                    .instanceId(req.instanceId())
                    .build();
            var response = accessService.devSessionLogin(sessionRequest);
            return ResponseEntity.ok(new AgentLoginResponse(
                    response.getAccessToken(),
                    response.getAccessUrls(),
                    response.getJumpUrl()
            ));
        } catch (Exception e) {
            return bad("Session login failed: " + e.getMessage());
        }
    }

    // --- Universe-to-Sector: Entry Points ---

    public record EntryPointsRequest(String worldId, String userId, String characterId, String instanceId) {}
    public record HexGridInfo(int q, int r, String title, String icon, boolean hasEntryPoint) {}
    public record EntryPointsResponse(boolean hasLastPosition, List<HexGridInfo> visitedGrids) {}

    @Operation(summary = "Get available entry points",
            description = "Returns last position availability and visited hex grids. Authenticated via Universe Bearer token.")
    @PostMapping("/entryPoints")
    public ResponseEntity<?> getEntryPoints(@RequestBody EntryPointsRequest req) {
        if (req.worldId() == null || req.userId() == null || req.characterId() == null) {
            return bad("worldId, userId and characterId are required");
        }

        String playerId = "@" + req.userId() + ":" + req.characterId();

        // Determine effective worldId (with instance if provided)
        String effectiveWorldId = req.worldId();
        if (req.instanceId() != null && !req.instanceId().isBlank()) {
            // Append instance to worldId: regionId:worldName::instanceId
            var wid = WorldId.of(req.worldId());
            if (wid.isPresent()) {
                effectiveWorldId = wid.get().toWorldWithInstance(req.instanceId()).getFullId();
            }
        }

        // Check if last position exists
        boolean hasLastPosition = playerSessionService.loadSession(effectiveWorldId, playerId).isPresent();

        // Get visited hex grids from progress (type="exploration")
        List<HexGridInfo> visitedGrids = List.of();
        if (req.instanceId() != null && !req.instanceId().isBlank()) {
            // Only for existing instances, not new ones
            var progressEntries = progressService.findByWorldIdAndPlayerIdAndType(effectiveWorldId, playerId, "exploration");
            var allHexGrids = hexGridService.findByWorldId(req.worldId()); // base world hex grids
            var hexGridMap = new java.util.HashMap<String, WHexGrid>();
            allHexGrids.forEach(g -> hexGridMap.put(g.getPosition(), g));

            visitedGrids = progressEntries.stream()
                    .filter(p -> p.getProgressData() != null && p.getProgressData().containsKey("hexPosition"))
                    .map(p -> {
                        String hexPos = (String) p.getProgressData().get("hexPosition");
                        WHexGrid grid = hexGridMap.get(hexPos);
                        if (grid == null || grid.getPublicData() == null) return null;
                        var pd = grid.getPublicData();
                        return new HexGridInfo(
                                pd.getPosition() != null ? pd.getPosition().getQ() : 0,
                                pd.getPosition() != null ? pd.getPosition().getR() : 0,
                                pd.getTitle(),
                                pd.getIcon(),
                                pd.getEntryPoint() != null
                        );
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        return ResponseEntity.ok(new EntryPointsResponse(hasLastPosition, visitedGrids));
    }
}
