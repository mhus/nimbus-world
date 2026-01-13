package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for EntityModel templates (read-only).
 * Returns only publicData from entities.
 */
@RestController
@RequestMapping("/player/world/entitymodel")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "EntityModels", description = "EntityModel templates for 3D models and animations")
public class EntityModelController {

    private final WEntityModelService service;
    private final AccessValidator accessUtil;

    @GetMapping("/{modelId}")
    @Operation(summary = "Get EntityModel by ID", description = "Returns EntityModel template for a specific model ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "EntityModel found"),
            @ApiResponse(responseCode = "400", description = "Invalid worldId"),
            @ApiResponse(responseCode = "404", description = "EntityModel not found")
    })
    public ResponseEntity<?> getEntityModel(
            HttpServletRequest request,
            @PathVariable String modelId) {

        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        return service.findByModelId(worldId, modelId)
                        .map(WEntityModel::getPublicData)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build());
    }

//    @Deprecated
//    @GetMapping
//    @Operation(summary = "Get all EntityModels", description = "Returns all enabled EntityModel templates")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "List of EntityModels"),
//            @ApiResponse(responseCode = "400", description = "Invalid worldId")
//    })
//    public ResponseEntity<?> getAllEntityModels(
//            @PathVariable String worldId) {
//
//        return WorldId.of(worldId)
//                .<ResponseEntity<?>>map(wid -> {
//                    List<EntityModel> entityModels = service.findAllEnabled(wid).stream()
//                            .map(WEntityModel::getPublicData)
//                            .toList();
//
//                    return ResponseEntity.ok(Map.of(
//                            "entityModels", entityModels,
//                            "count", entityModels.size()
//                    ));
//                })
//                .orElseGet(() -> ResponseEntity.badRequest().build());
//    }
}
