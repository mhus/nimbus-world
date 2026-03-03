package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityTools {

    private final WEntityService entityService;

    @Tool(name = "list_entities", description = "List all entities for a world. Returns entityId, modelId, name, enabled status, position, and server parameters.")
    public Map<String, Object> listEntities(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Optional search query to filter by entityId or name", required = false) String query) {
        log.debug("MCP: List entities: worldId={}, query={}", worldId, query);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        List<WEntity> entities;
        if (Strings.isBlank(query)) {
            entities = entityService.findByWorldId(wid);
        } else {
            entities = entityService.findByWorldIdAndQuery(wid, query);
        }

        var dtos = entities.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entityId", e.getEntityId());
            map.put("modelId", e.getModelId() != null ? e.getModelId() : "");
            if (e.getPublicData() != null) {
                map.put("name", e.getPublicData().getName() != null ? e.getPublicData().getName() : "");
            }
            map.put("enabled", e.isEnabled());
            if (e.getPosition() != null) {
                map.put("position", Map.of(
                        "x", e.getPosition().getX(),
                        "y", e.getPosition().getY(),
                        "z", e.getPosition().getZ()
                ));
            }
            if (e.getServer() != null && !e.getServer().isEmpty()) {
                map.put("server", e.getServer());
            }
            return map;
        }).toList();

        return Map.of(
                "worldId", worldId,
                "count", dtos.size(),
                "entities", dtos
        );
    }

    @Tool(name = "get_entity", description = "Get a single entity by its entityId. Returns full data including publicData, position, behavior, and server parameters.")
    public Map<String, Object> getEntity(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Entity ID") String entityId) {
        log.debug("MCP: Get entity: worldId={}, entityId={}", worldId, entityId);

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId)) {
            throw new McpToolException("worldId and entityId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        WEntity entity = entityService.findByWorldIdAndEntityId(wid, entityId)
                .orElseThrow(() -> new McpToolException("Entity not found: " + entityId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityId", entity.getEntityId());
        result.put("modelId", entity.getModelId());
        result.put("enabled", entity.isEnabled());
        result.put("source", entity.getSource());
        if (entity.getPublicData() != null) {
            result.put("publicData", entity.getPublicData());
        }
        if (entity.getPosition() != null) {
            result.put("position", Map.of(
                    "x", entity.getPosition().getX(),
                    "y", entity.getPosition().getY(),
                    "z", entity.getPosition().getZ()
            ));
        }
        if (entity.getMiddlePoint() != null) {
            result.put("middlePoint", Map.of(
                    "x", entity.getMiddlePoint().getX(),
                    "y", entity.getMiddlePoint().getY(),
                    "z", entity.getMiddlePoint().getZ()
            ));
        }
        result.put("radius", entity.getRadius());
        result.put("speed", entity.getSpeed());
        result.put("behaviorModel", entity.getBehaviorModel());
        if (entity.getBehaviorConfig() != null) {
            result.put("behaviorConfig", entity.getBehaviorConfig());
        }
        if (entity.getServer() != null) {
            result.put("server", entity.getServer());
        }
        return result;
    }

    @Tool(name = "create_entity", description = "Create a new entity in the world. Entities are instances of EntityModels placed in the world (e.g., NPCs, creatures).")
    public Map<String, Object> createEntity(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Unique entity identifier within the world") String entityId,
            @ToolParam(description = "Reference to an EntityModel ID (e.g. 'cow1', 'farmer1')") String modelId,
            @ToolParam(description = "Display name of the entity", required = false) String name,
            @ToolParam(description = "Movement type: 'static', 'passive', 'slow', 'dynamic'", required = false) String movementType,
            @ToolParam(description = "Controlled by: 'player', 'server', 'ai', 'client'", required = false) String controlledBy,
            @ToolParam(description = "Whether the entity is solid (collision)", required = false) Boolean solid,
            @ToolParam(description = "Whether the entity is interactive (can be clicked)", required = false) Boolean interactive,
            @ToolParam(description = "Max health points", required = false) Float healthMax,
            @ToolParam(description = "Position X coordinate", required = false) Double posX,
            @ToolParam(description = "Position Y coordinate", required = false) Double posY,
            @ToolParam(description = "Position Z coordinate", required = false) Double posZ,
            @ToolParam(description = "Movement radius around position (blocks)", required = false) Double radius,
            @ToolParam(description = "Movement speed (blocks per second)", required = false) Double speed,
            @ToolParam(description = "Behavior model identifier (e.g. 'PreyAnimalBehavior')", required = false) String behaviorModel,
            @ToolParam(description = "Server-side parameters as key-value pairs for gameplay configuration", required = false) Map<String, String> server) {
        log.debug("MCP: Create entity: worldId={}, entityId={}, modelId={}", worldId, entityId, modelId);

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId) || Strings.isBlank(modelId)) {
            throw new McpToolException("worldId, entityId, and modelId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            Entity publicData = Entity.builder()
                    .id(entityId)
                    .name(name)
                    .model(modelId)
                    .movementType(movementType != null ? movementType : "static")
                    .controlledBy(controlledBy != null ? controlledBy : "server")
                    .solid(solid)
                    .interactive(interactive)
                    .healthMax(healthMax != null ? healthMax : 100)
                    .build();

            WEntity saved = entityService.save(wid, entityId, publicData, modelId);

            // Set additional fields via update
            entityService.update(wid, entityId, entity -> {
                if (posX != null && posY != null && posZ != null) {
                    entity.setPosition(new de.mhus.nimbus.generated.types.Vector3(posX.floatValue(), posY.floatValue(), posZ.floatValue()));
                    entity.setMiddlePoint(new de.mhus.nimbus.generated.types.Vector3(posX.floatValue(), posY.floatValue(), posZ.floatValue()));
                }
                if (radius != null) entity.setRadius(radius);
                if (speed != null) entity.setSpeed(speed);
                if (Strings.isNotBlank(behaviorModel)) entity.setBehaviorModel(behaviorModel);
                if (server != null) entity.setServer(server);
            });

            return Map.of(
                    "entityId", saved.getEntityId(),
                    "worldId", worldId,
                    "modelId", modelId,
                    "status", "created"
            );
        } catch (Exception e) {
            throw new McpToolException("Failed to create entity: " + e.getMessage());
        }
    }

    @Tool(name = "update_entity", description = "Update an existing entity. Only provided fields are updated, others remain unchanged.")
    public Map<String, Object> updateEntity(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Entity ID to update") String entityId,
            @ToolParam(description = "New model ID", required = false) String modelId,
            @ToolParam(description = "New display name", required = false) String name,
            @ToolParam(description = "Movement type: 'static', 'passive', 'slow', 'dynamic'", required = false) String movementType,
            @ToolParam(description = "Controlled by: 'player', 'server', 'ai', 'client'", required = false) String controlledBy,
            @ToolParam(description = "Whether the entity is solid", required = false) Boolean solid,
            @ToolParam(description = "Whether the entity is interactive", required = false) Boolean interactive,
            @ToolParam(description = "Max health points", required = false) Float healthMax,
            @ToolParam(description = "Whether the entity is enabled", required = false) Boolean enabled,
            @ToolParam(description = "Position X coordinate", required = false) Double posX,
            @ToolParam(description = "Position Y coordinate", required = false) Double posY,
            @ToolParam(description = "Position Z coordinate", required = false) Double posZ,
            @ToolParam(description = "Movement radius around position (blocks)", required = false) Double radius,
            @ToolParam(description = "Movement speed (blocks per second)", required = false) Double speed,
            @ToolParam(description = "Behavior model identifier", required = false) String behaviorModel,
            @ToolParam(description = "Server-side parameters to merge into existing server parameters", required = false) Map<String, String> server) {
        log.debug("MCP: Update entity: worldId={}, entityId={}", worldId, entityId);

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId)) {
            throw new McpToolException("worldId and entityId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        var updated = entityService.update(wid, entityId, entity -> {
            Entity publicData = entity.getPublicData();
            if (publicData == null) {
                publicData = Entity.builder().id(entityId).build();
            }

            if (name != null) publicData.setName(name);
            if (movementType != null) publicData.setMovementType(movementType);
            if (controlledBy != null) publicData.setControlledBy(controlledBy);
            if (solid != null) publicData.setSolid(solid);
            if (interactive != null) publicData.setInteractive(interactive);
            if (healthMax != null) publicData.setHealthMax(healthMax);
            if (modelId != null) {
                publicData.setModel(modelId);
                entity.setModelId(modelId);
            }
            entity.setPublicData(publicData);

            if (enabled != null) entity.setEnabled(enabled);
            if (posX != null && posY != null && posZ != null) {
                entity.setPosition(new de.mhus.nimbus.generated.types.Vector3(posX.floatValue(), posY.floatValue(), posZ.floatValue()));
            }
            if (radius != null) entity.setRadius(radius);
            if (speed != null) entity.setSpeed(speed);
            if (behaviorModel != null) entity.setBehaviorModel(behaviorModel);
            if (server != null) {
                Map<String, String> existing = entity.getServer();
                if (existing == null) {
                    entity.setServer(new HashMap<>(server));
                } else {
                    existing.putAll(server);
                }
            }
        });

        if (updated.isEmpty()) {
            throw new McpToolException("Entity not found: " + entityId);
        }

        return Map.of(
                "entityId", entityId,
                "worldId", worldId,
                "status", "updated"
        );
    }

    @Tool(name = "delete_entity", description = "Delete an entity by its entityId.")
    public Map<String, Object> deleteEntity(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Entity ID to delete") String entityId) {
        log.debug("MCP: Delete entity: worldId={}, entityId={}", worldId, entityId);

        if (Strings.isBlank(worldId) || Strings.isBlank(entityId)) {
            throw new McpToolException("worldId and entityId are required");
        }

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        boolean deleted = entityService.delete(wid, entityId);
        if (!deleted) {
            throw new McpToolException("Entity not found: " + entityId);
        }

        return Map.of(
                "deleted", true,
                "entityId", entityId
        );
    }
}
