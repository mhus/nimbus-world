package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.access.RequireWorldRole;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WLogicStateDef;
import de.mhus.nimbus.world.shared.world.WLogicStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/control/worlds/{worldId}/logic-states")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Logic States", description = "Logic Machine flag definitions")
@RequireWorldRole(WorldRoles.EDITOR)
public class ELogicStateController extends BaseEditorController {

    private final WLogicStateService stateService;

    @GetMapping
    @Operation(summary = "List all Logic State definitions")
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query on name") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId));
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        String lookupWorldId = wid.toBaseWorldId().getId();
        List<WLogicStateDef> all = stateService.findByWorldId(lookupWorldId);

        if (!Strings.isBlank(query)) {
            String lowerQuery = query.toLowerCase();
            all = all.stream()
                    .filter(f -> f.getName() != null && f.getName().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        }

        int totalCount = all.size();

        List<Map<String, Object>> dtos = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "flags", dtos,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Logic State by ID")
    public ResponseEntity<?> get(
            @PathVariable String worldId,
            @PathVariable String id) {

        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLogicStateDef> opt = stateService.findById(id);
        if (opt.isEmpty()) return notFound("state definition not found");

        WLogicStateDef flag = opt.get();
        if (!flag.getWorldId().equals(worldId)) return notFound("state definition not found");

        return ResponseEntity.ok(toDto(flag));
    }

    @PostMapping
    @Operation(summary = "Create new Logic State definition")
    public ResponseEntity<?> create(
            @PathVariable String worldId,
            @RequestBody Map<String, Object> request) {

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId));

        String name = (String) request.get("name");
        if (Strings.isBlank(name)) return bad("name required");

        String lookupWorldId = wid.toBaseWorldId().getId();

        if (stateService.findByWorldIdAndName(lookupWorldId, name).isPresent()) {
            return conflict("state name already exists");
        }

        WLogicStateDef flag = WLogicStateDef.builder()
                .worldId(lookupWorldId)
                .name(name)
                .defaultValue(request.get("defaultValue"))
                .type((String) request.get("type"))
                .description((String) request.get("description"))
                .autoCreated(false)
                .createdAt(Instant.now())
                .build();

        WLogicStateDef saved = stateService.save(flag);
        log.info("Created logic state: id={}, name={}, worldId={}", saved.getId(), saved.getName(), lookupWorldId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Logic State definition")
    public ResponseEntity<?> update(
            @PathVariable String worldId,
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {

        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLogicStateDef> opt = stateService.findById(id);
        if (opt.isEmpty()) return notFound("state definition not found");

        WLogicStateDef flag = opt.get();
        if (!flag.getWorldId().equals(worldId)) return notFound("state definition not found");

        boolean changed = false;
        if (request.containsKey("defaultValue")) {
            flag.setDefaultValue(request.get("defaultValue"));
            changed = true;
        }
        if (request.containsKey("type")) {
            flag.setType((String) request.get("type"));
            changed = true;
        }
        if (request.containsKey("description")) {
            flag.setDescription((String) request.get("description"));
            changed = true;
        }

        if (!changed) return bad("at least one field required for update");

        WLogicStateDef saved = stateService.save(flag);
        log.info("Updated logic state: id={}, name={}", id, saved.getName());
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Logic State definition")
    public ResponseEntity<?> delete(
            @PathVariable String worldId,
            @PathVariable String id) {

        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLogicStateDef> opt = stateService.findById(id);
        if (opt.isEmpty()) return notFound("state definition not found");

        WLogicStateDef flag = opt.get();
        if (!flag.getWorldId().equals(worldId)) return notFound("state definition not found");

        stateService.delete(flag);
        log.info("Deleted logic state: id={}, name={}", id, flag.getName());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDto(WLogicStateDef flag) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", flag.getId());
        dto.put("worldId", flag.getWorldId());
        dto.put("name", flag.getName());
        dto.put("defaultValue", flag.getDefaultValue());
        dto.put("type", flag.getType());
        dto.put("description", flag.getDescription());
        dto.put("autoCreated", flag.isAutoCreated());
        dto.put("createdAt", flag.getCreatedAt());
        return dto;
    }
}
