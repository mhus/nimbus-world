package de.mhus.nimbus.world.generator.fauna;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Container for fauna animal definitions.
 * Loaded from WAnything collection "fauna".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FaunaTypeDefinition {
    private List<FaunaAnimalDefinition> animals;
}
