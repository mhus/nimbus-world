package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.weather.HexGridWeatherGeneratorJobExecutor;
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
 * Day 5 workflow: Environment generation (weather configuration) for all hex grids.
 *
 * Generates weather descriptors based on biome type for each hex grid.
 * Weather descriptors are stored in WHexGrid.parameters with the key "w_{epoch}".
 *
 * If a hex grid already has an explicit weather descriptor (e.g., from model definition),
 * it is NOT overwritten.
 *
 * Parameters:
 *   - "compositionId" (required) - ID of the composition document
 *   - "epoch" (optional) - Epoch number to generate for (default: 0)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Day5Environment extends MethodBasedWorkflow {

    private final WDocumentService documentService;

    @Override
    public String name() {
        return "genesis-day5-environment";
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

        String epochStr = params.getOrDefault(GenesisConst.EPOCH, "0");

        return Map.of(
                GenesisConst.COMPOSITION_ID, compositionId,
                GenesisConst.EPOCH, epochStr
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        // Load hex grid coordinates from composition
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

        List<Day5ProcessingState.HexCoordinate> hexCoordinates = Arrays.stream(coordinates.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(coord -> {
                    String[] parts = coord.split(";");
                    return new Day5ProcessingState.HexCoordinate(
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                })
                .collect(Collectors.toList());

        if (hexCoordinates.isEmpty()) {
            throw new WorkflowException(null, "No hexgrid coordinates found in result");
        }

        log.info("Generating weather for {} hex grids", hexCoordinates.size());

        Day5ProcessingState state = Day5ProcessingState.builder()
                .coordinates(hexCoordinates)
                .currentIndex(0)
                .build();
        context.addRecord(state);

        processNext(context, state);
    }

    private void processNext(WorkflowContext context, Day5ProcessingState state) throws WorkflowException {
        int index = state.getCurrentIndex();
        int total = state.getCoordinates().size();

        if (index >= total) {
            log.info("Weather generation completed for {} hex grids", total);
            context.doComplete("Generated weather for " + total + " hex grids");
            return;
        }

        Day5ProcessingState.HexCoordinate coord = state.getCoordinates().get(index);
        String epoch = (String) context.getParameters().get("epoch");
        String gridLabel = String.format("Grid %d;%d (%d/%d)",
                coord.getQ(), coord.getR(), index + 1, total);

        log.info("Generating weather for {}", gridLabel);

        context.updateWorkflowStatus("generateWeather");
        context.enqueueJob(
                HexGridWeatherGeneratorJobExecutor.EXECUTOR_NAME, "", "",
                "Weather for " + gridLabel,
                Map.of(
                        "hexQ", String.valueOf(coord.getQ()),
                        "hexR", String.valueOf(coord.getR()),
                        "epoch", epoch
                ));
    }

    @OnSuccess("generateWeather")
    public void onGenerateWeatherSuccess(WorkflowContext context) throws WorkflowException {
        Day5ProcessingState state = context.getLastJournalRecord(Day5ProcessingState.class)
                .orElseThrow(() -> new WorkflowException(null, "Processing state not found"));

        log.info("Completed weather {}/{}", state.getCurrentIndex() + 1, state.getCoordinates().size());

        state.setCurrentIndex(state.getCurrentIndex() + 1);
        context.addRecord(state);

        processNext(context, state);
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}
