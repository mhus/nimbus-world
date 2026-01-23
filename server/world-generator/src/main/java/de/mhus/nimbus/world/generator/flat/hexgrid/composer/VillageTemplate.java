package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Template definition for a village layout
 * Can be loaded from JSON and used to generate concrete villages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class VillageTemplate {
    /**
     * Template name (e.g., "medieval-hamlet", "farming-village")
     */
    private String name;

    /**
     * Village size category
     */
    private VillageSize size;

    /**
     * Village style/theme
     */
    private VillageStyle style;

    /**
     * Grid layout definition
     */
    private VillageGridLayout layout;

    /**
     * Plaza/square configuration (optional)
     */
    private PlazaDefinition plaza;

    /**
     * Building definitions
     */
    private List<TemplateBuildingDefinition> buildings;

    /**
     * Street definitions
     */
    private List<TemplateStreetDefinition> streets;

    /**
     * Serializes this template to JSON
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize VillageTemplate to JSON", e);
            throw new RuntimeException("Failed to serialize VillageTemplate", e);
        }
    }

    /**
     * Deserializes a template from JSON
     */
    public static VillageTemplate fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, VillageTemplate.class);
        } catch (Exception e) {
            log.error("Failed to deserialize VillageTemplate from JSON", e);
            throw new RuntimeException("Failed to deserialize VillageTemplate", e);
        }
    }

    /**
     * Gets a default template for a given size and style
     * This will attempt to load a built-in template from resources
     */
    public static VillageTemplate getDefaultTemplate(VillageSize size, VillageStyle style) {
        String templateName = style.getStyleName() + "-" + size.name().toLowerCase().replace('_', '-');
        return VillageTemplateLoader.load(templateName);
    }
}
