package de.mhus.nimbus.world.generator.composer.area;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.flow.Flow;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
// PRIVATE on purpose: a public all-args constructor is picked up by Jackson 3 as a
// properties-based creator, which bypasses the no-args constructor and thus every
// @Builder.Default value. Only the builder needs this constructor.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Composite extends Area {
    @Builder.Default
    private List<Feature> features = new ArrayList<>();

    public List<Area> getAreas() {
        return features.stream()
            .filter(f -> f instanceof Area)
            .map(f -> (Area) f)
            .collect(Collectors.toList());
    }

    public List<Flow> getFlows() {
        return features.stream()
            .filter(f -> f instanceof Flow)
            .map(f -> (Flow) f)
            .collect(Collectors.toList());
    }

    public static CompositeBuilder builder() {
        return new CompositeBuilder();
    }
}
