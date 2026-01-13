package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.world.shared.session.EditState;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Smooths block offsets between neighboring cube blocks.
 * Adjusts offsets to create smooth transitions by averaging values across neighbors.
 */
@Builder
@Slf4j
public class SmoothBlockOperation {

    private EditService editService;
    private EditState editState;
    private String sessionId;
    private int centerX;
    private int centerY;
    private int centerZ;

    /**
     * Execute the smooth operation.
     * Reads all blocks around the center position and smooths offsets for cube-shaped blocks.
     */
    public void execute() {
        log.debug("Executing smooth operation at ({},{},{})", centerX, centerY, centerZ);

        // Read center block
        Block centerBlock = editService.getBlock(editState, sessionId, centerX, centerY, centerZ);
        if (centerBlock == null) {
            log.debug("No center block found at ({},{},{})", centerX, centerY, centerZ);
            return;
        }

        // Check if center block is a cube (has offsets)
        if (!isCubeBlock(centerBlock)) {
            log.debug("Center block is not a cube shape at ({},{},{})", centerX, centerY, centerZ);
            return;
        }

        // Define the 6 neighbor positions (cardinal directions)
        int[][] neighbors = {
                {centerX + 1, centerY, centerZ}, // East
                {centerX - 1, centerY, centerZ}, // West
                {centerX, centerY, centerZ + 1}, // South
                {centerX, centerY, centerZ - 1}, // North
                {centerX, centerY + 1, centerZ}, // Up
                {centerX, centerY - 1, centerZ}  // Down
        };

        // Process each direction
        for (int[] neighborPos : neighbors) {
            smoothBlockPair(centerX, centerY, centerZ, neighborPos[0], neighborPos[1], neighborPos[2]);
        }
    }

    /**
     * Smooth the transition between two blocks.
     * Adjusts offsets by averaging them to create a smooth transition.
     */
    private void smoothBlockPair(int x1, int y1, int z1, int x2, int y2, int z2) {
        Block block1 = editService.getBlock(editState, sessionId, x1, y1, z1);
        Block block2 = editService.getBlock(editState, sessionId, x2, y2, z2);

        if (block1 == null || block2 == null) {
            return;
        }

        // Both blocks must be cubes
        if (!isCubeBlock(block1) || !isCubeBlock(block2)) {
            return;
        }

        // Get offsets (default to [0, 0, 0, 0, 0, 0] if null)
        List<Float> offsets1 = getOffsets(block1);
        List<Float> offsets2 = getOffsets(block2);

        // Calculate smoothed offsets (average between current values)
        // Use a smoothing factor to gradually adjust (not instantly to average)
        float smoothFactor = 0.3f; // 30% adjustment per execution

        List<Float> newOffsets1 = new ArrayList<>(6);
        List<Float> newOffsets2 = new ArrayList<>(6);

        for (int i = 0; i < 6; i++) {
            float val1 = offsets1.get(i);
            float val2 = offsets2.get(i);
            float avg = (val1 + val2) / 2.0f;

            // Move each value towards the average by smoothFactor
            float newVal1 = val1 + (avg - val1) * smoothFactor;
            float newVal2 = val2 + (avg - val2) * smoothFactor;

            newOffsets1.add(newVal1);
            newOffsets2.add(newVal2);
        }

        // Update blocks if offsets changed significantly
        if (offsetsChanged(offsets1, newOffsets1)) {
            block1.setOffsets(newOffsets1);
            editService.updateBlock(editState, sessionId, x1, y1, z1, block1);
            log.debug("Smoothed block at ({},{},{})", x1, y1, z1);
        }

        if (offsetsChanged(offsets2, newOffsets2)) {
            block2.setOffsets(newOffsets2);
            editService.updateBlock(editState, sessionId, x2, y2, z2, block2);
            log.debug("Smoothed block at ({},{},{})", x2, y2, z2);
        }
    }

    /**
     * Check if a block is cube-shaped (has offsets).
     */
    private boolean isCubeBlock(Block block) {
        // A cube block has offsets defined
        // If offsets is null or empty, it's not a modifiable cube
        List<Float> offsets = block.getOffsets();
        return offsets != null && !offsets.isEmpty();
    }

    /**
     * Get offsets from block, defaulting to [0,0,0,0,0,0] if null.
     */
    private List<Float> getOffsets(Block block) {
        List<Float> offsets = block.getOffsets();
        if (offsets == null || offsets.isEmpty()) {
            return List.of(0f, 0f, 0f, 0f, 0f, 0f);
        }
        // Ensure we have exactly 6 values
        if (offsets.size() < 6) {
            List<Float> padded = new ArrayList<>(offsets);
            while (padded.size() < 6) {
                padded.add(0f);
            }
            return padded;
        }
        return offsets;
    }

    /**
     * Check if offsets changed significantly (more than 0.001).
     */
    private boolean offsetsChanged(List<Float> old, List<Float> newVals) {
        float threshold = 0.001f;
        for (int i = 0; i < Math.min(old.size(), newVals.size()); i++) {
            if (Math.abs(old.get(i) - newVals.get(i)) > threshold) {
                return true;
            }
        }
        return false;
    }
}
