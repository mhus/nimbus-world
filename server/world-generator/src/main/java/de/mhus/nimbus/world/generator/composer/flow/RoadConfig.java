package de.mhus.nimbus.world.generator.composer.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * RoadBuilder configuration
 */
@Data
@Builder
@NoArgsConstructor
// PRIVATE on purpose: a public all-args constructor is picked up by Jackson 3 as a
// properties-based creator, which bypasses the no-args constructor and thus every
// @Builder.Default value. Only the builder needs this constructor.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoadConfig {
    private int lx;          // Plaza center
    private int lz;
    private int level;
    private Integer plazaSize;
    private String plazaMaterial;

    @Builder.Default
    private List<RouteDefinition> routes = new ArrayList<>();
}
