package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.access.RequireWorldRole;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.LogicEffect;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Logic Rule CRUD operations.
 * Base path: /control/worlds/{worldId}/logic-rules
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/logic-rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Logic Rules", description = "Logic Machine rule management")
@RequireWorldRole(WorldRoles.EDITOR)
public class ELogicRuleController extends BaseEditorController {

    private final WLogicRuleRepository ruleRepository;
    private final de.mhus.nimbus.world.shared.world.WLogicRuleService ruleService;
    private final de.mhus.nimbus.world.shared.world.LogicConditionService conditionService;
    private final de.mhus.nimbus.world.shared.client.WorldClientService worldClientService;

    @GetMapping
    @Operation(summary = "List all Logic Rules")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query on name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by epoch") @RequestParam(required = false) Integer epoch,
            @Parameter(description = "Filter by rulePackage") @RequestParam(required = false) String rulePackage,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST logic rules: worldId={}, query={}, epoch={}, rulePackage={}, offset={}, limit={}", worldId, query, epoch, rulePackage, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        String lookupWorldId = wid.toBaseWorldId().getId();

        List<WLogicRule> all;
        if (!Strings.isBlank(rulePackage)) {
            all = ruleRepository.findByWorldIdAndRulePackage(lookupWorldId, rulePackage);
        } else {
            all = ruleRepository.findByWorldId(lookupWorldId);
        }

        // Filter by query (name contains)
        if (!Strings.isBlank(query)) {
            String lowerQuery = query.toLowerCase();
            all = all.stream()
                    .filter(r -> r.getName() != null && r.getName().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        }

        // Filter by epoch
        if (epoch != null) {
            all = all.stream()
                    .filter(r -> r.getEpoches() != null && r.getEpoches().contains(epoch))
                    .collect(Collectors.toList());
        }

        int totalCount = all.size();

        List<Map<String, Object>> ruleDtos = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());

        // Collect distinct packages from ALL rules (unfiltered) for dropdown
        List<String> packages = ruleRepository.findByWorldId(lookupWorldId).stream()
                .map(WLogicRule::getRulePackage)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("rules", ruleDtos);
        response.put("count", totalCount);
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("packages", packages);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Logic Rule by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule found"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Rule identifier") @PathVariable String id) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLogicRule> opt = ruleRepository.findById(id);
        if (opt.isEmpty()) {
            return notFound("rule not found");
        }

        WLogicRule rule = opt.get();
        if (!rule.getWorldId().equals(worldId)) {
            return notFound("rule not found");
        }

        return ResponseEntity.ok(toDto(rule));
    }

    @PostMapping
    @Operation(summary = "Create new Logic Rule")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Rule name already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody Map<String, Object> request) {

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        String name = (String) request.get("name");
        if (Strings.isBlank(name)) {
            return bad("name required");
        }

        String lookupWorldId = wid.toBaseWorldId().getId();

        if (ruleRepository.findByWorldIdAndName(lookupWorldId, name).isPresent()) {
            return conflict("rule name already exists");
        }

        WLogicRule rule = WLogicRule.builder()
                .worldId(lookupWorldId)
                .name(name)
                .description((String) request.get("description"))
                .rulePackage((String) request.get("rulePackage"))
                .testFlags((String) request.get("testFlags"))
                .spelCondition((String) request.get("spelCondition"))
                .effects(toEffectList(request.get("effects")))
                .epoches(toIntList(request.get("epoches")))
                .enabled(request.containsKey("enabled") ? Boolean.TRUE.equals(request.get("enabled")) : true)
                .priority(request.containsKey("priority") ? ((Number) request.get("priority")).intValue() : 100)
                .build();
        // affected is auto-computed by ruleService.save()

        WLogicRule saved = ruleService.save(rule);
        log.info("Created logic rule: id={}, name={}, worldId={}", saved.getId(), saved.getName(), lookupWorldId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Logic Rule")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Rule identifier") @PathVariable String id,
            @RequestBody Map<String, Object> request) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLogicRule> opt = ruleRepository.findById(id);
        if (opt.isEmpty()) {
            return notFound("rule not found");
        }

        WLogicRule rule = opt.get();
        if (!rule.getWorldId().equals(worldId)) {
            return notFound("rule not found");
        }

        boolean changed = false;

        if (request.containsKey("name") && !Strings.isBlank((String) request.get("name"))) {
            rule.setName((String) request.get("name"));
            changed = true;
        }
        if (request.containsKey("description")) {
            rule.setDescription((String) request.get("description"));
            changed = true;
        }
        if (request.containsKey("rulePackage")) {
            rule.setRulePackage((String) request.get("rulePackage"));
            changed = true;
        }
        if (request.containsKey("testFlags")) {
            rule.setTestFlags((String) request.get("testFlags"));
            changed = true;
        }
        // affected is auto-computed by ruleService.save() from spelCondition + effects
        if (request.containsKey("spelCondition")) {
            rule.setSpelCondition((String) request.get("spelCondition"));
            changed = true;
        }
        if (request.containsKey("effects")) {
            rule.setEffects(toEffectList(request.get("effects")));
            changed = true;
        }
        if (request.containsKey("epoches")) {
            rule.setEpoches(toIntList(request.get("epoches")));
            changed = true;
        }
        if (request.containsKey("enabled")) {
            rule.setEnabled(Boolean.TRUE.equals(request.get("enabled")));
            changed = true;
        }
        if (request.containsKey("priority")) {
            rule.setPriority(((Number) request.get("priority")).intValue());
            changed = true;
        }

        if (!changed) {
            return bad("at least one field required for update");
        }

        // updatedAt and affected are set by ruleService.save()
        WLogicRule saved = ruleService.save(rule);

        log.info("Updated logic rule: id={}, name={}", id, saved.getName());
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Logic Rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rule deleted"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Rule identifier") @PathVariable String id) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLogicRule> opt = ruleRepository.findById(id);
        if (opt.isEmpty()) {
            return notFound("rule not found");
        }

        WLogicRule rule = opt.get();
        if (!rule.getWorldId().equals(worldId)) {
            return notFound("rule not found");
        }

        ruleRepository.delete(rule);
        log.info("Deleted logic rule: id={}, name={}", id, rule.getName());
        return ResponseEntity.noContent().build();
    }

    // --- Test / Simulate / Execute ---

    /**
     * Test: evaluate rule condition against live flags.
     * POST /control/worlds/{worldId}/logic-rules/test
     * Body: { ruleId?, spelCondition?, rulePackage?, worldInstanceId }
     */
    @PostMapping("/test")
    @Operation(summary = "Test rule condition against live flags")
    public ResponseEntity<?> testCondition(
            @PathVariable String worldId,
            @RequestBody Map<String, Object> request) {

        String instanceId = (String) request.get("worldInstanceId");
        if (Strings.isBlank(instanceId)) {
            return bad("worldInstanceId required");
        }
        String ruleId = (String) request.get("ruleId");
        return ResponseEntity.ok(conditionService.testCondition(instanceId, ruleId, ruleRepository, request));
    }

    /**
     * Simulate: dry-run rule with user-provided flags (sandbox).
     * POST /control/worlds/{worldId}/logic-rules/simulate
     * Body: { ruleId, flags: { "pkg": { "flag": value } } }
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/simulate")
    @Operation(summary = "Simulate rule with custom flags (sandbox)")
    public ResponseEntity<?> simulate(
            @PathVariable String worldId,
            @RequestBody Map<String, Object> request) {

        String ruleId = (String) request.get("ruleId");
        if (Strings.isBlank(ruleId)) {
            return bad("ruleId required");
        }
        Map<String, Object> flags = (Map<String, Object>) request.get("flags");
        return ResponseEntity.ok(conditionService.simulate(ruleId, ruleRepository, flags));
    }

    /**
     * Execute: run rule live against a world instance.
     * Delegates to world-life via REST (fire & forget with result).
     * POST /control/worlds/{worldId}/logic-rules/execute
     * Body: { ruleId, worldInstanceId }
     */
    @PostMapping("/execute")
    @Operation(summary = "Execute rule live on a world instance")
    public ResponseEntity<?> execute(
            @PathVariable String worldId,
            @RequestBody Map<String, Object> request) {

        String instanceId = (String) request.get("worldInstanceId");
        String ruleId = (String) request.get("ruleId");
        if (Strings.isBlank(instanceId) || Strings.isBlank(ruleId)) {
            return bad("worldInstanceId and ruleId required");
        }

        // Delegate to world-life
        try {
            worldClientService.sendLogicEvent(instanceId,
                    List.of(), // no eval, execute is handled by world-life test endpoint
                    "execute:" + ruleId);

            // For now, return that we triggered execution.
            // A proper implementation would call the /life/logic/execute endpoint synchronously.
            return ResponseEntity.ok(Map.of(
                    "mode", "execute",
                    "worldInstanceId", instanceId,
                    "ruleId", ruleId,
                    "status", "delegated to world-life"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Helper methods ---

    private Map<String, Object> toDto(WLogicRule rule) {
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", rule.getId());
        dto.put("worldId", rule.getWorldId());
        dto.put("name", rule.getName());
        dto.put("description", rule.getDescription());
        dto.put("rulePackage", rule.getRulePackage());
        dto.put("affected", rule.getAffected());
        dto.put("spelCondition", rule.getSpelCondition());
        dto.put("effects", rule.getEffects());
        dto.put("epoches", rule.getEpoches());
        dto.put("enabled", rule.isEnabled());
        dto.put("priority", rule.getPriority());
        dto.put("testFlags", rule.getTestFlags());
        dto.put("createdAt", rule.getCreatedAt());
        dto.put("updatedAt", rule.getUpdatedAt());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> toIntList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(v -> ((Number) v).intValue())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<LogicEffect> toEffectList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(v -> v instanceof Map)
                    .map(v -> {
                        Map<String, Object> map = (Map<String, Object>) v;
                        // Convert parameters to Map<String, String>
                        Map<String, String> params = new java.util.LinkedHashMap<>();
                        Object rawParams = map.get("parameters");
                        if (rawParams instanceof Map<?, ?> paramMap) {
                            paramMap.forEach((k, val) -> params.put(
                                    String.valueOf(k),
                                    val != null ? String.valueOf(val) : null
                            ));
                        }
                        return LogicEffect.builder()
                                .type((String) map.get("type"))
                                .parameters(params)
                                .build();
                    })
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
