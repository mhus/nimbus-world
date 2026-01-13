package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.generated.types.*;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rough block painter provider - adds random offset and rotation variance to blocks.
 *
 * Creates rough, irregular surfaces by randomly varying block offsets and rotations.
 * Offsets are only applied to CUBE shape blocks (shape index 1).
 *
 * Parameters:
 * - rotationVarianceX: int (default 0) - Max rotation variance in degrees for X axis
 *   Example: 3 means random value from -1 to +3 degrees
 * - rotationVarianceY: int (default 0) - Max rotation variance in degrees for Y axis
 * - offsetVarianceMin: float (default 0.0) - Min offset variance (applies to all axes x,y,z)
 * - offsetVarianceMax: float (default 0.0) - Max offset variance (applies to all axes x,y,z)
 *
 * Example (rough stone surface):
 * <pre>
 * {
 *   "plateau": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 10,
 *     "depth": 10,
 *     "blockType": "n:s",
 *     "painter": "rough",
 *     "rotationVarianceX": 3,
 *     "rotationVarianceY": 3,
 *     "offsetVarianceMin": -0.1,
 *     "offsetVarianceMax": 0.1
 *   }
 * }
 * </pre>
 *
 * Example (smooth surface - remove variance):
 * <pre>
 * {
 *   "cube": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 10,
 *     "height": 10,
 *     "depth": 10,
 *     "blockType": "n:s",
 *     "painter": "rough",
 *     "rotationVarianceX": 0,
 *     "rotationVarianceY": 0,
 *     "offsetVarianceMin": 0.0,
 *     "offsetVarianceMax": 0.0
 *   }
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoughBlockPainterProvider implements BlockPainterProvider {

    private final WBlockTypeService blockTypeService;

    // Cache for BlockType lookups (worldId:blockId -> BlockType)
    private final Map<String, BlockType> blockTypeCache = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "rough";
    }

    @Override
    public String getTitle() {
        return "Rough Painter";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Read parameters
        Integer rotationVarianceX = context.getIntParameter("rotationVarianceX");
        if (rotationVarianceX == null) rotationVarianceX = 0;

        Integer rotationVarianceY = context.getIntParameter("rotationVarianceY");
        if (rotationVarianceY == null) rotationVarianceY = 0;

        Double offsetVarianceMin = context.getDoubleParameter("offsetVarianceMin");
        if (offsetVarianceMin == null) offsetVarianceMin = 0.0;

        Double offsetVarianceMax = context.getDoubleParameter("offsetVarianceMax");
        if (offsetVarianceMax == null) offsetVarianceMax = 0.0;

        final int finalRotationVarianceX = rotationVarianceX;
        final int finalRotationVarianceY = rotationVarianceY;
        final double finalOffsetVarianceMin = offsetVarianceMin;
        final double finalOffsetVarianceMax = offsetVarianceMax;

        final Random random = new Random();

        log.debug("Creating rough painter: rotX={}, rotY={}, offsetMin={}, offsetMax={}",
                finalRotationVarianceX, finalRotationVarianceY, finalOffsetVarianceMin, finalOffsetVarianceMax);

        // Create painter with rough logic
        return new EditCachePainter.BlockPainter() {
            @Override
            public void paint(EditCachePainter painter, int x, int y, int z) {
                // Create block
                Block block = Block.builder()
                        .position(
                                de.mhus.nimbus.generated.types.Vector3Int.builder()
                                        .x(x)
                                        .y(y)
                                        .z(z)
                                        .build()
                        ).build();

                // Fill block with BlockDef (sets blockTypeId)
                BlockDef blockDef = painter.getBlockDef();
                blockDef.fillBlock(block);

                // Check if block type is CUBE shape (for offset application)
                String worldIdStr = painter.getWorld().getWorldId();
                de.mhus.nimbus.shared.types.WorldId worldId = de.mhus.nimbus.shared.types.WorldId.of(worldIdStr)
                        .orElse(null);
                boolean isCubeShape = (worldId != null) && isCubeBlock(worldId, block.getBlockTypeId());

                // Apply rotation variance if configured
                if (finalRotationVarianceX != 0 || finalRotationVarianceY != 0) {
                    double rotX = randomVariance(random, finalRotationVarianceX);
                    double rotY = randomVariance(random, finalRotationVarianceY);

                    RotationXY rotation = RotationXY.builder()
                            .x(rotX)
                            .y(rotY)
                            .build();
                    block.setRotation(rotation);
                }

                // Apply offset variance if configured AND block is CUBE shape
                if (isCubeShape && (finalOffsetVarianceMin != 0.0 || finalOffsetVarianceMax != 0.0)) {
                    float offsetX = (float) randomRange(random, finalOffsetVarianceMin, finalOffsetVarianceMax);
                    float offsetY = (float) randomRange(random, finalOffsetVarianceMin, finalOffsetVarianceMax);
                    float offsetZ = (float) randomRange(random, finalOffsetVarianceMin, finalOffsetVarianceMax);

                    List<Float> offsets = new ArrayList<>();
                    offsets.add(offsetX);
                    offsets.add(offsetY);
                    offsets.add(offsetZ);
                    block.setOffsets(offsets);
                }

                // Save block
                painter.getEditService().doSetAndSendBlock(
                        painter.getWorld(),
                        painter.getLayerDataId(),
                        painter.getModelName(),
                        block,
                        painter.getGroupId()
                );

                // Add block to ModelSelector if context is available
                if (painter.getContext() != null && painter.getContext().getModelSelector() != null) {
                    String color = painter.getContext().getModelSelector().getDefaultColor();
                    if (color == null) {
                        color = "#00ff00"; // Default green
                    }
                    painter.getContext().getModelSelector().addBlock(x, y, z, color);
                }
            }

            /**
             * Generate random variance value from -1 to +maxVariance
             */
            private double randomVariance(Random random, int maxVariance) {
                if (maxVariance == 0) return 0.0;
                // Generate value from -1 to +maxVariance
                return -1.0 + random.nextDouble() * (maxVariance + 1.0);
            }

            /**
             * Generate random value in range [min, max]
             */
            private double randomRange(Random random, double min, double max) {
                if (min == max) return min;
                return min + random.nextDouble() * (max - min);
            }
        };
    }

    /**
     * Check if block type has CUBE shape (shape index 1).
     * Uses cache for performance.
     */
    private boolean isCubeBlock(de.mhus.nimbus.shared.types.WorldId worldId, String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }

        String cacheKey = worldId.getId() + ":" + blockTypeId;

        // Check cache first
        BlockType blockType = blockTypeCache.get(cacheKey);
        if (blockType == null) {
            // Load from service
            Optional<WBlockType> wBlockTypeOpt = blockTypeService.findByBlockId(
                    worldId,
                    blockTypeId
            );

            if (wBlockTypeOpt.isEmpty()) {
                log.debug("BlockType not found: {}", blockTypeId);
                return false;
            }

            blockType = wBlockTypeOpt.get().getPublicData();
            if (blockType == null) {
                log.debug("BlockType has no publicData: {}", blockTypeId);
                return false;
            }

            // Cache for next time
            blockTypeCache.put(cacheKey, blockType);
        }

        // Check if modifier[0].visibility.shape == CUBE (1)
        if (blockType.getModifiers() == null) {
            return false;
        }

        BlockModifier modifier0 = blockType.getModifiers().get(0);
        if (modifier0 == null || modifier0.getVisibility() == null) {
            return false;
        }

        Integer shape = modifier0.getVisibility().getShape();
        return shape != null && shape == Shape.CUBE.getTsIndex();
    }
}
