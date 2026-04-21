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
import de.mhus.nimbus.world.shared.region.RegionCharacterSettings;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class UniverseController extends BaseEditorController {

    private final UniverseClientService universeClientService;
    private final WWorldService worldService;
    private final RUserService userService;
    private final RCharacterService characterService;
    private final AccessService accessService;
    private final WPlayerSessionService playerSessionService;
    private final WHexGridService hexGridService;
    private final WProgressService progressService;
    private final WAnythingService anythingService;
    private final RegionCharacterSettings characterSettings;

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

    @Operation(summary = "Unpair from universe", description = "Disconnects from universe, deletes keys and worlds")
    @DeleteMapping("/unpair")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> unpair() {
        var result = universeClientService.unpair();
        if (result.ok()) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.ok(Map.of("ok", false, "error", result.error()));
    }

    @Operation(summary = "Sync world with universe", description = "Registers or unregisters a world at the universe based on its sync flag")
    @PostMapping("/world/{worldId}/sync")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> syncWorld(@PathVariable String worldId) {
        var worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return notFound("World not found: " + worldId);
        }
        var result = universeClientService.syncWorld(worldOpt.get());
        if (result.ok()) {
            return ResponseEntity.ok(Map.of("ok", true, "name", result.name()));
        }
        return ResponseEntity.ok(Map.of("ok", false, "error", result.error()));
    }

    @Operation(summary = "Sync user with universe", description = "Queries universe for user data and returns it")
    @GetMapping("/user/{username}/sync")
    @RequireSectorRole(SectorRoles.ADMIN)
    public ResponseEntity<?> syncUser(@PathVariable String username) {
        var info = universeClientService.getUserInfo(username);
        if (info == null) {
            return ResponseEntity.ok(Map.of("found", false));
        }
        var result = new java.util.HashMap<String, Object>();
        result.put("found", true);
        result.put("username", info.username());
        result.put("email", info.email());
        if (info.language() != null) result.put("language", info.language());
        if (info.enabled() != null) result.put("enabled", info.enabled());
        return ResponseEntity.ok(result);
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

    public record CreateUserRequest(String username, String email, String language, Boolean enabled) {}

    @Operation(summary = "Create user from universe", description = "Creates a sector user if not exists, or updates language if exists. Authenticated via Universe Bearer token.")
    @PostMapping("/user")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req) {
        if (req.username() == null || req.username().isBlank() || req.email() == null || req.email().isBlank()) {
            return bad("username and email are required");
        }
        var existingOpt = userService.getByUsername(req.username());
        if (existingOpt.isPresent()) {
            // Sync language and enabled from universe
            var existing = existingOpt.get();
            boolean changed = false;
            if (req.language() != null && !req.language().equals(existing.getLanguage())) {
                existing.setLanguage(req.language());
                changed = true;
            }
            if (req.enabled() != null && req.enabled() != existing.isEnabled()) {
                if (req.enabled()) existing.enable(); else existing.disable();
                changed = true;
            }
            if (changed) {
                existing.touchUpdate();
                userService.save(existing);
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "User already exists"));
        }
        var publicData = new de.mhus.nimbus.shared.types.PlayerUser();
        publicData.setName(req.username());
        publicData.setTitle(req.username());
        var user = userService.createUser(publicData, req.email());
        boolean needsSave = false;
        if (req.language() != null) {
            user.setLanguage(req.language());
            needsSave = true;
        }
        if (req.enabled() != null && !req.enabled()) {
            user.disable();
            needsSave = true;
        }
        if (needsSave) {
            user.touchUpdate();
            userService.save(user);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", user.getName()));
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

    // --- Universe-to-Sector: Prepare New Character ---

    public record PrepareNewCharacterRequest(String userId, String worldId) {}
    public record CharacterTemplateInfo(String name, String title, String description) {}
    public record PrepareNewCharacterResponse(boolean canCreate, int currentCount, int maxCount, List<CharacterTemplateInfo> templates) {}

    @Operation(summary = "Prepare new character creation",
            description = "Returns available character templates and whether the user can create more characters. Authenticated via Universe Bearer token.")
    @PostMapping("/prepareNewCharacter")
    public ResponseEntity<?> prepareNewCharacter(@RequestBody PrepareNewCharacterRequest req) {
        if (req.userId() == null || req.userId().isBlank() || req.worldId() == null || req.worldId().isBlank()) {
            return bad("userId and worldId are required");
        }

        var userOpt = userService.getByUsername(req.userId());
        if (userOpt.isEmpty()) {
            return bad("User not found: " + req.userId());
        }

        var worldOpt = worldService.getByWorldId(req.worldId());
        if (worldOpt.isEmpty()) {
            return bad("World not found: " + req.worldId());
        }

        var worldId = WorldId.of(req.worldId());
        String regionId = worldId.get().getRegionId();

        // Check character limit
        var user = userOpt.get();
        Integer userLimit = user.getCharacterLimitForRegion(regionId);
        int effectiveLimit = userLimit != null ? userLimit : characterSettings.getMaxPerRegion();
        int currentCount = characterService.listCharacters(req.userId(), regionId).size();
        boolean canCreate = currentCount < effectiveLimit;

        // Load character templates from WAnything with collection 'character-templates' for the region
        String regionWorldId = WorldId.of(WorldId.COLLECTION_REGION, regionId).get().getId();
        var templates = anythingService.findByWorldIdAndCollectionAndEnabled(regionWorldId, "character-templates", true)
                .stream()
                .map(a -> new CharacterTemplateInfo(
                        a.getName(),
                        a.getTitle() != null ? a.getTitle() : a.getName(),
                        a.getDescription()
                ))
                .toList();

        return ResponseEntity.ok(new PrepareNewCharacterResponse(canCreate, currentCount, effectiveLimit, templates));
    }

    // --- Universe-to-Sector: Create Character ---

    public record CreateCharacterRequest(String userId, String worldId, String name, String title, String templateName) {}
    public record CreateCharacterResponse(String name, String title) {}

    @Operation(summary = "Create a new character",
            description = "Creates a new character for the user in the region. Authenticated via Universe Bearer token.")
    @PostMapping("/createCharacter")
    public ResponseEntity<?> createCharacter(@RequestBody CreateCharacterRequest req) {
        if (req.userId() == null || req.userId().isBlank() || req.worldId() == null || req.worldId().isBlank()
                || req.name() == null || req.name().isBlank()) {
            return bad("userId, worldId and name are required");
        }

        // Validate character name format
        if (!req.name().matches("[a-zA-Z0-9_-]{3,64}")) {
            return bad("Character name must be 3-64 characters, only a-zA-Z0-9_-");
        }

        var worldOpt = worldService.getByWorldId(req.worldId());
        if (worldOpt.isEmpty()) {
            return bad("World not found: " + req.worldId());
        }

        var worldId = WorldId.of(req.worldId());
        String regionId = worldId.get().getRegionId();

        // Check if character name already exists in the region
        if (characterService.findByRegionAndName(regionId, req.name()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Character name already exists in this region: " + req.name()));
        }

        String display = req.title() != null && !req.title().isBlank() ? req.title() : req.name();

        try {
            var character = characterService.createCharacter(req.userId(), regionId, req.name(), display);

            // If a template was specified, apply template data
            if (req.templateName() != null && !req.templateName().isBlank()) {
                String regionWorldId = WorldId.of(WorldId.COLLECTION_REGION, regionId).get().getId();
                var templateOpt = anythingService.findByWorldIdAndCollectionAndName(regionWorldId, "character-templates", req.templateName());
                if (templateOpt.isPresent()) {
                    applyCharacterTemplate(character, templateOpt.get());
                }
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(new CreateCharacterResponse(character.getName(),
                    character.getPublicData() != null ? character.getPublicData().getTitle() : character.getName()));
        } catch (IllegalStateException e) {
            return bad(e.getMessage());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyCharacterTemplate(de.mhus.nimbus.world.shared.region.RCharacter character, WAnything template) {
        if (template.getData() == null) return;
        try {
            Map<String, Object> data;
            if (template.getData() instanceof Map) {
                data = (Map<String, Object>) template.getData();
            } else {
                return;
            }

            // Apply portrait path from template
            if (data.containsKey("portraitPath") && character.getPublicData() != null) {
                character.getPublicData().setPortraitPath((String) data.get("portraitPath"));
            }

            // Apply third person model from template
            if (data.containsKey("thirdPersonModelId") && character.getPublicData() != null) {
                character.getPublicData().setThirdPersonModelId((String) data.get("thirdPersonModelId"));
            }

            // Apply skills from template
            if (data.containsKey("skills") && data.get("skills") instanceof Map) {
                Map<String, Object> skills = (Map<String, Object>) data.get("skills");
                for (var entry : skills.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        character.setSkill(entry.getKey(), ((Number) entry.getValue()).intValue());
                    }
                }
            }

            // Apply skill points from template
            if (data.containsKey("skillPoints") && data.get("skillPoints") instanceof Number) {
                character.setSkillPoints(((Number) data.get("skillPoints")).intValue());
            }

            // Apply silver from template
            if (data.containsKey("silver") && data.get("silver") instanceof Number) {
                character.setSilver(((Number) data.get("silver")).longValue());
            }

            characterService.updateCharater(character);
        } catch (Exception e) {
            log.warn("Failed to apply character template '{}': {}", template.getName(), e.getMessage());
        }
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

    public record InstancesRequest(String worldId, String playerId, String actor) {}
    public record InstanceInfo(String instanceId, String title, String creator, List<String> players, java.time.Instant createdAt) {}

    @Operation(summary = "List instances for player",
            description = "Returns instances accessible by the player. For EDITOR actor, returns synthetic epoch instances. Authenticated via Universe Bearer token.")
    @PostMapping("/instances")
    public ResponseEntity<?> listInstances(@RequestBody InstancesRequest req) {
        if (req.worldId() == null || req.worldId().isBlank()) {
            return bad("worldId is required");
        }
        try {
            // EDITOR actor: return synthetic epoch instances
            if ("EDITOR".equals(req.actor())) {
                String username = extractUsername(req.playerId());
                var editorInstances = worldService.getEditorInstances(req.worldId(), username);
                var result = editorInstances.stream()
                        .map(i -> new InstanceInfo(
                                i.getInstanceId(),
                                i.getTitle(),
                                i.getCreator(),
                                i.getPlayers(),
                                i.getCreatedAt()
                        ))
                        .toList();
                return ResponseEntity.ok(result);
            }

            // SUPPORT actor: return all instances (if user has SUPPORT role)
            boolean allInstances = false;
            if ("SUPPORT".equals(req.actor())) {
                String username = extractUsername(req.playerId());
                UserId userId = UserId.of(username).orElse(null);
                if (userId != null) {
                    var world = worldService.getByWorldId(req.worldId()).orElse(null);
                    if (world != null && world.getActorRolesForUser(userId).contains(ActorRoles.SUPPORT)) {
                        allInstances = true;
                    }
                }
            }

            var instances = accessService.getInstancesForPlayer(req.worldId(), req.playerId(), allInstances);
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

    private String extractUsername(String playerId) {
        if (playerId == null) return null;
        // playerId format: @username:characterId
        String id = playerId.startsWith("@") ? playerId.substring(1) : playerId;
        int colonIdx = id.indexOf(':');
        return colonIdx > 0 ? id.substring(0, colonIdx) : id;
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
            ActorRoles actor = ActorRoles.valueOf(req.actor());

            // Validate actor role and instance combination
            var validationError = validateActorInstance(req.userId(), req.worldId(), actor, req.instanceId(), req.characterId());
            if (validationError != null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", validationError));
            }

            var sessionRequest = de.mhus.nimbus.world.shared.dto.DevSessionLoginRequest.builder()
                    .worldId(req.worldId())
                    .userId(req.userId())
                    .characterId(req.characterId())
                    .actor(actor)
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

    /**
     * Validates that the actor role matches the chosen instance.
     * - EDITOR: only synthetic instances (e- prefix), requires EDITOR role
     * - SUPPORT: any instance, requires SUPPORT role
     * - PLAYER: only instances where player is allowed
     *
     * @return error message if validation fails, null if ok
     */
    private String validateActorInstance(String userId, String worldId, ActorRoles actor, String instanceId, String characterId) {
        UserId uid = UserId.of(userId).orElse(null);
        if (uid == null) return "Invalid userId";

        var world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) return "World not found";

        var allowedActors = world.getActorRolesForUser(uid);
        if (!allowedActors.contains(actor)) {
            return "User does not have " + actor + " role for this world";
        }

        if (instanceId == null || instanceId.isBlank()) {
            // No instance selected — only allowed for PLAYER (new instance)
            if (actor == ActorRoles.EDITOR || actor == ActorRoles.SUPPORT) {
                return actor + " must select an instance";
            }
            return null;
        }

        switch (actor) {
            case EDITOR -> {
                // EDITOR may only use synthetic epoch instances (e- prefix)
                if (!instanceId.startsWith("e-")) {
                    return "Editor can only use epoch instances";
                }
            }
            case SUPPORT -> {
                // SUPPORT can access all instances — no further check needed
            }
            case PLAYER -> {
                // PLAYER must be allowed in the instance
                String playerId = "@" + userId + ":" + characterId;
                var instance = accessService.getInstancesForPlayer(worldId, playerId, false).stream()
                        .filter(i -> instanceId.equals(i.getInstanceId()))
                        .findFirst();
                if (instance.isEmpty()) {
                    return "Player does not have access to this instance";
                }
            }
        }
        return null;
    }

    // --- Universe-to-Sector: Entry Points ---

    public record EntryPointsRequest(String worldId, String userId, String characterId, String instanceId) {}
    public record HexGridInfo(int q, int r, String title, String icon, boolean hasEntryPoint, String color) {}
    public record EntryPointsResponse(boolean hasLastPosition, List<HexGridInfo> visitedGrids) {}

    @Operation(summary = "Get available entry points",
            description = "Returns last position availability and visited hex grids. Authenticated via Universe Bearer token.")
    @PostMapping("/entryPoints")
    public ResponseEntity<?> getEntryPoints(@RequestBody EntryPointsRequest req) {
        if (req.worldId() == null || req.userId() == null || req.characterId() == null) {
            return bad("worldId, userId and characterId are required");
        }

        String playerId = "@" + req.userId() + ":" + req.characterId();
        log.info("entryPoints: worldId={}, userId={}, characterId={}, instanceId={}, playerId={}",
                req.worldId(), req.userId(), req.characterId(), req.instanceId(), playerId);

        // Determine effective worldId (with instance if provided)
        // instanceId may be a full worldId (region:world::uuid) or just a UUID
        String effectiveWorldId = req.worldId();
        if (req.instanceId() != null && !req.instanceId().isBlank()) {
            if (req.instanceId().contains(":")) {
                // Already a full worldId with instance
                effectiveWorldId = req.instanceId();
            } else {
                // Just a UUID — append to base worldId
                effectiveWorldId = WorldId.worldWithInstance(req.worldId(), req.instanceId());
            }
        }

        log.info("entryPoints: effectiveWorldId={}", effectiveWorldId);

        // Check if last position exists
        boolean hasLastPosition = playerSessionService.loadSession(effectiveWorldId, playerId).isPresent();
        log.info("entryPoints: hasLastPosition={}", hasLastPosition);

        // Get visited hex grids from progress — stored with effective worldId (with instance)
        List<HexGridInfo> visitedGrids = List.of();
        if (req.instanceId() != null && !req.instanceId().isBlank()) {
            // Only for existing instances, not new ones
            var progressEntries = progressService.findByWorldIdAndPlayerIdAndType(effectiveWorldId, playerId, "EXPLORED_HEX");
            log.info("entryPoints: progressEntries={}", progressEntries.size());
            var allHexGrids = hexGridService.findByWorldId(req.worldId()); // base world hex grids
            log.info("entryPoints: allHexGrids={}", allHexGrids.size());
            var hexGridMap = new java.util.HashMap<String, WHexGrid>();
            allHexGrids.forEach(g -> hexGridMap.put(g.getPosition(), g));

            visitedGrids = progressEntries.stream()
                    .filter(p -> p.getQuest() != null)
                    .map(p -> {
                        // quest field contains hex position as "q;r"
                        String hexPos = p.getQuest();
                        WHexGrid grid = hexGridMap.get(hexPos);
                        int q = 0, r = 0;
                        if (p.getProgressData() != null) {
                            Object qObj = p.getProgressData().get("q");
                            Object rObj = p.getProgressData().get("r");
                            if (qObj instanceof Number) q = ((Number) qObj).intValue();
                            if (rObj instanceof Number) r = ((Number) rObj).intValue();
                        }
                        String title = hexPos;
                        String icon = null;
                        boolean hasEntryPoint = false;
                        if (grid != null && grid.getPublicData() != null) {
                            var pd = grid.getPublicData();
                            if (pd.getTitle() != null) title = pd.getTitle();
                            icon = pd.getIcon();
                            hasEntryPoint = pd.getEntryPoint() != null;
                        }
                        String color = grid != null && grid.getParameters() != null
                                ? grid.getParameters().get("p_color") : null;
                        return new HexGridInfo(q, r, title, icon, hasEntryPoint, color);
                    })
                    .toList();
        }

        return ResponseEntity.ok(new EntryPointsResponse(hasLastPosition, visitedGrids));
    }
}
