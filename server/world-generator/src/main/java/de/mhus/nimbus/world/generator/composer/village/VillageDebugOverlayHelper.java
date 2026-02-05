package de.mhus.nimbus.world.generator.composer.village;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import de.mhus.nimbus.world.generator.composer.image.CrossOverlay;
import de.mhus.nimbus.world.generator.composer.image.TextOverlay;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.util.Map;

/**
 * Helper class for adding debug overlays to composite images for village slots.
 * Creates CrossOverlay markers and TextOverlay labels for each village slot
 * to visualize slot positions and names.
 */
@Slf4j
public class VillageDebugOverlayHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Adds debug overlays for village slots to the composite image creator.
     * For each slot in the village configuration:
     * - Adds a CrossOverlay at the slot center
     * - Adds a TextOverlay with the slot name below the cross
     *
     * @param creator The HexGridCompositeImageCreator to add overlays to
     * @param hexGrids Map of hex coordinates to WHexGrid objects containing g_village parameters
     */
    public static void addVillageSlotOverlays(HexGridCompositeImageCreator creator,
                                               Map<de.mhus.nimbus.generated.types.HexVector2,
                                                   de.mhus.nimbus.world.shared.generator.WFlat> flats) {
        if (creator == null || flats == null) {
            return;
        }

        int addedOverlays = 0;

        for (Map.Entry<de.mhus.nimbus.generated.types.HexVector2, de.mhus.nimbus.world.shared.generator.WFlat> entry : flats.entrySet()) {
            de.mhus.nimbus.generated.types.HexVector2 coord = entry.getKey();

            // Get g_village parameter from flat
            // Note: We need to access the WHexGrid to get the parameters
            // Since we only have WFlat here, we need to get the parameters differently
            // For now, skip this implementation - see addVillageSlotOverlaysFromHexGrids method
        }

        if (addedOverlays > 0) {
            log.debug("Added {} village slot overlays to composite image", addedOverlays);
        }
    }

    /**
     * Adds debug overlays for village slots from WHexGrid objects.
     * For each slot in the village configuration:
     * - Adds a CrossOverlay at the slot center
     * - Adds a TextOverlay with the slot name below the cross
     *
     * @param creator The HexGridCompositeImageCreator to add overlays to
     * @param hexGrids Map of hex coordinates to WHexGrid objects containing g_village parameters
     * @param flatSize Size of each flat in pixels (used for positioning in the composite image)
     */
    public static void addVillageSlotOverlaysFromHexGrids(
            HexGridCompositeImageCreator creator,
            Map<de.mhus.nimbus.generated.types.HexVector2,
                    de.mhus.nimbus.world.shared.world.WHexGrid> hexGrids,
            int flatSize) {

        if (creator == null || hexGrids == null) {
            return;
        }

        int addedOverlays = 0;

        for (Map.Entry<de.mhus.nimbus.generated.types.HexVector2,
                de.mhus.nimbus.world.shared.world.WHexGrid> entry : hexGrids.entrySet()) {

            de.mhus.nimbus.generated.types.HexVector2 coord = entry.getKey();
            de.mhus.nimbus.world.shared.world.WHexGrid hexGrid = entry.getValue();

            // Check if this grid has a g_village parameter
            String villageParam = hexGrid.getParameters() != null ?
                    hexGrid.getParameters().get("g_village") : null;

            if (villageParam == null || villageParam.isBlank()) {
                continue;
            }

            try {
                // Parse village configuration
                VillageGridConfig config = objectMapper.readValue(villageParam, VillageGridConfig.class);

                if (config.getPlaces() == null || config.getPlaces().isEmpty()) {
                    continue;
                }

                // Calculate hex center in world coordinates
                // IMPORTANT: Use flatSize here, not hexGridSize, because HexGridCompositeImageCreator
                // positions flats using flatSize
                double[] hexCenter = de.mhus.nimbus.world.shared.util.HexMathUtil.hexToCartesian(coord, flatSize);
                double hexCenterX = hexCenter[0];
                double hexCenterZ = hexCenter[1];

                // Add overlays for each slot
                for (VillageGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
                    // Calculate world coordinates for this slot
                    // IMPORTANT: In VillageBuilder, localX/localZ are calculated using:
                    //   int localX = flat.getSizeX() / 2 + relativePos.getX()
                    // where relativePos comes from HexLocalUtil.toHexGridLocalCenter() which uses hexGridSize (not flatSize)
                    // Since hexGridSize = flatSize - 30, and the border is split evenly, we need to add 15 pixels
                    // to account for the difference: (flatSize - hexGridSize) / 2 = 15
                    double worldX = hexCenterX - flatSize / 2.0 + place.getLocalX() + 15;
                    double worldZ = hexCenterZ - flatSize / 2.0 + place.getLocalZ() + 15;

                    // Add CrossOverlay at slot center
                    creator.addOverlay(new CrossOverlay(worldX, worldZ, 15, Color.RED, 3.0f));

                    // Add TextOverlay with slot name below the cross
                    String slotName = place.getName();
                    if (slotName != null && !slotName.isEmpty()) {
                        TextOverlay textOverlay = new TextOverlay(slotName, (int) worldX, (int) worldZ + 20, Color.YELLOW, 2);

                        // Center the text horizontally
                        int textWidth = textOverlay.getTextWidth();
                        textOverlay.setX((int) worldX - textWidth / 2);

                        creator.addOverlay(textOverlay);
                    }

                    addedOverlays += 2; // Cross + Text
                }

                log.debug("Added {} overlays for village district '{}' at [{},{}]",
                        config.getPlaces().size() * 2,
                        config.getDistrictName(),
                        coord.getQ(), coord.getR());

            } catch (Exception e) {
                log.warn("Failed to parse g_village parameter for grid [{},{}]: {}",
                        coord.getQ(), coord.getR(), e.getMessage());
            }
        }

        if (addedOverlays > 0) {
            log.debug("Added {} village slot overlays to composite image", addedOverlays);
        }
    }
}
