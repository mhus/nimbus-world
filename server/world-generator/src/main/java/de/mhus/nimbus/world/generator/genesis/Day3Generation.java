package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WDocumentService;
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
 *     Default: "createAll,groundAll,blenderAll,terrainAll,fillerAll,exportAll,imagesAll,compositeImages"
 *
 *   Example: Skip blender phase:
 *     Map.of("compositionId", "...", "phases", "createAll,groundAll,terrainAll,fillerAll,exportAll,imagesAll,compositeImages")
 *
 *   Example: Only create and ground:
 *     Map.of("compositionId", "...", "phases", "createAll,groundAll")
 *
 *   Available phases:
 *   - "createAll" - Create all hex grids
 *   - "groundAll" - Apply ground manipulation
 *   - "blenderAll" - Apply blender manipulation
 *   - "terrainAll" - Apply terrain manipulation
 *   - "fillerAll" - Apply filler manipulation
 *   - "exportAll" - Export grids to layers
 *   - "imagesAll" - Export individual grid images
 *   - "compositeImages" - Create composite images of entire world
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Day3Generation extends MethodBasedWorkflow {

    private final WDocumentService documentService;
    private final WFlatService flatService;

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
        if (documentService.findByDocumentId(wid, compositionId).isEmpty()) {
            throw new WorkflowException(null, "composition document not found: " + compositionId);
        }

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
            for (String phaseName : phaseList) {
                if (Day3Phase.fromPhaseName(phaseName) == null) {
                    throw new WorkflowException(null, "Unknown phase: " + phaseName
                            + ". Valid phases: " + GenesisConst.DEFAULT_PHASES);
                }
            }
            if (phaseList.isEmpty()) {
                throw new WorkflowException(null, "phases parameter must not be empty");
            }
            phases = String.join(",", phaseList);
            log.info("Custom phases configured: {}", phases);
        }

        return Map.of(
                GenesisConst.COMPOSITION_ID, compositionId,
                GenesisConst.PHASES, phases
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.updateWorkflowStatus("generateHexGrids");
        // GenerateHexGridFromCompositeJobExecutor
        context.enqueueJob("generator-generate-hexgrid-from-composite", "", Map.of(
                "documentId", (String)context.getParameters().get(GenesisConst.COMPOSITION_ID)
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
        String firstPhase = phases.split(",")[0];
        Day3ProcessingState state = Day3ProcessingState.builder()
                .coordinates(hexCoordinates)
                .flatIds(flatIds)
                .currentPhase(firstPhase)
                .currentIndex(0)
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
            case "createAll" -> {
                String flatId = "genesis_" + coord.getQ() + "_" + coord.getR();
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
                String levelPath = String.format("map/%d_%d/level.png", coord.getQ(), coord.getR());
                String materialPath = String.format("map/%d_%d/material.png", coord.getQ(), coord.getR());
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
                                    "flatIdSuffix", "genesis_",
                                    "drawGridLines", "false"
                            ));
                } else {
                    // Skip remaining indices - composite images only created once
                    state.setCurrentIndex(total);
                    context.addRecord(state);
                    processNextInPhase(context, state);
                }
            }
            default -> throw new WorkflowException(null, "Unknown phase: " + phase);
        }
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

