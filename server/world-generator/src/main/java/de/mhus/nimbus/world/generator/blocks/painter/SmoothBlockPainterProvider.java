package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.BlockModifier;
import de.mhus.nimbus.generated.types.BlockType;
import de.mhus.nimbus.generated.types.Shape;
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
 * Smooth Block Painter.
 * Creates smooth surfaces by adjusting block offsets to create smooth transitions.
 * Only applies offsets to CUBE shape blocks (shape index 1).
 *
 * This implementation creates smooth surfaces by applying small random offsets
 * that are biased towards smoothing rather than roughness.
 *
 * Parameters:
 * - smoothness: double (default 0.05) - how much to smooth the surface (0.0 = no smoothing, higher = more smoothing)
 *
 * Example:
 * <pre>
 * {
 *   "plateau": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 10,
 *     "depth": 10,
 *     "blockType": "n:s",
 *     "painter": "smooth",
 *     "smoothness": 0.05
 *   }
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmoothBlockPainterProvider implements BlockPainterProvider {

    private final WBlockTypeService blockTypeService;

    // Cache for BlockType lookups (worldId:blockId -> BlockType)
    private final Map<String, BlockType> blockTypeCache = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "smooth";
    }

    @Override
    public String getTitle() {
        return "Smooth Painter";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Read parameters with defaults
        Double smoothnessParam = context.getDoubleParameter("smoothness");
        final double smoothness = smoothnessParam != null ? smoothnessParam : 0.05;

        log.debug("Creating Smooth Painter: smoothness={}", smoothness);

        final Random random = new Random();

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

                // Apply smooth offsets if block is CUBE shape and smoothness is configured
                if (isCubeShape && smoothness > 0.0) {
                    // Create smooth offsets - small random values centered around 0
                    float offsetX = (float) (random.nextGaussian() * smoothness);
                    float offsetY = (float) (random.nextGaussian() * smoothness);
                    float offsetZ = (float) (random.nextGaussian() * smoothness);

                    // Clamp to reasonable range
                    offsetX = Math.max(-0.2f, Math.min(0.2f, offsetX));
                    offsetY = Math.max(-0.2f, Math.min(0.2f, offsetY));
                    offsetZ = Math.max(-0.2f, Math.min(0.2f, offsetZ));

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
        if (blockType.getModifiers() == null || blockType.getModifiers().isEmpty()) {
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
