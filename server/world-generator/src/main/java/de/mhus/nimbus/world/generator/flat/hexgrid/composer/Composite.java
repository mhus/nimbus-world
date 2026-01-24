package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@AllArgsConstructor
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
