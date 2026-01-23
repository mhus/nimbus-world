package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@GenerateTypeScript("dto")
public class BiomeDefinition {

    private String name;
    private String title;
    private BiomeType type;
    private BiomeShape shape;
    private BiomeSize size;
    private Integer sizeFrom;
    private Integer sizeTo;
    private List<RelativePosition> relativePositions;
    private Map<String, String> parameters;
    private String description;

    public int getEffectiveSizeFrom() {
        return sizeFrom != null ? sizeFrom : size.getFrom();
    }

    public int getEffectiveSizeTo() {
        return sizeTo != null ? sizeTo : size.getTo();
    }

    public String getDisplayTitle() {
        return title != null ? title : name;
    }
}
