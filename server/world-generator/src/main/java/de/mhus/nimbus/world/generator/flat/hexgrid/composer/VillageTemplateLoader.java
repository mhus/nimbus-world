package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads village templates from resources
 */
@Slf4j
public class VillageTemplateLoader {

    private static final Map<String, VillageTemplate> CACHE = new HashMap<>();

    /**
     * Loads template from resources/village-templates/
     */
    public static VillageTemplate load(String templateName) {
        if (CACHE.containsKey(templateName)) {
            log.debug("Loading template from cache: {}", templateName);
            return CACHE.get(templateName);
        }

        String path = "/village-templates/" + templateName + ".json";
        log.info("Loading village template: {}", path);

        try (InputStream is = VillageTemplateLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Template not found: " + templateName);
            }

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            VillageTemplate template = VillageTemplate.fromJson(json);
            CACHE.put(templateName, template);

            log.info("Successfully loaded template: {} ({} buildings, {} streets)",
                templateName,
                template.getBuildings() != null ? template.getBuildings().size() : 0,
                template.getStreets() != null ? template.getStreets().size() : 0);

            return template;
        } catch (Exception e) {
            log.error("Failed to load template: {}", templateName, e);
            throw new RuntimeException("Failed to load template: " + templateName, e);
        }
    }

    /**
     * Lists all available templates
     */
    public static List<String> listTemplates() {
        return List.of(
            "hamlet-medieval",
            "hamlet-farming",
            "village-2x1-medieval",
            "village-3x1-trading",
            "town-5cross-medieval",
            "town-7hex-large"
        );
    }

    /**
     * Clears the template cache
     */
    public static void clearCache() {
        log.info("Clearing template cache");
        CACHE.clear();
    }
}
