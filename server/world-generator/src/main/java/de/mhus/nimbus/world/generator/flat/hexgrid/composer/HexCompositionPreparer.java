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

        // Ensure composition is initialized (triggers migration if needed)
        composition.initialize();

        log.debug("Preparing HexComposition: {}", composition.getName());

        if (composition.getFeatures() == null || composition.getFeatures().isEmpty()) {
            throw new IllegalStateException("HexComposition has no features. " +
                "Legacy biomes/villages should have been migrated during initialize().");
        }

        PreparedHexComposition prepared = new PreparedHexComposition();
        prepared.setOriginal(composition);

        // Feature-based preparation
        prepared.setPreparedFeatures(prepareFeatures(composition.getFeatures()));
        log.debug("Prepared {} features", prepared.getPreparedFeatures().size());

        return prepared;
    }

    /**
     * Prepares a list of features.
     */
    private List<PreparedFeature> prepareFeatures(List<Feature> features) {
        if (features == null || features.isEmpty()) {
            return new ArrayList<>();
        }

        List<PreparedFeature> prepared = new ArrayList<>();
        for (Feature feature : features) {
            PreparedFeature preparedFeature = prepareFeature(feature);
            if (preparedFeature != null) {
                prepared.add(preparedFeature);
            }
        }
        return prepared;
    }

    /**
     * Prepares a single feature based on its type.
     */
    private PreparedFeature prepareFeature(Feature feature) {
        if (feature == null) {
            return null;
        }

        return switch (feature) {
            case Biome biome -> prepareBiomeFromFeature(biome);
            case Village village -> prepareVillageFromFeature(village);
            case Town town -> prepareTownFromFeature(town);
            case Composite composite -> prepareCompositeFromFeature(composite);
            case Road road -> prepareRoadFromFeature(road);
            case River river -> prepareRiverFromFeature(river);
            case Wall wall -> prepareWallFromFeature(wall);
            default -> {
                log.warn("Unknown feature type: {}", feature.getClass().getName());
                yield null;
            }
        };
    }

    /**
     * Prepare a Biome feature (new architecture).
     */
    private PreparedBiome prepareBiomeFromFeature(Biome biome) {
        PreparedBiome prepared = new PreparedBiome();
        prepared.setOriginal(biome);
        prepared.setFeatureId(biome.getFeatureId());
        prepared.setName(biome.getName());
        prepared.setTitle(biome.getTitle());
        prepared.setType(biome.getType());
        prepared.setShape(biome.getShape());

        // Prepare size range
        prepared.setSizeFrom(biome.getEffectiveSizeFrom());
        prepared.setSizeTo(biome.getEffectiveSizeTo());

        // Prepare positions
        prepared.setPositions(preparePositions(biome.getPositions()));

        // Copy parameters
        prepared.setParameters(biome.getParameters() != null ? new HashMap<>(biome.getParameters()) : new HashMap<>());

        log.debug("Prepared biome feature '{}': type={}, shape={}, size={}-{}, positions={}",
            prepared.getName(), prepared.getType(), prepared.getShape(),
            prepared.getSizeFrom(), prepared.getSizeTo(),
            prepared.getPositions() != null ? prepared.getPositions().size() : 0);

        return prepared;
    }

    /**
     * Prepare a Village feature (new architecture).
     */
    private PreparedVillage prepareVillageFromFeature(Village village) {
        PreparedVillage prepared = new PreparedVillage();
        prepared.setOriginal(village);
        prepared.setFeatureId(village.getFeatureId());
        prepared.setName(village.getName());
        prepared.setTitle(village.getTitle());
        prepared.setShape(village.getShape());

        // Prepare size range
        prepared.setSizeFrom(village.getEffectiveSizeFrom());
        prepared.setSizeTo(village.getEffectiveSizeTo());

        // Prepare positions
        prepared.setPositions(preparePositions(village.getPositions()));

        // Copy building and street definitions
        prepared.setBuildings(village.getBuildings() != null ? new ArrayList<>(village.getBuildings()) : new ArrayList<>());
        prepared.setStreets(village.getStreets() != null ? new ArrayList<>(village.getStreets()) : new ArrayList<>());

        // Copy calculated dimensions
        prepared.setCalculatedHexGridWidth(village.getCalculatedHexGridWidth() != null ? village.getCalculatedHexGridWidth() : 0);
        prepared.setCalculatedHexGridHeight(village.getCalculatedHexGridHeight() != null ? village.getCalculatedHexGridHeight() : 0);

        // Copy parameters
        prepared.setParameters(village.getParameters() != null ? new HashMap<>(village.getParameters()) : new HashMap<>());

        log.debug("Prepared village feature '{}': shape={}, size={}-{}, buildings={}, streets={}, positions={}",
            prepared.getName(), prepared.getShape(),
            prepared.getSizeFrom(), prepared.getSizeTo(),
            prepared.getBuildings().size(), prepared.getStreets().size(),
            prepared.getPositions() != null ? prepared.getPositions().size() : 0);

        return prepared;
    }

    /**
     * Prepare a Town feature.
     */
    private PreparedTown prepareTownFromFeature(Town town) {
        PreparedTown prepared = new PreparedTown();
        prepared.setOriginal(town);
        prepared.setFeatureId(town.getFeatureId());
        prepared.setName(town.getName());
        prepared.setTitle(town.getTitle());
        prepared.setShape(town.getShape());

        // Prepare size range
        prepared.setSizeFrom(town.getEffectiveSizeFrom());
        prepared.setSizeTo(town.getEffectiveSizeTo());

        // Prepare positions
        prepared.setPositions(preparePositions(town.getPositions()));

        // Copy building and street definitions
        prepared.setBuildings(town.getBuildings() != null ? new ArrayList<>(town.getBuildings()) : new ArrayList<>());
        prepared.setStreets(town.getStreets() != null ? new ArrayList<>(town.getStreets()) : new ArrayList<>());

        // Copy calculated dimensions
        prepared.setCalculatedHexGridWidth(town.getCalculatedHexGridWidth() != null ? town.getCalculatedHexGridWidth() : 0);
        prepared.setCalculatedHexGridHeight(town.getCalculatedHexGridHeight() != null ? town.getCalculatedHexGridHeight() : 0);

        // Copy parameters
        prepared.setParameters(town.getParameters() != null ? new HashMap<>(town.getParameters()) : new HashMap<>());

        log.debug("Prepared town feature '{}': shape={}, size={}-{}, buildings={}, streets={}",
            prepared.getName(), prepared.getShape(),
            prepared.getSizeFrom(), prepared.getSizeTo(),
            prepared.getBuildings().size(), prepared.getStreets().size());

        return prepared;
    }

    /**
     * Prepare a Composite feature.
     */
    private PreparedComposite prepareCompositeFromFeature(Composite composite) {
        PreparedComposite prepared = new PreparedComposite();
        prepared.setOriginal(composite);
        prepared.setFeatureId(composite.getFeatureId());
        prepared.setName(composite.getName());
        prepared.setTitle(composite.getTitle());
        prepared.setShape(composite.getShape());

        // Prepare size range
        prepared.setSizeFrom(composite.getEffectiveSizeFrom());
        prepared.setSizeTo(composite.getEffectiveSizeTo());

        // Prepare positions
        prepared.setPositions(preparePositions(composite.getPositions()));

        // Recursively prepare nested features
        prepared.setPreparedFeatures(prepareFeatures(composite.getFeatures()));

        log.debug("Prepared composite feature '{}': shape={}, size={}-{}, nested features={}",
            prepared.getName(), prepared.getShape(),
            prepared.getSizeFrom(), prepared.getSizeTo(),
            prepared.getPreparedFeatures().size());

        return prepared;
    }

    /**
     * Prepare a Road feature.
     */
    private PreparedRoad prepareRoadFromFeature(Road road) {
        PreparedRoad prepared = new PreparedRoad();
        prepared.setOriginal(road);
        prepared.setFeatureId(road.getFeatureId());
        prepared.setName(road.getName());
        prepared.setTitle(road.getTitle());
        prepared.setFlowType(FlowType.ROAD);
        prepared.setStartPointId(road.getStartPointId());
        prepared.setWidthBlocks(road.getEffectiveWidthBlocks());

        // Copy road-specific fields
        prepared.setWaypointIds(road.getWaypointIds() != null ? new ArrayList<>(road.getWaypointIds()) : new ArrayList<>());
        prepared.setEndPointId(road.getEndPointId());
        prepared.setRoadType(road.getRoadType());
        prepared.setLevel(road.getLevel());

        log.debug("Prepared road feature '{}': width={}, waypoints={}, roadType={}",
            prepared.getName(), prepared.getWidthBlocks(),
            prepared.getWaypointIds().size(), prepared.getRoadType());

        return prepared;
    }

    /**
     * Prepare a River feature.
     */
    private PreparedRiver prepareRiverFromFeature(River river) {
        PreparedRiver prepared = new PreparedRiver();
        prepared.setOriginal(river);
        prepared.setFeatureId(river.getFeatureId());
        prepared.setName(river.getName());
        prepared.setTitle(river.getTitle());
        prepared.setFlowType(FlowType.RIVER);
        prepared.setStartPointId(river.getStartPointId());
        prepared.setWidthBlocks(river.getEffectiveWidthBlocks());

        // Copy river-specific fields
        prepared.setWaypointIds(river.getWaypointIds() != null ? new ArrayList<>(river.getWaypointIds()) : new ArrayList<>());
        prepared.setMergeToId(river.getMergeToId());
        prepared.setDepth(river.getDepth());
        prepared.setLevel(river.getLevel());

        log.debug("Prepared river feature '{}': width={}, waypoints={}, depth={}",
            prepared.getName(), prepared.getWidthBlocks(),
            prepared.getWaypointIds().size(), prepared.getDepth());

        return prepared;
    }

    /**
     * Prepare a Wall feature.
     */
    private PreparedWall prepareWallFromFeature(Wall wall) {
        PreparedWall prepared = new PreparedWall();
        prepared.setOriginal(wall);
        prepared.setFeatureId(wall.getFeatureId());
        prepared.setName(wall.getName());
        prepared.setTitle(wall.getTitle());
        prepared.setFlowType(FlowType.WALL);
        prepared.setStartPointId(wall.getStartPointId());
        prepared.setWidthBlocks(wall.getEffectiveWidthBlocks());

        // Copy wall-specific fields
        prepared.setWaypointIds(wall.getWaypointIds() != null ? new ArrayList<>(wall.getWaypointIds()) : new ArrayList<>());
        prepared.setEndPointId(wall.getEndPointId());
        prepared.setHeight(wall.getHeight());
        prepared.setMaterial(wall.getMaterial());

        log.debug("Prepared wall feature '{}': width={}, waypoints={}, height={}, material={}",
            prepared.getName(), prepared.getWidthBlocks(),
            prepared.getWaypointIds().size(), prepared.getHeight(), prepared.getMaterial());

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
