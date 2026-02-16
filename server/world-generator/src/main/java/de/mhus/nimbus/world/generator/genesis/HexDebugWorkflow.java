package de.mhus.nimbus.world.generator.genesis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.workflow.WorkflowJobExecutor;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Debug workflow that creates a composed document with 7 hex grids
 * (1 center + 6 neighbors) using the DebugBuilder, then runs
 * Day3Generation to generate and export them.
 * <p>
 * Useful for verifying hex creation, alignment and export correctness.
 * <p>
 * Parameters:
 * - coordinate: Start hex coordinate in "q;r" format (default: "0;0")
 * - targetPhase: Optional Day3 target phase (default: compositeImages)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HexDebugWorkflow extends MethodBasedWorkflow {

    private static final String COMPOSED_COLLECTION = "generator_composed";

    private final WDocumentService documentService;
    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "hex-debug";
    }

    @Override
    public Map<String, Object> initialize(String worldId, Map<String, String> params) throws WorkflowException {
        String coordinate = params.getOrDefault("coordinate", "0;0");
        String[] parts = coordinate.split(";");
        if (parts.length != 2) {
            throw new WorkflowException(null, "Invalid coordinate format, expected 'q;r': " + coordinate);
        }
        try {
            Integer.parseInt(parts[0].trim());
            Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new WorkflowException(null, "Invalid coordinate values: " + coordinate);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("coordinate", coordinate);
        if (params.containsKey(GenesisConst.TARGET_PHASE)) {
            result.put(GenesisConst.TARGET_PHASE, params.get(GenesisConst.TARGET_PHASE));
        }
        return result;
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        String coordinate = (String) context.getParameters().getOrDefault("coordinate", "0;0");
        String[] parts = coordinate.split(";");
        int centerQ = Integer.parseInt(parts[0].trim());
        int centerR = Integer.parseInt(parts[1].trim());

        String documentId = UUID.randomUUID().toString();
        String documentName = "debug-" + System.currentTimeMillis();

        log.info("Creating debug composition at center ({};{}) with name '{}'", centerQ, centerR, documentName);

        // Build 7 hex grids: 1 center + 6 neighbors
        List<FeatureHexGrid> grids = new ArrayList<>();

        // Center hex: level=100, base=GRASS, circles=DIRT/STONE/SAND
        grids.add(createDebugHexGrid(centerQ, centerR,
                "150",
                String.valueOf(FlatMaterialService.GRASS),
                String.valueOf(FlatMaterialService.DIRT),
                String.valueOf(FlatMaterialService.STONE),
                String.valueOf(FlatMaterialService.SAND),
                "debug-center"));

        // 6 neighbors: level=50, base=GRASS, circles=SAND/STONE/DIRT
        int[][] neighborOffsets = {
                { 1, -1}, // NE
                { 1,  0}, // E
                { 0,  1}, // SE
                {-1,  1}, // SW
                {-1,  0}, // W
                { 0, -1}  // NW
        };
        String[] neighborNames = {"NE", "E", "SE", "SW", "W", "NW"};

        for (int i = 0; i < neighborOffsets.length; i++) {
            int nq = centerQ + neighborOffsets[i][0];
            int nr = centerR + neighborOffsets[i][1];
            grids.add(createDebugHexGrid(nq, nr,
                    "150",
                    String.valueOf(FlatMaterialService.GRASS),
                    String.valueOf(FlatMaterialService.DIRT),
                    String.valueOf(FlatMaterialService.STONE),
                    String.valueOf(FlatMaterialService.SAND),
//                    String.valueOf(FlatMaterialService.GRASS),
//                    String.valueOf(FlatMaterialService.SAND),
//                    String.valueOf(FlatMaterialService.STONE),
//                    String.valueOf(FlatMaterialService.DIRT),
                    "debug-" + neighborNames[i]));
        }

        // Build composition
        HexComposition composition = HexComposition.builder()
                .compositionId(UUID.randomUUID().toString())
                .name(documentName)
                .title("Debug Hex Grid")
                .worldId(context.getWorldId())
                .features(List.of())
                .featureHexGrids(grids)
                .build();
        composition.initialize();

        // Serialize to JSON
        String compositionJson;
        try {
            compositionJson = objectMapper.writeValueAsString(composition);
        } catch (Exception e) {
            throw new WorkflowException(null, "Failed to serialize debug composition: " + e.getMessage());
        }

        // Save to generator_composed collection
        WorldId wid = WorldId.of(context.getWorldId())
                .orElseThrow(() -> new WorkflowException(null, "Invalid worldId: " + context.getWorldId()));

        documentService.save(wid, COMPOSED_COLLECTION, documentId, doc -> {
            doc.setName(documentName);
            doc.setTitle("Debug Hex Grid");
            doc.setFormat("json");
            doc.setContent(compositionJson);
            doc.setType("composer-composed");
            doc.setReadOnly(false);
        });

        log.info("Saved debug composition: documentId={}, name={}, grids={}", documentId, documentName, grids.size());

        // Store document ID in journal
        context.addRecord(new CompositionDocIdRecord(documentId));

        // Build Day3 parameters
        Map<String, String> day3Params = new HashMap<>();
        day3Params.put(GenesisConst.COMPOSITION_ID, documentId);
        String targetPhase = (String) context.getParameters().get(GenesisConst.TARGET_PHASE);
        if (targetPhase != null) {
            day3Params.put(GenesisConst.TARGET_PHASE, targetPhase);
        }

        // Start Day3 Generation workflow
        context.updateWorkflowStatus("day3Generation");
        context.enqueueJob(
                WorkflowJobExecutor.NAME,
                "genesis-day3-generation",
                locationService.getApplicationServiceName(),
                "Day3: Debug Generation",
                day3Params
        );
    }

    @OnSuccess("day3Generation")
    public void onDay3Success(WorkflowContext context) throws WorkflowException {
        log.info("Debug hex generation completed successfully");

        String compositionDocId = context.getLastJournalRecord(CompositionDocIdRecord.class)
                .orElseThrow(() -> new WorkflowException(null, "compositionDocId not found"))
                .getValue();

        context.doComplete(Map.of(
                "compositionDocId", compositionDocId
        ));
    }

    private FeatureHexGrid createDebugHexGrid(int q, int r, String level,
            String base, String circle1, String circle2, String circle3, String name) {
        Map<String, String> params = new HashMap<>();
        params.put("g_builder", "debug");
        params.put("g_level", level);
        params.put("g_base", base);
        params.put("g_circle1", circle1);
        params.put("g_circle2", circle2);
        params.put("g_circle3", circle3);

        return FeatureHexGrid.builder()
                .coordinate(HexVector2.builder().q(q).r(r).build())
                .parameters(params)
                .name(name + " [" + q + ";" + r + "]")
                .description("Debug hex grid")
                .build();
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}
