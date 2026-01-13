package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for managing MaterialDefinitions on WFlat instances.
 * Handles setting and updating material definitions for flat terrain.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FlatMaterialService {

    public static final int GRASS = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int SAND = 4;
    public static final int WATER = 5;
    public static final int BEDROCK = 6;
    public static final int SNOW = 7;
    public static final int INVISIBLE = 8;
    public static final int INVISIBLE_SOLID = 9;

    public static final int UNKNOWN_PROTECTED = 0;
    public static final int UNKNOWN_NOT_PROTECTED = 255;

    public static final String PALETTE_LEGACY = "legacy";
    public static final String PALETTE_MIMBUS = "nimbus";

    // Preset material palettes with format: "blockDef|nextBlockDef|hasOcean"
    private static final Map<String,Map<Integer,String>> PRESET_MATERIALS = Map.of(
            PALETTE_MIMBUS, Map.of(
                    GRASS, "n:g@s:default||true",
                    DIRT, "n:d@s:default||true",
                    STONE, "n:s@s:default||true",
                    SAND, "n:sa@s:default||true",
                    WATER, "n:w@s:default||true",
                    BEDROCK, "n:b@s:default||true",
                    SNOW, "n:sn@s:default||true",
                    INVISIBLE, "n:2@s:default||true",
                    INVISIBLE_SOLID, "n:3@s:default||true"
            ),
            PALETTE_LEGACY, Map.of(
                    GRASS, "w:310@s:default||true",        // old world
                    DIRT, "w:279@s:default||true",         // old world
                    STONE, "w:553@s:default||true",
                    SAND, "w:520@s:default||true",
                    WATER, "w:1008@s:default||true",
                    BEDROCK, "w:127@s:default||true",
                    SNOW, "w:537@s:default||true",
                    INVISIBLE, "w:2@s:default||true",
                    INVISIBLE_SOLID, "w:3@s:default||true"
            )
    );


    private final WFlatService flatService;

    /**
     * Set a single material definition on a flat.
     *
     * @param flatId Flat database ID
     * @param materialId Material ID (0-255)
     * @param blockDef Block definition string (e.g. "n:stone@s:default")
     * @param nextBlockDef Next block definition (used below level, can be null)
     * @param hasOcean Whether this material has ocean at ocean level
     * @return Updated WFlat instance
     * @throws IllegalArgumentException if flat not found or materialId out of range
     */
    public WFlat setMaterialDefinition(String flatId, int materialId, String blockDef,
                                       String nextBlockDef, boolean hasOcean) {
        log.debug("Setting material definition: flatId={}, materialId={}, blockDef={}, nextBlockDef={}, hasOcean={}",
                flatId, materialId, blockDef, nextBlockDef, hasOcean);

        // Load flat
        WFlat flat = flatService.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        // Validate materialId
        if (materialId < 0 || materialId > 255) {
            throw new IllegalArgumentException("Material ID must be between 0 and 255, got: " + materialId);
        }

        if (materialId == WFlat.NOT_SET) {
            log.warn("Cannot set material definition for NOT_SET (0), skipping");
            return flat;
        }

        // Create and set material definition
        WFlat.MaterialDefinition materialDef = WFlat.MaterialDefinition.builder()
                .blockDef(blockDef)
                .nextBlockDef(nextBlockDef)
                .hasOcean(hasOcean)
                .build();

        flat.setMaterial(materialId, materialDef);

        // Update flat
        WFlat updated = flatService.update(flat);
        log.info("Material definition set: flatId={}, materialId={}", flatId, materialId);

        return updated;
    }

    /**
     * Set multiple material definitions on a flat at once.
     * Properties map format:
     * - Key: "materialId" (e.g. "1", "2", "3")
     * - Value: JSON-like string with format "blockDef|nextBlockDef|hasOcean"
     *   Example: "n:stone@s:default|n:dirt@s:default|false"
     *
     * @param flatId Flat database ID
     * @param properties Map of materialId to property strings
     * @return Updated WFlat instance
     * @throws IllegalArgumentException if flat not found or invalid properties
     */
    public WFlat setMaterialDefinitions(String flatId, Map<String, String> properties) {
        log.debug("Setting material definitions: flatId={}, count={}", flatId, properties.size());

        // Load flat
        WFlat flat = flatService.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        int updated = 0;
        int skipped = 0;

        // Process each property
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String materialIdStr = entry.getKey();
            String propertyValue = entry.getValue();

            try {
                // Parse material ID
                int materialId = Integer.parseInt(materialIdStr);

                if (materialId < 0 || materialId > 255) {
                    log.warn("Invalid material ID {}, skipping", materialId);
                    skipped++;
                    continue;
                }

                if (materialId == WFlat.NOT_SET) {
                    log.debug("Skipping NOT_SET (0) material");
                    skipped++;
                    continue;
                }

                // Parse property value: "blockDef|nextBlockDef|hasOcean"
                String[] parts = propertyValue.split("\\|", 3);
                if (parts.length < 1) {
                    log.warn("Invalid property format for materialId {}: {}, skipping", materialId, propertyValue);
                    skipped++;
                    continue;
                }

                String blockDef = parts[0];
                String nextBlockDef = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
                boolean hasOcean = parts.length > 2 && Boolean.parseBoolean(parts[2]);

                // Create and set material definition
                WFlat.MaterialDefinition materialDef = WFlat.MaterialDefinition.builder()
                        .blockDef(blockDef)
                        .nextBlockDef(nextBlockDef)
                        .hasOcean(hasOcean)
                        .build();

                flat.setMaterial(materialId, materialDef);
                updated++;

                log.debug("Set material {}: blockDef={}, nextBlockDef={}, hasOcean={}",
                        materialId, blockDef, nextBlockDef, hasOcean);

            } catch (NumberFormatException e) {
                log.warn("Invalid material ID format: {}, skipping", materialIdStr);
                skipped++;
            } catch (Exception e) {
                log.warn("Failed to set material definition for {}: {}, skipping", materialIdStr, e.getMessage());
                skipped++;
            }
        }

        // Update flat
        WFlat result = flatService.update(flat);
        log.info("Material definitions set: flatId={}, updated={}, skipped={}", flatId, updated, skipped);

        return result;
    }

    /**
     * Set multiple material definitions using a more structured format.
     *
     * @param flatId Flat database ID
     * @param materials Map of materialId to MaterialDefinition
     * @return Updated WFlat instance
     * @throws IllegalArgumentException if flat not found
     */
    public WFlat setMaterialDefinitionsTyped(String flatId, Map<Integer, WFlat.MaterialDefinition> materials) {
        log.debug("Setting typed material definitions: flatId={}, count={}", flatId, materials.size());

        // Load flat
        WFlat flat = flatService.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        int updated = 0;

        // Set each material definition
        for (Map.Entry<Integer, WFlat.MaterialDefinition> entry : materials.entrySet()) {
            int materialId = entry.getKey();
            WFlat.MaterialDefinition materialDef = entry.getValue();

            if (materialId < 0 || materialId > 255) {
                log.warn("Invalid material ID {}, skipping", materialId);
                continue;
            }

            if (materialId == WFlat.NOT_SET) {
                log.debug("Skipping NOT_SET (0) material");
                continue;
            }

            flat.setMaterial(materialId, materialDef);
            updated++;
        }

        // Update flat
        WFlat result = flatService.update(flat);
        log.info("Typed material definitions set: flatId={}, updated={}", flatId, updated);

        return result;
    }

    /**
     * Get a material definition from a flat.
     *
     * @param flatId Flat database ID
     * @param materialId Material ID (0-255)
     * @return MaterialDefinition or null if not found
     * @throws IllegalArgumentException if flat not found or materialId out of range
     */
    public WFlat.MaterialDefinition getMaterialDefinition(String flatId, int materialId) {
        log.debug("Getting material definition: flatId={}, materialId={}", flatId, materialId);

        // Load flat
        WFlat flat = flatService.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        return flat.getMaterial(materialId);
    }

    /**
     * Remove a material definition from a flat.
     *
     * @param flatId Flat database ID
     * @param materialId Material ID (0-255)
     * @return Updated WFlat instance
     * @throws IllegalArgumentException if flat not found or materialId out of range
     */
    public WFlat removeMaterialDefinition(String flatId, int materialId) {
        log.debug("Removing material definition: flatId={}, materialId={}", flatId, materialId);

        // Load flat
        WFlat flat = flatService.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        // Remove material by setting null
        flat.setMaterial(materialId, null);

        // Update flat
        WFlat updated = flatService.update(flat);
        log.info("Material definition removed: flatId={}, materialId={}", flatId, materialId);

        return updated;
    }

    /**
     * Set a predefined material palette on a flat.
     * Available palettes: "nimbus" (PALETTE_NIMBUS), "legacy" (PALETTE_LEGACY)
     *
     * @param flatId Flat database ID
     * @param paletteName Name of the predefined palette
     * @return Updated WFlat instance
     * @throws IllegalArgumentException if flat not found or palette not found
     */
    public WFlat setPalette(String flatId, String paletteName) {
        log.debug("Setting palette: flatId={}, paletteName={}", flatId, paletteName);

        // Validate palette name
        if (paletteName == null || paletteName.isBlank()) {
            throw new IllegalArgumentException("Palette name required");
        }

        // Get palette from presets
        Map<Integer, String> palette = PRESET_MATERIALS.get(paletteName);
        if (palette == null) {
            throw new IllegalArgumentException("Palette not found: " + paletteName +
                    ". Available palettes: " + PRESET_MATERIALS.keySet());
        }

        // Convert palette to MaterialDefinition map
        // Format: "blockDef|nextBlockDef|hasOcean"
        Map<Integer, WFlat.MaterialDefinition> materials = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> entry : palette.entrySet()) {
            int materialId = entry.getKey();
            String propertyValue = entry.getValue();

            // Parse property value: "blockDef|nextBlockDef|hasOcean"
            String[] parts = propertyValue.split("\\|", 3);
            if (parts.length < 1) {
                log.warn("Invalid property format for materialId {}: {}, skipping", materialId, propertyValue);
                continue;
            }

            String blockDef = parts[0];
            String nextBlockDef = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            boolean hasOcean = parts.length > 2 && Boolean.parseBoolean(parts[2]);

            WFlat.MaterialDefinition materialDef = WFlat.MaterialDefinition.builder()
                    .blockDef(blockDef)
                    .nextBlockDef(nextBlockDef)
                    .hasOcean(hasOcean)
                    .build();

            materials.put(materialId, materialDef);
        }

        // Set all materials using typed method
        WFlat result = setMaterialDefinitionsTyped(flatId, materials);
        log.info("Palette set: flatId={}, paletteName={}, materials={}", flatId, paletteName, materials.size());

        return result;
    }
}
