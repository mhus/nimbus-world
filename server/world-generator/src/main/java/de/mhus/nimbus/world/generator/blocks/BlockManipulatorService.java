package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionPosition;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing and executing block manipulators and generators.
 * Maintains a registry of all available manipulators and provides execution capabilities
 * with default parameter management.
 */
@Service
@Slf4j
public class BlockManipulatorService {

    private final List<BlockManipulator> manipulators;
    private final List<de.mhus.nimbus.world.generator.blocks.painter.BlockPainterProvider> painterProviders;
    private final ObjectMapper objectMapper;
    private final WSessionService wSessionService;
    private final WEditCacheService editCacheService;
    private final WWorldService worldService;
    private Map<String, BlockManipulator> manipulatorMap;
    private Map<String, de.mhus.nimbus.world.generator.blocks.painter.BlockPainterProvider> painterProviderMap;
    private final Map<String, Object> defaultParameters = new HashMap<>();

    /**
     * Constructor with lazy-loaded list of manipulators and painter providers.
     * Spring will inject all beans implementing BlockManipulator and BlockPainterProvider interfaces.
     *
     * @param manipulators list of all registered manipulators
     * @param painterProviders list of all registered painter providers
     * @param objectMapper Jackson ObjectMapper for JSON operations
     * @param wSessionService service for accessing player position and session data
     * @param editCacheService service for block editing operations
     * @param worldService service for loading world data
     */
    @Autowired
    public BlockManipulatorService(List<BlockManipulator> manipulators,
                                  List<de.mhus.nimbus.world.generator.blocks.painter.BlockPainterProvider> painterProviders,
                                  ObjectMapper objectMapper,
                                  WSessionService wSessionService,
                                  WEditCacheService editCacheService,
                                  WWorldService worldService) {
        this.manipulators = manipulators;
        this.painterProviders = painterProviders;
        this.objectMapper = objectMapper;
        this.wSessionService = wSessionService;
        this.editCacheService = editCacheService;
        this.worldService = worldService;
        log.info("BlockManipulatorService initialized with {} manipulators and {} painter providers (lazy-loaded)",
                manipulators != null ? manipulators.size() : 0,
                painterProviders != null ? painterProviders.size() : 0);
    }

    /**
     * Initialize the manipulator map if not already done.
     * This is called lazily on first access.
     */
    private synchronized void initializeManipulatorsIfNeeded() {
        if (manipulatorMap == null) {
            manipulatorMap = new HashMap<>();
            if (manipulators != null) {
                for (BlockManipulator manipulator : manipulators) {
                    String name = manipulator.getName();
                    if (manipulatorMap.containsKey(name)) {
                        log.warn("Duplicate manipulator name '{}' detected. Previous: {}, New: {}",
                                name,
                                manipulatorMap.get(name).getClass().getSimpleName(),
                                manipulator.getClass().getSimpleName());
                    }
                    manipulatorMap.put(name, manipulator);
                    log.debug("Registered manipulator: {} - {}", name, manipulator.getTitle());
                }
            }
            log.info("Initialized {} block manipulators", manipulatorMap.size());
        }
    }

    /**
     * Initialize the painter provider map if not already done.
     * This is called lazily on first access.
     */
    private synchronized void initializePainterProvidersIfNeeded() {
        if (painterProviderMap == null) {
            painterProviderMap = new HashMap<>();
            if (painterProviders != null) {
                for (de.mhus.nimbus.world.generator.blocks.painter.BlockPainterProvider provider : painterProviders) {
                    String name = provider.getName();
                    if (painterProviderMap.containsKey(name)) {
                        log.warn("Duplicate painter provider name '{}' detected. Previous: {}, New: {}",
                                name,
                                painterProviderMap.get(name).getClass().getSimpleName(),
                                provider.getClass().getSimpleName());
                    }
                    painterProviderMap.put(name, provider);
                    log.debug("Registered painter provider: {} - {}", name, provider.getTitle());
                }
            }
            log.info("Initialized {} block painter providers", painterProviderMap.size());
        }
    }

    /**
     * Get all available manipulators.
     *
     * @return list of all registered manipulators
     */
    public List<BlockManipulator> getAvailableManipulators() {
        initializeManipulatorsIfNeeded();
        return new ArrayList<>(manipulatorMap.values());
    }

    /**
     * Get a manipulator by name.
     *
     * @param name technical name of the manipulator
     * @return manipulator, or null if not found
     */
    public BlockManipulator getManipulator(String name) {
        initializeManipulatorsIfNeeded();
        return manipulatorMap.get(name);
    }

    /**
     * Check if a manipulator exists.
     *
     * @param name technical name of the manipulator
     * @return true if manipulator exists
     */
    public boolean hasManipulator(String name) {
        initializeManipulatorsIfNeeded();
        return manipulatorMap.containsKey(name);
    }

    /**
     * Get all manipulator names.
     *
     * @return list of all registered manipulator names
     */
    public List<String> getManipulatorNames() {
        initializeManipulatorsIfNeeded();
        return new ArrayList<>(manipulatorMap.keySet());
    }

    /**
     * Get manipulator information (name, title, description).
     *
     * @return list of maps with manipulator information
     */
    public List<Map<String, String>> getManipulatorInfo() {
        initializeManipulatorsIfNeeded();
        return manipulatorMap.values().stream()
                .map(m -> {
                    Map<String, String> info = new HashMap<>();
                    info.put("name", m.getName());
                    info.put("title", m.getTitle());
                    info.put("description", m.getDescription());
                    return info;
                })
                .collect(Collectors.toList());
    }

    /**
     * Execute a manipulator by name.
     *
     * @param manipulatorName name of the manipulator to execute
     * @param context execution context
     * @return manipulation result
     * @throws BlockManipulatorException if manipulator not found or execution fails
     */
    public ManipulatorResult execute(String manipulatorName, ManipulatorContext context)
            throws BlockManipulatorException {

        BlockManipulator manipulator = getManipulator(manipulatorName);
        if (manipulator == null) {
            throw new BlockManipulatorException("Manipulator not found: " + manipulatorName);
        }

        // Set service reference if not already set
        if (context.getService() == null) {
            context.setService(this);
        }

        // Apply default parameters (context parameters override defaults)
        applyDefaultParameters(context);

        // Apply transformations (position, marker, forward, etc.)
        applyTransformations(context);

        log.info("Executing manipulator '{}' - {}", manipulatorName, manipulator.getTitle());
        log.debug("Context: originalParams={}, params={}",
                context.getOriginalParams(), context.getParams());

        try {
            ManipulatorResult result = manipulator.execute(context);

            log.info("Manipulator '{}' completed: success={}, message={}",
                    manipulatorName, result.isSuccess(), result.getMessage());

            return result;
        } catch (Exception e) {
            log.error("Manipulator '{}' execution failed", manipulatorName, e);

            ManipulatorResult errorResult = ManipulatorResult.error(
                    "Execution failed: " + e.getMessage()
            );

            throw new BlockManipulatorException("Execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a manipulator directly.
     *
     * @param manipulator the manipulator to execute
     * @param context execution context
     * @return manipulation result
     * @throws BlockManipulatorException if execution fails
     */
    public ManipulatorResult execute(BlockManipulator manipulator, ManipulatorContext context)
            throws BlockManipulatorException {

        if (manipulator == null) {
            throw new BlockManipulatorException("Manipulator is null");
        }

        return execute(manipulator.getName(), context);
    }

    /**
     * Set a default parameter value.
     * Default parameters are applied to all contexts if not explicitly overridden.
     *
     * @param key parameter key
     * @param value parameter value
     */
    public void setDefaultParameter(String key, Object value) {
        defaultParameters.put(key, value);
        log.debug("Set default parameter: {} = {}", key, value);
    }

    /**
     * Get a default parameter value.
     *
     * @param key parameter key
     * @return parameter value, or null if not set
     */
    public Object getDefaultParameter(String key) {
        return defaultParameters.get(key);
    }

    /**
     * Get all default parameters.
     *
     * @return copy of default parameters map
     */
    public Map<String, Object> getDefaultParameters() {
        return new HashMap<>(defaultParameters);
    }

    /**
     * Remove a default parameter.
     *
     * @param key parameter key
     */
    public void removeDefaultParameter(String key) {
        defaultParameters.remove(key);
        log.debug("Removed default parameter: {}", key);
    }

    /**
     * Clear all default parameters.
     */
    public void clearDefaultParameters() {
        defaultParameters.clear();
        log.debug("Cleared all default parameters");
    }

    /**
     * Apply default parameters to context.
     * Context parameters take precedence over defaults.
     *
     * @param context the context to apply defaults to
     */
    private void applyDefaultParameters(ManipulatorContext context) {
        if (context.getParams() == null) {
            context.setParams(objectMapper.createObjectNode());
        }

        ObjectNode params = context.getParams();

        // Add defaults that are not already present in context
        for (Map.Entry<String, Object> entry : defaultParameters.entrySet()) {
            if (!params.has(entry.getKey())) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    params.put(entry.getKey(), (String) value);
                } else if (value instanceof Integer) {
                    params.put(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    params.put(entry.getKey(), (Long) value);
                } else if (value instanceof Double) {
                    params.put(entry.getKey(), (Double) value);
                } else if (value instanceof Boolean) {
                    params.put(entry.getKey(), (Boolean) value);
                } else {
                    params.putPOJO(entry.getKey(), value);
                }
            }
        }
    }

    /**
     * Apply transformations based on "transform" parameter.
     * Transformations are applied in order: position, marker, forward.
     *
     * Supported transformations:
     * - "position": Add player's current position to params
     * - "marker": Add marker position from WSession entryPoint
     * - "forward": Determine facing direction and swap width/depth for E/W
     *
     * @param context the context to apply transformations to
     */
    private void applyTransformations(ManipulatorContext context) throws BlockManipulatorException {
        if (context.getParams() == null) {
            return;
        }

        ObjectNode params = context.getParams();

        // Check if transform parameter exists
        if (!params.has("transform")) {
            return;
        }

        String transformStr = params.get("transform").asText();
        if (transformStr == null || transformStr.isBlank()) {
            return;
        }

        // Parse comma-separated transformations
        String[] transformations = transformStr.split(",");
        for (String transformation : transformations) {
            transformation = transformation.trim().toLowerCase();

            switch (transformation) {
                case "position":
                    applyPositionTransform(context);
                    break;
                case "marker":
                    applyMarkerTransform(context);
                    break;
                case "forward":
                    applyForwardTransform(context);
                    break;
                default:
                    log.warn("Unknown transformation: {}", transformation);
            }
        }
    }

    /**
     * Apply "position" transformation: Add player's current position.
     */
    private void applyPositionTransform(ManipulatorContext context) throws BlockManipulatorException {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Cannot apply position transformation: sessionId is null");
            return;
        }

        Optional<WSessionPosition> posOpt = wSessionService.getPosition(sessionId);
        if (posOpt.isEmpty()) {
            log.warn("Cannot apply position transformation: player position not found for session {}", sessionId);
            return;
        }

        WSessionPosition pos = posOpt.get();
        if (!pos.hasPosition()) {
            log.warn("Cannot apply position transformation: player position incomplete");
            return;
        }

        // Add position to params
        ObjectNode params = context.getParams();
        ObjectNode positionNode = objectMapper.createObjectNode();
        positionNode.put("x", pos.getX().intValue());
        positionNode.put("y", pos.getY().intValue());
        positionNode.put("z", pos.getZ().intValue());
        params.set("position", positionNode);

        log.debug("Applied position transformation: ({}, {}, {})",
                pos.getX().intValue(), pos.getY().intValue(), pos.getZ().intValue());
    }

    /**
     * Apply "marker" transformation: Add marker position from WSession entryPoint.
     */
    private void applyMarkerTransform(ManipulatorContext context) throws BlockManipulatorException {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Cannot apply marker transformation: sessionId is null");
            return;
        }

        Optional<WSession> wSessionOpt = wSessionService.get(sessionId);
        if (wSessionOpt.isEmpty()) {
            log.warn("Cannot apply marker transformation: WSession not found for session {}", sessionId);
            return;
        }

        WSession wSession = wSessionOpt.get();
        String entryPoint = wSession.getEntryPoint();
        if (entryPoint == null || entryPoint.isBlank()) {
            log.warn("Cannot apply marker transformation: entryPoint not set");
            return;
        }

        // Parse entryPoint - format could be "last", "grid:q,r", or "world:x,y,z"
        // For now, we assume it's in format "x,y,z" or "world:x,y,z"
        String[] parts;
        if (entryPoint.contains(":")) {
            parts = entryPoint.split(":", 2)[1].split(",");
        } else {
            parts = entryPoint.split(",");
        }

        if (parts.length >= 3) {
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());

                ObjectNode params = context.getParams();
                ObjectNode positionNode = objectMapper.createObjectNode();
                positionNode.put("x", x);
                positionNode.put("y", y);
                positionNode.put("z", z);
                params.set("position", positionNode);

                log.debug("Applied marker transformation: ({}, {}, {})", x, y, z);
            } catch (NumberFormatException e) {
                log.warn("Cannot parse marker coordinates from entryPoint: {}", entryPoint);
            }
        } else {
            log.warn("Invalid entryPoint format for marker: {}", entryPoint);
        }
    }

    /**
     * Apply "forward" transformation: Determine facing direction and adjust parameters.
     * - Determines facing direction (N, E, S, W) from player yaw
     * - For E/W facing: swaps width and depth parameters
     * - Adds "direction" parameter
     */
    private void applyForwardTransform(ManipulatorContext context) throws BlockManipulatorException {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Cannot apply forward transformation: sessionId is null");
            return;
        }

        Optional<WSessionPosition> posOpt = wSessionService.getPosition(sessionId);
        if (posOpt.isEmpty()) {
            log.warn("Cannot apply forward transformation: player position not found for session {}", sessionId);
            return;
        }

        WSessionPosition pos = posOpt.get();
        if (pos.getYaw() == null) {
            log.warn("Cannot apply forward transformation: player yaw not available");
            return;
        }

        // Determine facing direction from yaw
        // Yaw: 0-360 degrees
        // N: 315-45 (-Z), E: 45-135 (+X), S: 135-225 (+Z), W: 225-315 (-X)
        double yaw = pos.getYaw();
        String direction;
        boolean swapWidthDepth = false;

        if ((yaw >= 315 && yaw <= 360) || (yaw >= 0 && yaw < 45)) {
            direction = "N";
        } else if (yaw >= 45 && yaw < 135) {
            direction = "E";
            swapWidthDepth = true;
        } else if (yaw >= 135 && yaw < 225) {
            direction = "S";
        } else {
            direction = "W";
            swapWidthDepth = true;
        }

        ObjectNode params = context.getParams();
        params.put("direction", direction);

        // Swap width and depth for E/W directions
        if (swapWidthDepth) {
            if (params.has("width") && params.has("depth")) {
                int width = params.get("width").asInt();
                int depth = params.get("depth").asInt();
                params.put("width", depth);
                params.put("depth", width);
                log.debug("Applied forward transformation: direction={}, swapped width={} <-> depth={}",
                        direction, depth, width);
            } else {
                log.debug("Applied forward transformation: direction={} (no width/depth to swap)", direction);
            }
        } else {
            log.debug("Applied forward transformation: direction={}", direction);
        }
    }

    /**
     * Gets a WWorld by worldId.
     *
     * @param worldId the world ID
     * @return WWorld object
     * @throws BlockManipulatorException if world not found
     */
    public WWorld getWorld(String worldId) throws BlockManipulatorException {
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            throw new BlockManipulatorException("World not found: " + worldId);
        }
        return worldOpt.get();
    }

    /**
     * Create an EditCachePainter with context from ManipulatorContext.
     * This painter is configured with the current layer values and can be used
     * to paint blocks into WEditCache.
     * Automatically initializes a ModelSelector in the context if not present.
     *
     * @param context manipulator context with worldId, layerDataId, modelName, groupId
     * @param blockDef block definition to paint
     * @return configured EditCachePainter
     * @throws BlockManipulatorException if world not found or configuration invalid
     */
    public EditCachePainter createBlockPainter(ManipulatorContext context, BlockDef blockDef)
            throws BlockManipulatorException {

        String worldId = context.getWorldId();
        String layerDataId = context.getLayerDataId();
        String modelName = context.getModelName();
        int groupId = context.getGroupId();

        if (worldId == null || worldId.isBlank()) {
            throw new BlockManipulatorException("WorldId required for creating BlockPainter");
        }

        if (layerDataId == null || layerDataId.isBlank()) {
            throw new BlockManipulatorException("LayerDataId required for creating BlockPainter");
        }

        if (blockDef == null) {
            throw new BlockManipulatorException("BlockDef required for creating BlockPainter");
        }

        // Initialize ModelSelector in context if not present
        if (context.getModelSelector() == null) {
            // Build autoSelectName in format: layerDataId:layerName
            // Frontend expects: "#color,layerDataId:layerName"
            String layerName = context.getLayerName();
            String autoSelectName = layerName != null && !layerName.isBlank()
                    ? layerDataId + ":" + layerName
                    : layerDataId;

            context.setModelSelector(ModelSelector.builder()
                    .defaultColor("#00ff00")
                    .autoSelectName(autoSelectName)
                    .build());
            log.debug("Initialized ModelSelector in context with autoSelectName: {}", autoSelectName);
        }

        // Load world
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            throw new BlockManipulatorException("World not found: " + worldId);
        }

        WWorld world = worldOpt.get();

        // Create painter
        EditCachePainter painter = new EditCachePainter(editCacheService);
        painter.setContext(world, layerDataId, modelName, groupId, blockDef);
        painter.setManipulatorContext(context);

        // Apply painter type if specified via "painter" parameter
        String painterType = context.getParameter("painter");
        if (painterType != null && !painterType.isBlank()) {
            initializePainterProvidersIfNeeded();
            de.mhus.nimbus.world.generator.blocks.painter.BlockPainterProvider provider =
                    painterProviderMap.get(painterType.toLowerCase());

            if (provider != null) {
                EditCachePainter.BlockPainter customPainter = provider.createPainter(context);
                painter.setPainter(customPainter);
                log.debug("Applied custom painter: {} - {}", painterType, provider.getTitle());
            } else {
                log.warn("Unknown painter type '{}', using default. Available painters: {}",
                        painterType, painterProviderMap.keySet());
            }
        }

        log.debug("Created BlockPainter: world={}, layerDataId={}, modelName={}, groupId={}, blockType={}, painter={}",
                worldId, layerDataId, modelName, groupId, blockDef.getBlockTypeId(),
                painterType != null ? painterType : "default");

        return painter;
    }

    /**
     * Get the ObjectMapper instance.
     *
     * @return ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
