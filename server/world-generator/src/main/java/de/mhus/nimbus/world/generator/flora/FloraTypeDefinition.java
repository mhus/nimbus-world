package de.mhus.nimbus.world.generator.flora;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Definition of a flora type stored in WAnything.
 * Contains a list of plant definitions that can grow in this flora type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FloraTypeDefinition {

    private List<FloraPlantDefinition> plants;
}
