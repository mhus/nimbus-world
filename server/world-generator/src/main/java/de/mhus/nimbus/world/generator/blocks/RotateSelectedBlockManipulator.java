package de.mhus.nimbus.world.generator.blocks;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.RotationXY;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.util.ModelSelectorUtil;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rotate Selected Blocks Manipulator.
 * Rotates all blocks that are currently selected in the ModelSelector around the selection center.
 *
 * Usage:
 * <pre>
 * {
 *   "rotate-selected": {
 *     "x": 0,
 *     "y": 90,
 *     "z": 0
 *   }
 * }
 * </pre>
 *
 * Only 90-degree steps are allowed: 90, 180, 270, -90, -180, -270.
 * If a rotation axis is not specified, it defaults to 0 (no rotation).
 *
 * The manipulator rotates both:
 * - Block positions around the selection center
 * - Block rotation values (RotationXY)
 *
 * Returns a new ModelSelector with the updated positions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RotateSelectedBlockManipulator implements BlockManipulator {

    private final WSessionService wSessionService;
    private final WEditCacheService editCacheService;
    private final WWorldService worldService;

    @Override
    public String getName() {
        return "rotate-selected";
    }

    @Override
    public String getTitle() {
        return "Rotate Selected Blocks";
    }

    @Override
    public String getDescription() {
        return "Rotates all blocks that are currently selected in the ModelSelector around the selection center. " +
                "Only 90-degree steps allowed (90, 180, 270, -90, -180, -270). " +
                "Parameters: x (default 0), y (default 0), z (default 0). " +
                "Example: {\"rotate-selected\": {\"y\": 90}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Validate required context fields
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return ManipulatorResult.error("SessionId required for rotate-selected operation");
        }

        String worldId = context.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return ManipulatorResult.error("WorldId required for rotate-selected operation");
        }

        String layerDataId = context.getLayerDataId();
        if (layerDataId == null || layerDataId.isBlank()) {
            return ManipulatorResult.error("LayerDataId required for rotate-selected operation");
        }

        String modelName = context.getModelName();
        int groupId = context.getGroupId();

        // Load WWorld
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ManipulatorResult.error("World not found: " + worldId);
        }
        WWorld world = worldOpt.get();

        // Load WSession to get ModelSelector
        var sessionOpt = wSessionService.get(sessionId);
        if (sessionOpt.isEmpty()) {
            return ManipulatorResult.error("Session not found: " + sessionId);
        }

        List<String> modelSelectorData = sessionOpt.get().getModelSelector();
        if (modelSelectorData == null || modelSelectorData.isEmpty()) {
            return ManipulatorResult.error("No blocks selected. Please select blocks first using the ModelSelector.");
        }

        // Parse ModelSelector
        ModelSelector modelSelector = ModelSelectorUtil.fromStringList(modelSelectorData);
        if (modelSelector == null || modelSelector.getBlockCount() == 0) {
            return ManipulatorResult.error("No blocks selected. ModelSelector is empty.");
        }

        // Get rotation angles (default to 0 if not specified)
        int rotX = context.getIntParameter("x") != null ? context.getIntParameter("x") : 0;
        int rotY = context.getIntParameter("y") != null ? context.getIntParameter("y") : 0;
        int rotZ = context.getIntParameter("z") != null ? context.getIntParameter("z") : 0;

        // Validate rotation angles (only 90-degree steps)
        if (!isValidRotation(rotX) || !isValidRotation(rotY) || !isValidRotation(rotZ)) {
            return ManipulatorResult.error("Invalid rotation angles. Only 90-degree steps allowed: 90, 180, 270, -90, -180, -270");
        }

        if (rotX == 0 && rotY == 0 && rotZ == 0) {
            return ManipulatorResult.error("No rotation specified. At least one of x, y, or z must be non-zero.");
        }

        // Normalize rotations to 0-270 range
        rotX = normalizeRotation(rotX);
        rotY = normalizeRotation(rotY);
        rotZ = normalizeRotation(rotZ);

        log.info("Rotating {} selected blocks by angles (x:{}, y:{}, z:{}) in layer {}",
                modelSelector.getBlockCount(), rotX, rotY, rotZ, layerDataId);

        // Load all cached blocks for the layer
        List<WEditCache> cachedBlocks = editCacheService.findByWorldIdAndLayerDataId(worldId, layerDataId);

        // Create a map for quick lookup by coordinates (x,y,z)
        Map<String, WEditCache> blockMap = new HashMap<>();
        for (WEditCache cache : cachedBlocks) {
            LayerBlock layerBlock = cache.getBlock();
            if (layerBlock != null && layerBlock.getBlock() != null) {
                Block block = layerBlock.getBlock();
                if (block.getPosition() != null) {
                    String key = block.getPosition().getX() + "," +
                               block.getPosition().getY() + "," +
                               block.getPosition().getZ();
                    blockMap.put(key, cache);
                }
            }
        }

        log.debug("Loaded {} cached blocks for layer {}", blockMap.size(), layerDataId);

        // Calculate center of selection
        List<String> blocks = modelSelector.getBlocks();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (String blockEntry : blocks) {
            String[] parts = blockEntry.split(",");
            if (parts.length >= 3) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    int z = Integer.parseInt(parts[2].trim());

                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            }
        }

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;

        log.debug("Selection center: ({}, {}, {})", centerX, centerY, centerZ);

        // Process each selected block
        int rotatedCount = 0;
        int errorCount = 0;

        // Create new ModelSelector for the rotated blocks
        String layerName = context.getLayerName();
        String autoSelectName = layerName != null && !layerName.isBlank()
                ? layerDataId + ":" + layerName
                : layerDataId;

        ModelSelector newModelSelector = ModelSelector.builder()
                .defaultColor(modelSelector.getDefaultColor())
                .autoSelectName(autoSelectName)
                .build();

        for (String blockEntry : blocks) {
            try {
                // Parse block entry: "x,y,z,#color"
                String[] parts = blockEntry.split(",");
                if (parts.length < 3) {
                    log.warn("Invalid block entry format: {}", blockEntry);
                    errorCount++;
                    continue;
                }

                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                String color = parts.length > 3 ? parts[3].trim() : modelSelector.getDefaultColor();

                // Find the cached block
                String key = x + "," + y + "," + z;
                WEditCache cachedBlock = blockMap.get(key);

                if (cachedBlock == null) {
                    log.warn("Block not found in cache at ({},{},{}), skipping", x, y, z);
                    errorCount++;
                    continue;
                }

                // Get the original block data
                LayerBlock layerBlock = cachedBlock.getBlock();
                Block originalBlock = layerBlock.getBlock();

                // Calculate new position after rotation
                Vector3Int newPosition = rotatePosition(x, y, z, centerX, centerY, centerZ, rotX, rotY, rotZ);

                // Calculate new block rotation
                RotationXY newRotation = rotateBlockRotation(originalBlock.getRotation(), rotX, rotY);

                // Create a new block with updated position and rotation
                Block newBlock = Block.builder()
                        .position(newPosition)
                        .blockTypeId(originalBlock.getBlockTypeId())
                        .offsets(originalBlock.getOffsets())
                        .rotation(newRotation)
                        .faceVisibility(originalBlock.getFaceVisibility())
                        .status(originalBlock.getStatus())
                        .modifiers(originalBlock.getModifiers())
                        .metadata(originalBlock.getMetadata())
                        .level(originalBlock.getLevel())
                        .source(originalBlock.getSource())
                        .build();

                // Delete old block
                editCacheService.doDeleteAndSendBlock(world, layerDataId, x, y, z);

                // Set new block at new position
                editCacheService.doSetAndSendBlock(world, layerDataId, modelName, newBlock, groupId);

                // Add to new ModelSelector
                newModelSelector.addBlock(newPosition.getX(), newPosition.getY(), newPosition.getZ(), color);

                rotatedCount++;
                log.debug("Rotated block from ({},{},{}) to ({},{},{})",
                        x, y, z, newPosition.getX(), newPosition.getY(), newPosition.getZ());

            } catch (NumberFormatException e) {
                log.warn("Failed to parse block coordinates from entry: {}", blockEntry, e);
                errorCount++;
            } catch (Exception e) {
                log.error("Failed to rotate block from entry: {}", blockEntry, e);
                errorCount++;
            }
        }

        // Update ModelSelector in session with new positions
        wSessionService.updateModelSelector(sessionId, ModelSelectorUtil.toStringList(newModelSelector));
        log.debug("Updated ModelSelector for session: {}", sessionId);

        // Build result message
        String message;
        if (errorCount > 0) {
            message = String.format("Rotated %d blocks by (x:%d°, y:%d°, z:%d°), %d errors occurred",
                    rotatedCount, rotX, rotY, rotZ, errorCount);
            log.warn(message);
        } else {
            message = String.format("Successfully rotated %d blocks by (x:%d°, y:%d°, z:%d°)",
                    rotatedCount, rotX, rotY, rotZ);
            log.info(message);
        }

        return ManipulatorResult.success(message, newModelSelector);
    }

    /**
     * Validate that rotation angle is a valid 90-degree step.
     */
    private boolean isValidRotation(int angle) {
        int normalized = Math.abs(angle) % 360;
        return normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270;
    }

    /**
     * Normalize rotation angle to 0-270 range.
     */
    private int normalizeRotation(int angle) {
        int normalized = angle % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized;
    }

    /**
     * Rotate a position around a center point.
     */
    private Vector3Int rotatePosition(int x, int y, int z, double cx, double cy, double cz,
                                     int rotX, int rotY, int rotZ) {
        // Relative to center
        double rx = x - cx;
        double ry = y - cy;
        double rz = z - cz;

        // Apply Y rotation (around vertical axis) - most common for building
        if (rotY != 0) {
            double[] result = rotateY(rx, rz, rotY);
            rx = result[0];
            rz = result[1];
        }

        // Apply X rotation (pitch)
        if (rotX != 0) {
            double[] result = rotateX(ry, rz, rotX);
            ry = result[0];
            rz = result[1];
        }

        // Apply Z rotation (roll)
        if (rotZ != 0) {
            double[] result = rotateZ(rx, ry, rotZ);
            rx = result[0];
            ry = result[1];
        }

        // Back to world coordinates (rounded to nearest integer)
        int newX = (int) Math.round(cx + rx);
        int newY = (int) Math.round(cy + ry);
        int newZ = (int) Math.round(cz + rz);

        return Vector3Int.builder()
                .x(newX)
                .y(newY)
                .z(newZ)
                .build();
    }

    /**
     * Rotate around Y axis (horizontal rotation).
     */
    private double[] rotateY(double x, double z, int degrees) {
        return switch (degrees) {
            case 90 -> new double[]{-z, x};
            case 180 -> new double[]{-x, -z};
            case 270 -> new double[]{z, -x};
            default -> new double[]{x, z};
        };
    }

    /**
     * Rotate around X axis (pitch).
     */
    private double[] rotateX(double y, double z, int degrees) {
        return switch (degrees) {
            case 90 -> new double[]{-z, y};
            case 180 -> new double[]{-y, -z};
            case 270 -> new double[]{z, -y};
            default -> new double[]{y, z};
        };
    }

    /**
     * Rotate around Z axis (roll).
     */
    private double[] rotateZ(double x, double y, int degrees) {
        return switch (degrees) {
            case 90 -> new double[]{-y, x};
            case 180 -> new double[]{-x, -y};
            case 270 -> new double[]{y, -x};
            default -> new double[]{x, y};
        };
    }

    /**
     * Rotate block rotation values.
     */
    private RotationXY rotateBlockRotation(RotationXY original, int rotX, int rotY) {
        if (original == null) {
            original = RotationXY.builder().x(0).y(0).build();
        }

        double newX = original.getX();
        double newY = original.getY();

        // Add rotation angles (convert to radians for proper addition)
        if (rotX != 0) {
            newX += Math.toRadians(rotX);
        }
        if (rotY != 0) {
            newY += Math.toRadians(rotY);
        }

        // Normalize to 0-2π range
        newX = normalizeRadians(newX);
        newY = normalizeRadians(newY);

        return RotationXY.builder()
                .x(newX)
                .y(newY)
                .build();
    }

    /**
     * Normalize radians to 0-2π range.
     */
    private double normalizeRadians(double radians) {
        double twoPi = 2 * Math.PI;
        radians = radians % twoPi;
        if (radians < 0) {
            radians += twoPi;
        }
        return radians;
    }
}
