package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import de.mhus.nimbus.world.generator.composer.image.CrossOverlay;
import de.mhus.nimbus.world.generator.composer.image.TextOverlay;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.util.Map;

/**
 * Helper class for adding debug overlays to composite images for village slots.
 * Creates CrossOverlay markers and TextOverlay labels for each village slot
 * to visualize slot positions and names.
 */
@Slf4j
public class TownDebugOverlayHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Adds debug overlays for village slots to the composite image creator.
     * For each slot in the village configuration:
     * - Adds a CrossOverlay at the slot center
     * - Adds a TextOverlay with the slot name below the cross
     *
     * @param creator The HexGridCompositeImageCreator to add overlays to
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
     * @param creator     The HexGridCompositeImageCreator to add overlays to
     * @param hexGrids    Map of hex coordinates to WHexGrid objects containing g_village parameters
     * @param hexGridSize Height of the hex grid in pixels (radius=hexGridSize/2, hexGridWidth=HexMathUtil.getGridWidth(hexGridSize))
     */
    public static void addVillageSlotOverlaysFromHexGrids(
            HexGridCompositeImageCreator creator,
            Map<HexVector2, WHexGrid> hexGrids,
            int hexGridSize) {

        if (creator == null || hexGrids == null) {
            return;
        }

        int addedOverlays = 0;

        for (Map.Entry<HexVector2, WHexGrid> entry : hexGrids.entrySet()) {

            HexVector2 coord = entry.getKey();
            WHexGrid hexGrid = entry.getValue();

            String villageParam = hexGrid.getParameters() != null ?
                    hexGrid.getParameters().get("g_village") : null;

            if (villageParam == null || villageParam.isBlank()) {
                continue;
            }

            try {
                TownGridConfig config = objectMapper.readValue(villageParam, TownGridConfig.class);

                if (config.getPlaces() == null || config.getPlaces().isEmpty()) {
                    continue;
                }

                // Hex center in the same coordinate system as HexGridCompositeImageCreator bounds
                int[] hexCenter = de.mhus.nimbus.world.shared.util.HexMathUtil.hexToCartesian(coord, hexGridSize);

                for (TownGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
                    // localX/localZ are in hex grid space (0..hexGridSize), center at hexGridSize/2.
                    // Calculated in TownDesigner as: localX = hexGridSize / 2 + relativePos.getX()
                    // World coordinate = hex top-left + local offset
                    double worldX = hexCenter[0] - hexGridSize / 2.0 + place.getLocalX();
                    double worldZ = hexCenter[1] - hexGridSize / 2.0 + place.getLocalZ();

                    creator.addOverlay(new CrossOverlay(worldX, worldZ, 15, Color.RED, 3.0f));

                    String slotName = place.getName();
                    if (slotName != null && !slotName.isEmpty()) {
                        TextOverlay textOverlay = new TextOverlay(slotName, (int) worldX, (int) worldZ + 20, Color.YELLOW, 2);
                        int textWidth = textOverlay.getTextWidth();
                        textOverlay.setX((int) worldX - textWidth / 2);
                        creator.addOverlay(textOverlay);
                    }

                    addedOverlays += 2;
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
