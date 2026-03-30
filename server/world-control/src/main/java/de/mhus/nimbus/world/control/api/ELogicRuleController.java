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
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST logic rules: worldId={}, query={}, epoch={}, offset={}, limit={}", worldId, query, epoch, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        String lookupWorldId = wid.toBaseWorldId().getId();

        List<WLogicRule> all = ruleRepository.findByWorldId(lookupWorldId);

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

        return ResponseEntity.ok(Map.of(
                "rules", ruleDtos,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
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

    // --- Helper methods ---

    private Map<String, Object> toDto(WLogicRule rule) {
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", rule.getId());
        dto.put("worldId", rule.getWorldId());
        dto.put("name", rule.getName());
        dto.put("description", rule.getDescription());
        dto.put("affected", rule.getAffected());
        dto.put("spelCondition", rule.getSpelCondition());
        dto.put("effects", rule.getEffects());
        dto.put("epoches", rule.getEpoches());
        dto.put("enabled", rule.isEnabled());
        dto.put("priority", rule.getPriority());
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
