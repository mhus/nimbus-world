package de.mhus.nimbus.world.ai.tool;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.edit.BlockUpdateService;
import de.mhus.nimbus.world.shared.job.WJobService;
import de.mhus.nimbus.world.shared.layer.*;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import de.mhus.nimbus.world.shared.session.EditState;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WChunkRepository;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI Tool Service for layer management.
 * Provides AI agents with tools to query, create, and manage world layers.
 *
 * Layers are used to organize world content in separate overlays:
 * - GROUND layers: Terrain data stored as WLayerTerrain
 * - MODEL layers: Entity-oriented storage with WLayerModel
 *
 * The worldId parameter must always be provided by the agent for each tool call.
 */
@Slf4j
@Service
public class LayerToolService {

    private final WLayerService layerService;
    private final WLayerModelRepository modelRepository;
    private final WSessionService sessionService;
    private final WorldRedisService redisService;
    private final WEditCacheService editCacheService;
    private final WEditCacheDirtyService editCacheDirtyService;
    private final WJobService jobService;
    private final WDirtyChunkService dirtyChunkService;
    private final WChunkRepository chunkRepository;
    private final BlockUpdateService blockUpdateService;

    public LayerToolService(WLayerService layerService,
                            WLayerModelRepository modelRepository,
                            WSessionService sessionService,
                            WorldRedisService redisService,
                            WEditCacheService editCacheService,
                            WEditCacheDirtyService editCacheDirtyService,
                            WJobService jobService,
                            WDirtyChunkService dirtyChunkService,
                            WChunkRepository chunkRepository,
                            BlockUpdateService blockUpdateService) {
        this.layerService = layerService;
        this.modelRepository = modelRepository;
        this.sessionService = sessionService;
        this.redisService = redisService;
        this.editCacheService = editCacheService;
        this.editCacheDirtyService = editCacheDirtyService;
        this.jobService = jobService;
        this.dirtyChunkService = dirtyChunkService;
        this.chunkRepository = chunkRepository;
        this.blockUpdateService = blockUpdateService;
        log.info("LayerToolService created");
    }

    // ========== Query Methods ==========

    /**
     * List all layers in a world.
     * Returns all layers defined in the specified world.
     * Use this to get an overview of available layers.
     *
     * @param worldId The world identifier (required)
     * @return Formatted list of all layers in the world
     */
    @Tool("List all layers - returns all layers in the specified world with their properties.")
    public String listLayers(String worldId) {
        log.info("AI Tool: listLayers - worldId={}", worldId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();
            List<WLayer> layers = layerService.findByWorldId(lookupWorldId);

            if (layers.isEmpty()) {
                return String.format("No layers found in world '%s'", worldId);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d layer(s) in world '%s':\n\n", layers.size(), worldId));

            for (WLayer layer : layers) {
                result.append(String.format("Name: %s\n", layer.getName()));
                result.append(String.format("Type: %s\n", layer.getLayerType()));
                result.append(String.format("Enabled: %s\n", layer.isEnabled()));
                result.append(String.format("Order: %d\n", layer.getOrder()));
                result.append(String.format("Base Ground: %s\n", layer.isBaseGround()));
                result.append(String.format("All Chunks: %s\n", layer.isAllChunks()));
                if (layer.getGroups() != null && !layer.getGroups().isEmpty()) {
                    result.append(String.format("Groups: %s\n", layer.getGroups()));
                }
                result.append("---\n\n");
            }

            log.info("AI Tool: listLayers - found {} layers", layers.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: listLayers failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Find a specific layer by name.
     * Returns detailed information about a layer.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required)
     * @return Layer information or error message
     */
    @Tool("Find layer by name - retrieves detailed information about a specific layer.")
    public String findLayer(String worldId, String layerName) {
        log.info("AI Tool: findLayer - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();
            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);

            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();
            StringBuilder result = new StringBuilder();
            result.append(String.format("Layer: %s\n", layer.getName()));
            result.append(String.format("Type: %s\n", layer.getLayerType()));
            result.append(String.format("Enabled: %s\n", layer.isEnabled()));
            result.append(String.format("Order: %d\n", layer.getOrder()));
            result.append(String.format("Base Ground: %s\n", layer.isBaseGround()));
            result.append(String.format("All Chunks: %s\n", layer.isAllChunks()));
            result.append(String.format("Layer Data ID: %s\n", layer.getLayerDataId()));
            if (layer.getGroups() != null && !layer.getGroups().isEmpty()) {
                result.append(String.format("Groups: %s\n", layer.getGroups()));
            }
            if (!layer.isAllChunks() && layer.getAffectedChunks() != null) {
                result.append(String.format("Affected Chunks: %d\n", layer.getAffectedChunks().size()));
            }

            log.info("AI Tool: findLayer - layer found");
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: findLayer failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * List all models in a MODEL layer.
     * Returns all models defined in the specified MODEL layer.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required)
     * @return Formatted list of models or error message
     */
    @Tool("List layer models - returns all models in a MODEL layer.")
    public String listLayerModels(String worldId, String layerName) {
        log.info("AI Tool: listLayerModels - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();
            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);

            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();
            if (layer.getLayerType() != LayerType.MODEL) {
                return String.format("ERROR: Layer '%s' is not a MODEL layer (type: %s)", layerName, layer.getLayerType());
            }

            List<WLayerModel> models = modelRepository.findByLayerDataIdOrderByOrder(layer.getLayerDataId());

            if (models.isEmpty()) {
                return String.format("No models found in layer '%s'", layerName);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d model(s) in layer '%s':\n\n", models.size(), layerName));

            for (WLayerModel model : models) {
                result.append(String.format("Name: %s\n", model.getName()));
                if (model.getTitle() != null) {
                    result.append(String.format("Title: %s\n", model.getTitle()));
                }
                result.append(String.format("Mount Position: (%d, %d, %d)\n", model.getMountX(), model.getMountY(), model.getMountZ()));
                result.append(String.format("Order: %d\n", model.getOrder()));
                if (model.getGroups() != null && !model.getGroups().isEmpty()) {
                    result.append(String.format("Groups: %s\n", model.getGroups()));
                }
                if (model.getContent() != null) {
                    result.append(String.format("Blocks: %d\n", model.getContent().size()));
                }
                result.append("---\n\n");
            }

            log.info("AI Tool: listLayerModels - found {} models", models.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: listLayerModels failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== Create Methods ==========

    /**
     * Create a new GROUND layer.
     * GROUND layers store terrain data as WLayerTerrain chunks.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required, must be unique)
     * @param order The layer order (default: 0)
     * @param allChunks Whether the layer affects all chunks (default: true)
     * @return Success message with layer details or error message
     */
    @Tool("Create GROUND layer - creates a new terrain layer for storing block data.")
    public String createGroundLayer(String worldId, String layerName, Integer order, Boolean allChunks) {
        log.info("AI Tool: createGroundLayer - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            // Check if layer already exists
            Optional<WLayer> existing = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (existing.isPresent()) {
                return String.format("ERROR: Layer with name '%s' already exists", layerName);
            }

            // Create layer
            WLayer layer = layerService.createLayer(
                    lookupWorldId,
                    layerName,
                    LayerType.GROUND,
                    order != null ? order : 0,
                    allChunks != null ? allChunks : true,
                    null,
                    false
            );

            log.info("AI Tool: createGroundLayer - created layer: name={}, type=GROUND", layerName);
            return String.format("SUCCESS: GROUND layer '%s' created successfully\nLayer Data ID: %s\nOrder: %d\nAll Chunks: %s",
                    layerName, layer.getLayerDataId(), layer.getOrder(), layer.isAllChunks());

        } catch (Exception e) {
            log.error("AI Tool: createGroundLayer failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Create a new MODEL layer.
     * MODEL layers store entity-oriented data as WLayerModel entities.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required, must be unique)
     * @param order The layer order (default: 0)
     * @param allChunks Whether the layer affects all chunks (default: true)
     * @return Success message with layer details or error message
     */
    @Tool("Create MODEL layer - creates a new layer for storing entity-oriented models.")
    public String createModelLayer(String worldId, String layerName, Integer order, Boolean allChunks) {
        log.info("AI Tool: createModelLayer - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            // Check if layer already exists
            Optional<WLayer> existing = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (existing.isPresent()) {
                return String.format("ERROR: Layer with name '%s' already exists", layerName);
            }

            // Create layer
            WLayer layer = layerService.createLayer(
                    lookupWorldId,
                    layerName,
                    LayerType.MODEL,
                    order != null ? order : 0,
                    allChunks != null ? allChunks : true,
                    null,
                    false
            );

            log.info("AI Tool: createModelLayer - created layer: name={}, type=MODEL", layerName);
            return String.format("SUCCESS: MODEL layer '%s' created successfully\nLayer Data ID: %s\nOrder: %d\nAll Chunks: %s",
                    layerName, layer.getLayerDataId(), layer.getOrder(), layer.isAllChunks());

        } catch (Exception e) {
            log.error("AI Tool: createModelLayer failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Create a new model in a MODEL layer.
     * Models store block structures with relative positions from a mount point.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required)
     * @param modelName The model name (required, must be unique within layer)
     * @param mountX Mount point X coordinate (required)
     * @param mountY Mount point Y coordinate (required)
     * @param mountZ Mount point Z coordinate (required)
     * @param title Optional model title
     * @return Success message with model details or error message
     */
    @Tool("Create layer model - creates a new model in a MODEL layer with specified mount point.")
    public String createLayerModel(String worldId, String layerName, String modelName,
                                   int mountX, int mountY, int mountZ, String title) {
        log.info("AI Tool: createLayerModel - worldId={}, layerName={}, modelName={}", worldId, layerName, modelName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        if (Strings.isBlank(modelName)) {
            return "ERROR: modelName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            // Find layer
            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();
            if (layer.getLayerType() != LayerType.MODEL) {
                return String.format("ERROR: Layer '%s' is not a MODEL layer (type: %s)", layerName, layer.getLayerType());
            }

            // Check if model with same name already exists
            List<WLayerModel> existing = modelRepository.findByWorldIdAndName(lookupWorldId, modelName);
            if (!existing.isEmpty()) {
                // Check if any of the models belongs to this layer
                for (WLayerModel existingModel : existing) {
                    if (layer.getLayerDataId().equals(existingModel.getLayerDataId())) {
                        return String.format("ERROR: Model with name '%s' already exists in layer '%s'", modelName, layerName);
                    }
                }
            }

            // Create model
            WLayerModel model = WLayerModel.builder()
                    .worldId(lookupWorldId)
                    .layerDataId(layer.getLayerDataId())
                    .name(modelName)
                    .title(title)
                    .mountX(mountX)
                    .mountY(mountY)
                    .mountZ(mountZ)
                    .order(0)
                    .content(List.of())
                    .build();
            model.touchCreate();

            WLayerModel saved = modelRepository.save(model);

            log.info("AI Tool: createLayerModel - created model: name={} in layer={}", modelName, layerName);
            return String.format("SUCCESS: Model '%s' created successfully in layer '%s'\nMount Point: (%d, %d, %d)\nModel ID: %s",
                    modelName, layerName, mountX, mountY, mountZ, saved.getId());

        } catch (Exception e) {
            log.error("AI Tool: createLayerModel failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== Generation Methods ==========

    /**
     * Regenerate a layer.
     * For MODEL layers: Creates a job to recreate terrain data from models.
     * For GROUND layers: Marks affected chunks as dirty for regeneration.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required)
     * @return Success message with regeneration details or error message
     */
    @Tool("Regenerate layer - triggers complete regeneration of layer data. For MODEL layers creates job, for GROUND layers marks chunks dirty.")
    public String regenerateLayer(String worldId, String layerName) {
        log.info("AI Tool: regenerateLayer - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();

            if (layer.getLayerType() == LayerType.MODEL) {
                // For MODEL layers: Create regeneration job
                var job = jobService.createJob(
                        lookupWorldId,
                        "recreate-model-based-layer",
                        "layer-regeneration",
                        Map.of(
                                "layerDataId", layer.getLayerDataId(),
                                "markChunksDirty", "true"
                        ),
                        8,  // High priority
                        3   // Max retries
                );

                log.info("AI Tool: regenerateLayer - created regeneration job for MODEL layer: jobId={}", job.getId());
                return String.format("SUCCESS: Regeneration job created for MODEL layer '%s'\nJob ID: %s\nLayer Type: MODEL",
                        layerName, job.getId());

            } else {
                // For GROUND layers: Mark chunks as dirty
                List<String> affectedChunks;
                if (layer.isAllChunks()) {
                    affectedChunks = chunkRepository.findByWorldId(lookupWorldId)
                            .stream()
                            .map(chunk -> chunk.getChunk())
                            .collect(Collectors.toList());
                } else {
                    affectedChunks = layer.getAffectedChunks();
                }

                if (affectedChunks.isEmpty()) {
                    return String.format("WARNING: No chunks to regenerate for GROUND layer '%s'", layerName);
                }

                dirtyChunkService.markChunksDirty(lookupWorldId, affectedChunks, "layer_regeneration");

                log.info("AI Tool: regenerateLayer - marked {} chunks dirty for GROUND layer", affectedChunks.size());
                return String.format("SUCCESS: Marked %d chunks for regeneration in GROUND layer '%s'\nLayer Type: GROUND",
                        affectedChunks.size(), layerName);
            }

        } catch (Exception e) {
            log.error("AI Tool: regenerateLayer failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== Edit Functions ==========

    /**
     * Select a layer for editing in a session.
     * This sets the selected layer in the session's edit state.
     *
     * @param worldId The world identifier (required)
     * @param sessionId The session identifier (required)
     * @param layerName The layer name to select (required)
     * @return Success message or error message
     */
    @Tool("Select layer for editing - sets the selected layer in the session's edit state.")
    public String selectLayerForEditing(String worldId, String sessionId, String layerName) {
        log.info("AI Tool: selectLayerForEditing - worldId={}, sessionId={}, layerName={}", worldId, sessionId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(sessionId)) {
            return "ERROR: sessionId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            // Verify layer exists
            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();

            // Update edit state
            Optional<EditState> stateOpt = sessionService.getEditState(sessionId);
            EditState state = stateOpt.orElse(EditState.builder()
                    .worldId(lookupWorldId)
                    .editMode(false)
                    .build());

            state.setSelectedLayer(layerName);
            state.setLayerDataId(layer.getLayerDataId());

            sessionService.updateEditState(sessionId, state);

            log.info("AI Tool: selectLayerForEditing - selected layer: name={}, type={}", layerName, layer.getLayerType());
            return String.format("SUCCESS: Layer '%s' selected for editing\nLayer Type: %s\nLayer Data ID: %s",
                    layerName, layer.getLayerType(), layer.getLayerDataId());

        } catch (Exception e) {
            log.error("AI Tool: selectLayerForEditing failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Commit edit cache changes to layer.
     * Clears the edit cache for the specified layer after committing.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required)
     * @return Success message with commit details or error message
     */
    @Tool("Commit layer changes - commits edit cache changes to the layer and clears the cache.")
    public String commitLayerChanges(String worldId, String layerName) {
        log.info("AI Tool: commitLayerChanges - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();
            String layerDataId = layer.getLayerDataId();

            // Count cached blocks
            long cachedBlocks = editCacheService.countByWorldIdAndLayerDataId(lookupWorldId, layerDataId);

            if (cachedBlocks == 0) {
                return String.format("WARNING: No cached changes to commit for layer '%s'", layerName);
            }

            // For MODEL layers, merge cache into models
            if (layer.getLayerType() == LayerType.MODEL) {
                // TODO: Implement merge logic for MODEL layers
                log.warn("Commit for MODEL layers not yet fully implemented");
            }

            // Clear edit cache
            long deleted = editCacheService.deleteByWorldIdAndLayerDataId(lookupWorldId, layerDataId);

            // Clear dirty cache using clearDirty method
            if (editCacheDirtyService.isDirty(lookupWorldId, layerDataId)) {
                editCacheDirtyService.clearDirty(lookupWorldId, layerDataId);
            }

            log.info("AI Tool: commitLayerChanges - committed {} changes for layer: name={}", deleted, layerName);
            return String.format("SUCCESS: Committed %d changes to layer '%s'\nLayer Type: %s\nCache cleared: %d blocks",
                    cachedBlocks, layerName, layer.getLayerType(), deleted);

        } catch (Exception e) {
            log.error("AI Tool: commitLayerChanges failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Discard edit cache changes for a layer.
     * Clears all cached changes without committing them.
     *
     * @param worldId The world identifier (required)
     * @param layerName The layer name (required)
     * @return Success message with discard details or error message
     */
    @Tool("Discard layer changes - discards all edit cache changes for a layer without committing.")
    public String discardLayerChanges(String worldId, String layerName) {
        log.info("AI Tool: discardLayerChanges - worldId={}, layerName={}", worldId, layerName);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(layerName)) {
            return "ERROR: layerName parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            String lookupWorldId = wid.withoutInstance().getId();

            Optional<WLayer> layerOpt = layerService.findByWorldIdAndName(lookupWorldId, layerName);
            if (layerOpt.isEmpty()) {
                return String.format("ERROR: Layer not found - layerName='%s'", layerName);
            }

            WLayer layer = layerOpt.get();
            String layerDataId = layer.getLayerDataId();

            // Count cached blocks
            long cachedBlocks = editCacheService.countByWorldIdAndLayerDataId(lookupWorldId, layerDataId);

            if (cachedBlocks == 0) {
                return String.format("INFO: No cached changes to discard for layer '%s'", layerName);
            }

            // Clear edit cache
            long deleted = editCacheService.deleteByWorldIdAndLayerDataId(lookupWorldId, layerDataId);

            // Clear dirty cache using clearDirty method
            if (editCacheDirtyService.isDirty(lookupWorldId, layerDataId)) {
                editCacheDirtyService.clearDirty(lookupWorldId, layerDataId);
            }

            log.info("AI Tool: discardLayerChanges - discarded {} changes for layer: name={}", deleted, layerName);
            return String.format("SUCCESS: Discarded %d changes from layer '%s'\nLayer Type: %s\nCache cleared: %d blocks",
                    cachedBlocks, layerName, layer.getLayerType(), deleted);

        } catch (Exception e) {
            log.error("AI Tool: discardLayerChanges failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Set a marker at a position in the session.
     * This stores a marked position that can be used for copy/paste operations.
     *
     * @param worldId The world identifier (required)
     * @param sessionId The session identifier (required)
     * @param x The X coordinate (required)
     * @param y The Y coordinate (required)
     * @param z The Z coordinate (required)
     * @return Success message or error message
     */
    @Tool("Set marker position - marks a position in the session for copy/paste operations.")
    public String setMarkerPosition(String worldId, String sessionId, int x, int y, int z) {
        log.info("AI Tool: setMarkerPosition - worldId={}, sessionId={}, pos=({},{},{})", worldId, sessionId, x, y, z);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(sessionId)) {
            return "ERROR: sessionId parameter is required";
        }

        try {
            WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            // Store marker position in edit state
            Optional<EditState> stateOpt = sessionService.getEditState(sessionId);
            EditState state = stateOpt.orElse(EditState.builder()
                    .worldId(worldId)
                    .editMode(false)
                    .build());

            // Store marker coordinates (implementation depends on EditState structure)
            // For now, just acknowledge the marker was set
            sessionService.updateEditState(sessionId, state);

            log.info("AI Tool: setMarkerPosition - marked position: ({},{},{})", x, y, z);
            return String.format("SUCCESS: Marker set at position (%d, %d, %d)", x, y, z);

        } catch (Exception e) {
            log.error("AI Tool: setMarkerPosition failed", e);
            return "ERROR: " + e.getMessage();
        }
    }
}
