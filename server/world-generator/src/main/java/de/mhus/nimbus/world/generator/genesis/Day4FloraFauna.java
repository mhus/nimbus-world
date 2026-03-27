package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.fauna.HexGridFaunaGeneratorJobExecutor;
import de.mhus.nimbus.world.generator.flora.HexGridFloraGeneratorJobExecutor;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Day 4 workflow: Flora (and later Fauna) generation for all hex grids.
 *
 * Parameters:
 *   - "compositionId" (required) - ID of the composition document
 *   - "phases" (optional) - Comma-separated list of phases to execute in order.
 *     Default: "floraAll,faunaAll,waitForChunks"
 *
 *   Available phases:
 *   - "floraAll" - Generate flora for each hex grid
 *   - "faunaAll" - Generate fauna for each hex grid
 *   - "waitForChunks" - Wait until all dirty chunks have been processed
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Day4FloraFauna extends MethodBasedWorkflow {

    private static final String DEFAULT_PHASES = "floraAll,faunaAll,waitForChunks";

    private final WDocumentService documentService;

    @Override
    public String name() {
        return "genesis-day4-flora-fauna";
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

        String phasesParam = params.get(GenesisConst.PHASES);
        String phases;
        if (Strings.isBlank(phasesParam)) {
            phases = DEFAULT_PHASES;
        } else {
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

        String epochStr = params.getOrDefault(GenesisConst.EPOCH, "0");

        return Map.of(
                GenesisConst.COMPOSITION_ID, compositionId,
                GenesisConst.PHASES, phases,
                GenesisConst.EPOCH, epochStr
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.updateWorkflowStatus("loadModel");
        String epoch = String.valueOf(context.getParameters().get(GenesisConst.EPOCH));
        context.enqueueJob("generator-generate-hexgrid-from-composite", "", Map.of(
                "documentId", (String) context.getParameters().get(GenesisConst.COMPOSITION_ID),
                "epoch", epoch
        ));
    }

    @OnSuccess("loadModel")
    public void onLoadModelSuccess(WorkflowContext context) throws WorkflowException {
        var coordinates = context.getJobResultString("coordinates").orElseThrow();
        log.info("Loaded model with coordinates: {}", coordinates);

        List<Day4ProcessingState.HexCoordinate> hexCoordinates = Arrays.stream(coordinates.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(coord -> {
                    String[] parts = coord.split(";");
                    return new Day4ProcessingState.HexCoordinate(
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                })
                .collect(Collectors.toList());

        if (hexCoordinates.isEmpty()) {
            throw new WorkflowException(null, "No hexgrid coordinates found in result");
        }

        log.info("Parsed {} hexgrid coordinates for flora/fauna generation", hexCoordinates.size());

        String phases = (String) context.getParameters().get(GenesisConst.PHASES);
        String firstPhase = phases.split(",")[0];
        Day4ProcessingState state = Day4ProcessingState.builder()
                .coordinates(hexCoordinates)
                .currentPhase(firstPhase)
                .currentIndex(0)
                .build();
        context.addRecord(state);

        processNextInPhase(context, state);
    }

    private void processNextInPhase(WorkflowContext context, Day4ProcessingState state) throws WorkflowException {
        String phase = state.getCurrentPhase();
        int index = state.getCurrentIndex();
        int total = state.getCoordinates().size();

        List<String> phases = Arrays.asList(
                ((String) context.getParameters().get(GenesisConst.PHASES)).split(","));

        if (index >= total) {
            int phaseIndex = phases.indexOf(phase);
            if (phaseIndex < 0 || phaseIndex >= phases.size() - 1) {
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

        Day4ProcessingState.HexCoordinate coord = state.getCoordinates().get(index);
        String gridLabel = String.format("Grid %d;%d (%d/%d)",
                coord.getQ(), coord.getR(), index + 1, total);

        log.info("Processing phase '{}' for {}", phase, gridLabel);

        switch (phase) {
            case "floraAll" -> {
                context.updateWorkflowStatus("generateFlora");
                context.enqueueJob(
                        HexGridFloraGeneratorJobExecutor.EXECUTOR_NAME, "", "",
                        "Flora for " + gridLabel,
                        Map.of(
                                "hexQ", String.valueOf(coord.getQ()),
                                "hexR", String.valueOf(coord.getR())
                        ));
            }
            case "faunaAll" -> {
                context.updateWorkflowStatus("generateFauna");
                context.enqueueJob(
                        HexGridFaunaGeneratorJobExecutor.EXECUTOR_NAME, "", "",
                        "Fauna for " + gridLabel,
                        Map.of(
                                "hexQ", String.valueOf(coord.getQ()),
                                "hexR", String.valueOf(coord.getR())
                        ));
            }
            case "waitForChunks" -> {
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

    @OnSuccess("generateFlora")
    public void onGenerateFloraSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("generateFauna")
    public void onGenerateFaunaSuccess(WorkflowContext context) throws WorkflowException {
        advanceToNextInPhase(context);
    }

    @OnSuccess("waitForDirtyChunks")
    public void onWaitForDirtyChunksSuccess(WorkflowContext context) throws WorkflowException {
        log.info("All dirty chunks cleared");
        advanceToNextInPhase(context);
    }

    private void advanceToNextInPhase(WorkflowContext context) throws WorkflowException {
        Day4ProcessingState state = getProcessingState(context);
        log.info("Completed index {}/{} in phase '{}'",
                state.getCurrentIndex() + 1, state.getCoordinates().size(), state.getCurrentPhase());

        state.setCurrentIndex(state.getCurrentIndex() + 1);
        context.addRecord(state);

        processNextInPhase(context, state);
    }

    private Day4ProcessingState getProcessingState(WorkflowContext context) throws WorkflowException {
        return context.getLastJournalRecord(Day4ProcessingState.class)
                .orElseThrow(() -> new WorkflowException(null, "Processing state not found in journal"));
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}
