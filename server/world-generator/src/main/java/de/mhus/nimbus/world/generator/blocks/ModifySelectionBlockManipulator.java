package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Modify Selection Block Manipulator - modifies the current ModelSelector in WSession.
 *
 * This manipulator does NOT create or delete blocks in the world - it only modifies
 * the selection (ModelSelector) by adding/removing block coordinates.
 *
 * Operations (executed in order):
 * 1. enhance - Expand/shrink selection as a bounding box
 * 2. move - Move all selected blocks by offset
 * 3. add - Add new blocks to selection
 * 4. remove - Remove blocks from selection
 *
 * Parameters:
 * - add: array of {x, y, z, color (optional)} - Add blocks to selection
 * - remove: array of {x, y, z} - Remove blocks from selection
 * - move: {x, y, z} - Move all blocks by offset
 * - enhance: {north, south, east, west, top, bottom} - Expand/shrink bounding box
 *
 * Example:
 * <pre>
 * {
 *   "modify-selection": {
 *     "enhance": {
 *       "north": 1,
 *       "south": 1,
 *       "east": 1,
 *       "west": -1,
 *       "top": 1,
 *       "bottom": -1
 *     },
 *     "move": {
 *       "x": 1,
 *       "y": 0,
 *       "z": -1
 *     },
 *     "add": [
 *       {"x": 101, "y": 64, "z": 100, "color": "#ff0000"},
 *       {"x": 102, "y": 64, "z": 100, "color": "#00ff00"}
 *     ],
 *     "remove": [
 *       {"x": 100, "y": 64, "z": 100},
 *       {"x": 99, "y": 64, "z": 100}
 *     ]
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ModifySelectionBlockManipulator implements BlockManipulator {

    private final WSessionService wSessionService;

    @Override
    public String getName() {
        return "modify-selection";
    }

    @Override
    public String getTitle() {
        return "Modify Selection";
    }

    @Override
    public String getDescription() {
        return "Modifies the current ModelSelector in WSession. " +
                "Operations: enhance (expand/shrink bounding box), move (offset all blocks), " +
                "add (add blocks), remove (remove blocks). " +
                "Example: {\"modify-selection\": {\"enhance\": {\"x\": 1, \"y\": 0, \"z\": 1}}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return ManipulatorResult.error("SessionId required for modify-selection");
        }

        // Load current ModelSelector from WSession
        Optional<de.mhus.nimbus.world.shared.session.WSession> sessionOpt = wSessionService.get(sessionId);
        if (sessionOpt.isEmpty()) {
            return ManipulatorResult.error("Session not found: " + sessionId);
        }

        List<String> modelSelectorData = sessionOpt.get().getModelSelector();
        if (modelSelectorData == null || modelSelectorData.isEmpty()) {
            return ManipulatorResult.error("No selection found. Please select blocks first.");
        }

        // Parse ModelSelector from WSession data
        ModelSelector currentSelector = ModelSelector.fromStringList(modelSelectorData);

        // Parse current selection into a set of coordinates
        List<String> blocks = currentSelector.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return ManipulatorResult.error("Selection is empty");
        }

        Set<BlockCoordinate> selectedBlocks = new HashSet<>();
        String defaultColor = currentSelector.getDefaultColor();
        if (defaultColor == null || defaultColor.isBlank()) {
            defaultColor = "#00ff00";
        }

        for (String entry : blocks) {
            String[] parts = entry.split(",");
            if (parts.length >= 3) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    String color = parts.length >= 4 ? parts[3] : defaultColor;
                    selectedBlocks.add(new BlockCoordinate(x, y, z, color));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse block coordinate: {}", entry);
                }
            }
        }

        if (selectedBlocks.isEmpty()) {
            return ManipulatorResult.error("No valid blocks in selection");
        }

        int originalCount = selectedBlocks.size();
        log.info("Modifying selection: {} blocks", originalCount);

        // Execute operations in order: enhance -> move -> add -> remove

        // 1. Enhance (expand/shrink bounding box)
        JsonNode enhanceNode = context.getJsonParameter("enhance");
        if (enhanceNode != null && enhanceNode.isObject()) {
            selectedBlocks = applyEnhance(selectedBlocks, enhanceNode, defaultColor);
        }

        // 2. Move (offset all blocks)
        JsonNode moveNode = context.getJsonParameter("move");
        if (moveNode != null && moveNode.isObject()) {
            selectedBlocks = applyMove(selectedBlocks, moveNode);
        }

        // 3. Add (add new blocks)
        JsonNode addNode = context.getJsonParameter("add");
        if (addNode != null && addNode.isArray()) {
            selectedBlocks = applyAdd(selectedBlocks, addNode, defaultColor);
        }

        // 4. Remove (remove blocks)
        JsonNode removeNode = context.getJsonParameter("remove");
        if (removeNode != null && removeNode.isArray()) {
            selectedBlocks = applyRemove(selectedBlocks, removeNode);
        }

        // Build new ModelSelector
        ModelSelector newSelector = ModelSelector.builder()
                .defaultColor(defaultColor)
                .autoSelectName(currentSelector.getAutoSelectName())
                .build();

        for (BlockCoordinate block : selectedBlocks) {
            newSelector.addBlock(block.x, block.y, block.z, block.color);
        }

        int finalCount = selectedBlocks.size();
        String message = String.format("Modified selection: %d blocks (was %d, %+d)",
                finalCount, originalCount, finalCount - originalCount);

        log.info(message);

        return ManipulatorResult.success(message, newSelector);
    }

    /**
     * Apply enhance operation - expand/shrink bounding box
     */
    private Set<BlockCoordinate> applyEnhance(Set<BlockCoordinate> blocks, JsonNode enhanceNode, String defaultColor) {
        // Find bounding box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockCoordinate block : blocks) {
            minX = Math.min(minX, block.x);
            maxX = Math.max(maxX, block.x);
            minY = Math.min(minY, block.y);
            maxY = Math.max(maxY, block.y);
            minZ = Math.min(minZ, block.z);
            maxZ = Math.max(maxZ, block.z);
        }

        // Parse enhance parameters
        int north = enhanceNode.has("north") ? enhanceNode.get("north").asInt() : 0;  // -Z
        int south = enhanceNode.has("south") ? enhanceNode.get("south").asInt() : 0;  // +Z
        int east = enhanceNode.has("east") ? enhanceNode.get("east").asInt() : 0;     // +X
        int west = enhanceNode.has("west") ? enhanceNode.get("west").asInt() : 0;     // -X
        int top = enhanceNode.has("top") ? enhanceNode.get("top").asInt() : 0;        // +Y
        int bottom = enhanceNode.has("bottom") ? enhanceNode.get("bottom").asInt() : 0; // -Y

        // Calculate new bounding box
        int newMinX = minX - west;
        int newMaxX = maxX + east;
        int newMinY = minY - bottom;
        int newMaxY = maxY + top;
        int newMinZ = minZ - north;
        int newMaxZ = maxZ + south;

        log.info("Enhance: bounds ({},{},{}) to ({},{},{}) -> new bounds ({},{},{}) to ({},{},{})",
                minX, minY, minZ, maxX, maxY, maxZ,
                newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);

        // Create new selection with all blocks in new bounding box
        Set<BlockCoordinate> result = new HashSet<>();

        // Keep all existing blocks that are still in bounds
        for (BlockCoordinate block : blocks) {
            if (block.x >= newMinX && block.x <= newMaxX &&
                block.y >= newMinY && block.y <= newMaxY &&
                block.z >= newMinZ && block.z <= newMaxZ) {
                result.add(block);
            }
        }

        // Add new blocks on the surfaces of the expanded bounding box
        // This creates a hollow box outline for the expanded areas

        // Top and bottom faces (Y)
        if (top > 0 || bottom > 0) {
            for (int x = newMinX; x <= newMaxX; x++) {
                for (int z = newMinZ; z <= newMaxZ; z++) {
                    if (top > 0) {
                        for (int y = maxY + 1; y <= newMaxY; y++) {
                            result.add(new BlockCoordinate(x, y, z, defaultColor));
                        }
                    }
                    if (bottom > 0) {
                        for (int y = newMinY; y < minY; y++) {
                            result.add(new BlockCoordinate(x, y, z, defaultColor));
                        }
                    }
                }
            }
        }

        // North and south faces (Z)
        if (north > 0 || south > 0) {
            for (int x = newMinX; x <= newMaxX; x++) {
                for (int y = newMinY; y <= newMaxY; y++) {
                    if (north > 0) {
                        for (int z = newMinZ; z < minZ; z++) {
                            result.add(new BlockCoordinate(x, y, z, defaultColor));
                        }
                    }
                    if (south > 0) {
                        for (int z = maxZ + 1; z <= newMaxZ; z++) {
                            result.add(new BlockCoordinate(x, y, z, defaultColor));
                        }
                    }
                }
            }
        }

        // East and west faces (X)
        if (east > 0 || west > 0) {
            for (int y = newMinY; y <= newMaxY; y++) {
                for (int z = newMinZ; z <= newMaxZ; z++) {
                    if (east > 0) {
                        for (int x = maxX + 1; x <= newMaxX; x++) {
                            result.add(new BlockCoordinate(x, y, z, defaultColor));
                        }
                    }
                    if (west > 0) {
                        for (int x = newMinX; x < minX; x++) {
                            result.add(new BlockCoordinate(x, y, z, defaultColor));
                        }
                    }
                }
            }
        }

        log.info("Enhanced selection: {} -> {} blocks", blocks.size(), result.size());
        return result;
    }

    /**
     * Apply move operation - offset all blocks
     */
    private Set<BlockCoordinate> applyMove(Set<BlockCoordinate> blocks, JsonNode moveNode) {
        int dx = moveNode.has("x") ? moveNode.get("x").asInt() : 0;
        int dy = moveNode.has("y") ? moveNode.get("y").asInt() : 0;
        int dz = moveNode.has("z") ? moveNode.get("z").asInt() : 0;

        if (dx == 0 && dy == 0 && dz == 0) {
            return blocks; // No movement
        }

        Set<BlockCoordinate> result = new HashSet<>();
        for (BlockCoordinate block : blocks) {
            result.add(new BlockCoordinate(block.x + dx, block.y + dy, block.z + dz, block.color));
        }

        log.info("Moved selection by ({},{},{})", dx, dy, dz);
        return result;
    }

    /**
     * Apply add operation - add new blocks
     */
    private Set<BlockCoordinate> applyAdd(Set<BlockCoordinate> blocks, JsonNode addNode, String defaultColor) {
        Set<BlockCoordinate> result = new HashSet<>(blocks);
        int added = 0;

        for (JsonNode blockNode : addNode) {
            if (blockNode.isObject()) {
                Integer x = blockNode.has("x") ? blockNode.get("x").asInt() : null;
                Integer y = blockNode.has("y") ? blockNode.get("y").asInt() : null;
                Integer z = blockNode.has("z") ? blockNode.get("z").asInt() : null;
                String color = blockNode.has("color") ? blockNode.get("color").asText() : defaultColor;

                if (x != null && y != null && z != null) {
                    if (result.add(new BlockCoordinate(x, y, z, color))) {
                        added++;
                    }
                }
            }
        }

        log.info("Added {} blocks to selection", added);
        return result;
    }

    /**
     * Apply remove operation - remove blocks
     */
    private Set<BlockCoordinate> applyRemove(Set<BlockCoordinate> blocks, JsonNode removeNode) {
        Set<BlockCoordinate> result = new HashSet<>(blocks);
        int removed = 0;

        for (JsonNode blockNode : removeNode) {
            if (blockNode.isObject()) {
                Integer x = blockNode.has("x") ? blockNode.get("x").asInt() : null;
                Integer y = blockNode.has("y") ? blockNode.get("y").asInt() : null;
                Integer z = blockNode.has("z") ? blockNode.get("z").asInt() : null;

                if (x != null && y != null && z != null) {
                    // Remove by coordinates (color doesn't matter)
                    if (result.removeIf(b -> b.x == x && b.y == y && b.z == z)) {
                        removed++;
                    }
                }
            }
        }

        log.info("Removed {} blocks from selection", removed);
        return result;
    }

    /**
     * Helper class to represent a block coordinate with color
     */
    private static class BlockCoordinate {
        final int x, y, z;
        final String color;

        BlockCoordinate(int x, int y, int z, String color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockCoordinate that = (BlockCoordinate) o;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
