package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepares the HexComposition model by converting abstract values (enums, words)
 * into concrete technical values (numbers, ranges).
 *
 * This class fills the model with from-to values that are not exactly defined
 * in the original composition, making them ready for the actual composition algorithm.
 */
@Slf4j
public class HexCompositionPreparer {

    /**
     * Prepares a HexComposition by converting all abstract values to concrete ranges.
     */
    public PreparedHexComposition prepare(HexComposition composition) {
        if (composition == null) {
            throw new IllegalArgumentException("HexComposition cannot be null");
        }

        log.debug("Preparing HexComposition: {}", composition.getName());

        PreparedHexComposition prepared = new PreparedHexComposition();
        prepared.setOriginal(composition);
        prepared.setBiomes(prepareBiomes(composition.getBiomes()));
        prepared.setVillages(prepareVillages(composition.getVillages()));

        log.debug("Prepared {} biomes and {} villages",
            prepared.getBiomes() != null ? prepared.getBiomes().size() : 0,
            prepared.getVillages() != null ? prepared.getVillages().size() : 0);

        return prepared;
    }

    private List<PreparedBiome> prepareBiomes(List<BiomeDefinition> biomes) {
        if (biomes == null || biomes.isEmpty()) {
            return new ArrayList<>();
        }

        List<PreparedBiome> prepared = new ArrayList<>();
        for (BiomeDefinition biome : biomes) {
            prepared.add(prepareBiome(biome));
        }
        return prepared;
    }

    private PreparedBiome prepareBiome(BiomeDefinition biome) {
        PreparedBiome prepared = new PreparedBiome();
        prepared.setOriginal(biome);
        prepared.setName(biome.getName());
        prepared.setType(biome.getType());
        prepared.setShape(biome.getShape());

        // Prepare size range
        prepared.setSizeFrom(biome.getEffectiveSizeFrom());
        prepared.setSizeTo(biome.getEffectiveSizeTo());

        // Prepare positions
        prepared.setPositions(preparePositions(biome.getRelativePositions()));

        // Copy parameters
        prepared.setParameters(biome.getParameters() != null ? new HashMap<>(biome.getParameters()) : new HashMap<>());

        log.debug("Prepared biome '{}': type={}, shape={}, size={}-{}, positions={}",
            prepared.getName(), prepared.getType(), prepared.getShape(),
            prepared.getSizeFrom(), prepared.getSizeTo(),
            prepared.getPositions() != null ? prepared.getPositions().size() : 0);

        return prepared;
    }

    private List<PreparedVillage> prepareVillages(List<VillageDefinition> villages) {
        if (villages == null || villages.isEmpty()) {
            return new ArrayList<>();
        }

        List<PreparedVillage> prepared = new ArrayList<>();
        for (VillageDefinition village : villages) {
            prepared.add(prepareVillage(village));
        }
        return prepared;
    }

    private PreparedVillage prepareVillage(VillageDefinition village) {
        PreparedVillage prepared = new PreparedVillage();
        prepared.setOriginal(village);
        prepared.setName(village.getName());
        prepared.setType(village.getType());
        prepared.setShape(village.getShape());

        // Prepare positions
        prepared.setPositions(preparePositions(village.getRelativePositions()));

        // Copy building and street definitions
        prepared.setBuildings(village.getBuildings() != null ? new ArrayList<>(village.getBuildings()) : new ArrayList<>());
        prepared.setStreets(village.getStreets() != null ? new ArrayList<>(village.getStreets()) : new ArrayList<>());

        // Copy parameters
        prepared.setParameters(village.getParameters() != null ? new HashMap<>(village.getParameters()) : new HashMap<>());

        log.debug("Prepared village '{}': type={}, shape={}, buildings={}, streets={}, positions={}",
            prepared.getName(), prepared.getType(), prepared.getShape(),
            prepared.getBuildings().size(), prepared.getStreets().size(),
            prepared.getPositions() != null ? prepared.getPositions().size() : 0);

        return prepared;
    }

    private List<PreparedPosition> preparePositions(List<RelativePosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return new ArrayList<>();
        }

        List<PreparedPosition> prepared = new ArrayList<>();
        for (RelativePosition position : positions) {
            prepared.add(preparePosition(position));
        }
        return prepared;
    }

    private PreparedPosition preparePosition(RelativePosition position) {
        PreparedPosition prepared = new PreparedPosition();
        prepared.setOriginal(position);

        // Convert direction to angle in degrees (0° = N, 60° = NE, 120° = E, etc.)
        prepared.setDirectionAngle(convertDirectionToAngle(position.getDirection()));
        prepared.setDirection(position.getDirection());

        // Prepare distance range
        prepared.setDistanceFrom(position.getEffectiveDistanceFrom());
        prepared.setDistanceTo(position.getEffectiveDistanceTo());

        // Copy other values
        prepared.setAnchor(position.getAnchor());
        prepared.setPriority(position.getPriority());

        log.trace("Prepared position: direction={} ({}°), distance={}-{}, anchor={}, priority={}",
            prepared.getDirection(), prepared.getDirectionAngle(),
            prepared.getDistanceFrom(), prepared.getDistanceTo(),
            prepared.getAnchor() != null ? prepared.getAnchor() : "origin",
            prepared.getPriority());

        return prepared;
    }

    /**
     * Converts a Direction enum to an angle in degrees.
     * Hex grid directions: N=0°, NE=60°, E=120°, SE=180°, S=240°, SW=300°, W=360°/0°, NW=420°/60°
     */
    private int convertDirectionToAngle(Direction direction) {
        if (direction == null) {
            return 0;
        }

        return switch (direction) {
            case N -> 0;
            case NE -> 60;
            case E -> 120;
            case SE -> 180;
            case S -> 240;
            case SW -> 300;
            case W -> 0;   // W is opposite of E in hex, but we'll use 0 and handle in algorithm
            case NW -> 60;  // NW is opposite of SE
        };
    }
}
