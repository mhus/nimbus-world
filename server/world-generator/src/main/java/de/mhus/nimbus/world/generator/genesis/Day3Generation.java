package de.mhus.nimbus.world.generator.genesis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.structures.HexGridStructurePlacerJobExecutor;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parameters:
 *   - "compositionId" (required) - ID of the composition document
 *   - "phases" (optional) - Comma-separated list of phases to execute in order.
 *     Default: "createAll,groundAll,blenderAll,terrainAll,fillerAll,exportAll,imagesAll,compositeImages,waitForChunks"
 *
 *   Example: Skip blender phase:
 *     Map.of("compositionId", "...", "phases", "createAll,groundAll,terrainAll,fillerAll,exportAll,imagesAll,compositeImages")
 *
 *   Example: Only create and ground:
 *     Map.of("compositionId", "...", "phases", "createAll,groundAll")
 *
 *   Available phases:
 *   - "schemaImage" - Create schematic overview image showing biome layout
 *   - "createAll" - Create all hex grids
 *   - "groundAll" - Apply ground manipulation
 *   - "blenderAll" - Apply blender manipulation
 *   - "terrainAll" - Apply terrain manipulation
 *   - "fillerAll" - Apply filler manipulation
 *   - "exportAll" - Export grids to layers
 *   - "imagesAll" - Export individual grid images
 *   - "compositeImages" - Create composite images of entire world
 *   - "importFlats" - (alternative to createAll) Import pre-created flats by flatId instead of creating them in this workflow. Only executes once, not per grid.
 *   - "structuresAll" - Place structures (buildings) for each hex grid based on g_village configuration
 *   - "waitForChunks" - Wait until all dirty chunks for the world have been processed. Only executes once, not per grid.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Day3Generation extends MethodBasedWorkflow {

    private final WDocumentService documentService;
    private final WFlatService flatService;
    private final WHexGridService hexGridService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "genesis-day3-generation";
    }

    @Override
    public Map<String, Object> initialize(String worldId, Map<String, String> params) throws WorkflowException {

        var compositionId = params.get(GenesisConst.COMPOSITION_ID);
        if (Strings.isBlank(compositionId)) {
            throw new WorkflowException(null, "compositionId is required");
        }

        WorldId wid = WorldId.of(worldId).orElseThrow();
        var compositionDoc = documentService.findByDocumentId(wid, compositionId)
                .orElseThrow(() -> new WorkflowException(null, "composition document not found: " + compositionId));

        // Read epoch from composition document
        int epoch = extractEpochFromComposition(compositionDoc.getContent());
        log.info("Composition epoch: {}", epoch);

        // Optional phases parameter - comma-separated list of phases to execute in order
        // Default: all phases (createAll,groundAll,blenderAll,terrainAll,fillerAll,exportAll,imagesAll,compositeImages)
        String phasesParam = params.get(GenesisConst.PHASES);
        String phases;
        if (Strings.isBlank(phasesParam)) {
            phases = GenesisConst.DEFAULT_PHASES;
        } else {
            // Validate all phase names
            List<String> phaseList = Arrays.stream(phasesParam.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (phaseList.isEmpty()) {
                throw new WorkflowException(null, "phases parameter must not be empty");
            }
            phases = String.join(",", phaseList);
            log.info("Custom phases configured: {}", phases);
        }

        return Map.of(
                GenesisConst.COMPOSITION_ID, compositionId,
                GenesisConst.PHASES, phases,
                GenesisConst.EPOCH, epoch
        );
    }

    /**
     * Extract epoch from composition document JSON content.
     * Returns 0 if epoch is not set or cannot be parsed.
     */
    private int extractEpochFromComposition(String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            HexComposition composition = mapper.readValue(content, HexComposition.class);
            return composition.getEpoch();
        } catch (Exception e) {
            log.warn("Failed to extract epoch from composition, defaulting to 0: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.updateWorkflowStatus("generateHexGrids");
        int epoch = (int) context.getParameters().get(GenesisConst.EPOCH);
        // GenerateHexGridFromCompositeJobExecutor
        context.enqueueJob("generator-generate-hexgrid-from-composite", "", Map.of(
                "documentId", (String)context.getParameters().get(GenesisConst.COMPOSITION_ID),
                "epoch", String.valueOf(epoch)
        ));
    }

    @OnSuccess("generateHexGrids")
    public void onGenerateHexGridsSuccess(WorkflowContext context) throws WorkflowException {
        var coordinates = context.getJobResultString("coordinates").orElseThrow();
        log.info("Generated hexgrids with coordinates: {}", coordinates);

        // Parse coordinates (space-separated: "0;0 0;1 1;0")
        List<Day3ProcessingState.HexCoordinate> hexCoordinates = Arrays.stream(coordinates.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(coord -> {
                    String[] parts = coord.split(";");
                    return new Day3ProcessingState.HexCoordinate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                })
                .collect(Collectors.toList());

        if (hexCoordinates.isEmpty()) {
            throw new WorkflowException(null, "No hexgrid coordinates found in result");
        }

        log.info("Parsed {} hexgrid coordinates", hexCoordinates.size());

        // Initialize flatIds list with empty values
        List<String> flatIds = new ArrayList<>(hexCoordinates.size());
        for (int i = 0; i < hexCoordinates.size(); i++) {
            flatIds.add(null);
        }

        // Store processing state - start with first configured phase
        String phases = (String) context.getParameters().get(GenesisConst.PHASES);
        int epoch = (int) context.getParameters().get(GenesisConst.EPOCH);
        String firstPhase = phases.split(",")[0];
        Day3ProcessingState state = Day3ProcessingState.builder()
                .coordinates(hexCoordinates)
                .flatIds(flatIds)
                .currentPhase(firstPhase)
                .currentIndex(0)
                .epoch(epoch)
                .build();
        context.addRecord(state);

        // Start creating first flat
        processNextInPhase(context, state);
    }

    private void processNextInPhase(WorkflowContext context, Day3ProcessingState state) throws WorkflowException {
        String phase = state.getCurrentPhase();
        int index = state.getCurrentIndex();
        int total = state.getCoordinates().size();

        // Get configured phases from workflow parameters
        List<String> phases = Arrays.asList(((String) context.getParameters().get(GenesisConst.PHASES)).split(","));

        // Check if current phase is complete
        if (index >= total) {
            // Find next phase in configured list
            int phaseIndex = phases.indexOf(phase);
            if (phaseIndex < 0 || phaseIndex >= phases.size() - 1) {
                // Last phase or unknown phase - all done
                log.info("All configured phases completed for {} hexgrids (phases: {})", total, phases);
                context.doComplete("Processed " + total + " hexgrids through phases: " + String.join(",", phases));
                return;
            }

            String nextPhase = phases.get(phaseIndex + 1);
            log.info("Phase '{}' completed, moving to phase '{}'", phase, nextPhase);
            state.setCurrentPhase(nextPhase);
            state.setCurrentIndex(0);
            context.addRecord(state);
            processNextInPhase(context, state);
            return;
        }

        // Process current item in current phase
        Day3ProcessingState.HexCoordinate coord = state.getCoordinates().get(index);
        String gridLabel = String.format("Grid %d;%d (%d/%d)",
                coord.getQ(), coord.getR(), index + 1, total);

        log.info("Processing phase '{}' for {}", phase, gridLabel);

        switch (phase) {
            case "schemaImage" -> {
                // Only execute once (at index 0), not for each grid
                if (index == 0) {
                    String compositionId = (String) context.getParameters().get(GenesisConst.COMPOSITION_ID);
                    context.updateWorkflowStatus("createSchemaImage");
                    context.enqueueJob("hex-grid-schema-image", "", "",
                            "Create Schema Image",
                            Map.of(
                                    "compositionId", compositionId
                            ));
                } else {
                    state.setCurrentIndex(total);
                    context.addRecord(state);
                    processNextInPhase(context, state);
                }
            }
            case "createAll" -> {
                String flatId = "genesis_" + state.getEpoch() + "_" + coord.getQ() + "_" + coord.getR();
                // delete if exists
                if (flatService.exists(context.getWorldId(), flatId)) {
                    log.debug("Flat {} already exists for world {}, deleting before creation", flatId, context.getWorldId());
                    flatService.delete(context.getWorldId(), flatId);
                }
                // Store flatId in list for use in later phases
                state.getFlatIds().set(index, flatId);
                context.addRecord(state);
                context.updateWorkflowStatus("createFlat");
                context.enqueueJob("flat-create-hexgrid-empty", "", "",
                        "Create " + gridLabel,
                        Map.of(
                                "layerName", "ground",
                                "hexQ", String.valueOf(coord.getQ()),
                                "hexR", String.valueOf(coord.getR()),
                                "flatId", flatId,
                                "paletteName", "nimbus"
                        ));
            }
            case "importFlats" -> {
                // use this instead of createAll if you want to import pre-created flats instead of generating them in this workflow
                // Only execute once (at index 0), not for each grid
                // Execute directly
                for (int i = index; i < total; i++) {
                    Day3ProcessingState.HexCoordinate c = state.getCoordinates().get(i);
                    String flatId = "genesis_" + state.getEpoch() + "_" + c.getQ() + "_" + c.getR();
                    var flatOpt = flatService.findByWorldAndFlatId(context.getWorldId(), flatId);
                    if (flatOpt == null) {
                        throw new WorkflowException(null, "Error checking flat existence: flatService returned null for world " + context.getWorldId() + " and flatId " + flatId);
                    }
                    state.getFlatIds().set(i, flatId);
                }

                // Skip remaining indices - composite images only created once
                state.setCurrentIndex(total);
                context.addRecord(state);
                processNextInPhase(context, state);
            }
            case "groundAll" -> {
                String flatId = state.getFlatIds().get(index);
                context.updateWorkflowStatus("manipulateGround");
                context.enqueueJob("flat-manipulate", "hex-grid", "",
                        "GROUND for " + gridLabel,
                        Map.of(
                                "flatId", flatId,
                                "step", "GROUND"
                        ));
            }
            case "blenderAll" -> {
                String flatId = state.getFlatIds().get(index);
                context.updateWorkflowStatus("manipulateBlender");
                context.enqueueJob("flat-manipulate", "hex-grid", "",
                        "BLENDER for " + gridLabel,
                        Map.of(
                                "flatId", flatId,
                                "step", "BLENDER"
                        ));
            }
            case "terrainAll" -> {
                String flatId = state.getFlatIds().get(index);
                context.updateWorkflowStatus("manipulateTerrain");
                context.enqueueJob("flat-manipulate", "hex-grid", "",
                        "TERRAIN for " + gridLabel,
                        Map.of(
                                "flatId", flatId,
                                "step", "TERRAIN"
                        ));
            }
            case "fillerAll" -> {
                String flatId = state.getFlatIds().get(index);
                context.updateWorkflowStatus("manipulateFiller");
                context.enqueueJob("flat-manipulate", "hex-grid", "",
                        "FILLER for " + gridLabel,
                        Map.of(
                                "flatId", flatId,
                                "step", "FILLER"
                        ));
            }
            case "exportAll" -> {
                String flatId = state.getFlatIds().get(index);
                context.updateWorkflowStatus("exportFlat");
                context.enqueueJob("flat-export", "", "",
                        "Export to Layer " + gridLabel,
                        Map.of(
                                "flatId", flatId,
                                "deleteAfterExport", "false"
                        ));
            }
            case "imagesAll" -> {
                String flatId = state.getFlatIds().get(index);
                String levelPath = String.format("map/%d_%d/%d_level.png", coord.getQ(), coord.getR(), state.getEpoch());
                String materialPath = String.format("map/%d_%d/%d_material.png", coord.getQ(), coord.getR(), state.getEpoch());
                context.updateWorkflowStatus("exportImages");
                context.enqueueJob("flat-export-images", "", "",
                        "Export Images " + gridLabel,
                        Map.of(
                                "flatId", flatId,
                                "levelPath", levelPath,
                                "materialPath", materialPath,
                                "ignoreEmptyMaterial", "false"
                        ));
            }
            case "compositeImages" -> {
                // Only execute once (at index 0), not for each grid
                if (index == 0) {
                    String compositionId = (String) context.getParameters().get(GenesisConst.COMPOSITION_ID);
                    context.updateWorkflowStatus("createCompositeImages");
                    context.enqueueJob("hex-grid-composite-image", "", "",
                            "Create Composite Images",
                            Map.of(
                                    "compositionId", compositionId,
                                    "flatIdSuffix", "genesis_" + state.getEpoch() + "_",
                                    "drawGridLines", "false"
                            ));
                } else {
                    // Skip remaining indices - composite images only created once
                    state.setCurrentIndex(total);
                    context.addRecord(state);
                    processNextInPhase(context, state);
                }
            }
            case "structuresAll" -> {
                // Only place structures for grids that have g_village defined
                var hexPos = HexVector2.builder().q(coord.getQ()).r(coord.getR()).build();
                var hexGridOpt = hexGridService.findByWorldIdAndPosition(context.getWorldId(), hexPos);
                if (hexGridOpt.isPresent() && !Strings.isBlank(hexGridOpt.get().getParameters().get("g_village"))) {
                    context.updateWorkflowStatus("placeStructures");
                    context.enqueueJob(
                            HexGridStructurePlacerJobExecutor.EXECUTOR_NAME, "", "",
                            "Structures for " + gridLabel,
                            Map.of(
                                    "hexQ", String.valueOf(coord.getQ()),
                                    "hexR", String.valueOf(coord.getR()),
                                    "epoch", String.valueOf(state.getEpoch())
                            ));
                } else {
                    log.info("Skipping structures for {} - no g_village defined", gridLabel);
                    state.setCurrentIndex(index + 1);
                    context.addRecord(state);
                    processNextInPhase(context, state);
                }
            }
            case "waitForChunks" -> {
                // Only execute once (at index 0), not for each grid
                if (index == 0) {
                    context.updateWorkflowStatus("waitForDirtyChunks");
                    context.enqueueJob(
                            WaitForDirtyChunksJobExecutor.EXECUTOR_NAME,
                            "", null,
                            "Wait for Dirty Chunks",
                            Map.of()
                    );
                } else {
                    state.setCurrentIndex(total);
                    context.addRecord(state);
                    processNextInPhase(context, state);
                }
            }
            default -> throw new WorkflowException(null, "Unknown phase: " + phase);
        }
    }

    @OnSuccess("createSchemaImage")
    public void onCreateSchemaImageSuccess(WorkflowContext context) throws WorkflowException {
        log.info("Schema image created successfully");
        advanceToNextInPhase(context);
    }

    @OnSuccess("createFlat")
    public void onCreateFlatSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("manipulateGround")
    public void onManipulateGroundSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("manipulateBlender")
    public void onManipulateBlenderSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("manipulateTerrain")
    public void onManipulateTerrainSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("manipulateFiller")
    public void onManipulateFillerSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("exportFlat")
    public void onExportFlatSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("exportImages")
    public void onExportImagesSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("createCompositeImages")
    public void onCreateCompositeImagesSuccess(WorkflowContext context) throws WorkflowException {
        log.info("Composite images created successfully");
        advanceToNextInPhase(context);
    }

    @OnSuccess("placeStructures")
    public void onPlaceStructuresSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("waitForDirtyChunks")
    public void onWaitForDirtyChunksSuccess(WorkflowContext context) throws WorkflowException {
        log.info("All dirty chunks cleared");
        advanceToNextInPhase(context);
    }

    private void advanceToNextInPhase(WorkflowContext context) throws WorkflowException {
        Day3ProcessingState state = getProcessingState(context);
        log.info("Completed {} for index {}/{} in phase '{}'",
                state.getCurrentPhase(), state.getCurrentIndex() + 1, state.getCoordinates().size(), state.getCurrentPhase());

        // Move to next index
        state.setCurrentIndex(state.getCurrentIndex() + 1);
        context.addRecord(state);

        // Process next in phase (or move to next phase if current is complete)
        processNextInPhase(context, state);
    }

    private Day3ProcessingState getProcessingState(WorkflowContext context) throws WorkflowException {
        return context.getLastJournalRecord(Day3ProcessingState.class)
                .orElseThrow(() -> new WorkflowException(null, "Processing state not found in journal"));
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}

