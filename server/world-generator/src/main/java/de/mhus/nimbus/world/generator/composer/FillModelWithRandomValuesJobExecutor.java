package de.mhus.nimbus.world.generator.composer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static de.mhus.nimbus.world.generator.translator.TranslateInstructionJobExecutor.COMPOSED_COLLECTION;

/**
 * Job executor that fills missing flora/fauna parameters on FeatureHexGrids
 * with random, biome-appropriate values.
 *
 * After composition (HexComposition), grids may lack gf_flora, gf_fauna,
 * gf_density or gf_category values. This executor loads available flora/fauna
 * definitions from the region and assigns matching ones based on the biome type
 * (g_builder parameter).
 *
 * Executor name: 'generator-fill-model-random-values'
 *
 * Required parameters:
 * - documentId: Id of the composed document (in 'generator_composed' collection)
 * - worldId: World identifier
 *
 * Output:
 * - success: New document ID where the enriched composition was saved
 * - failure: Error message
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FillModelWithRandomValuesJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "generator-fill-model-random-values";
    private static final String[] CATEGORIES = {"one", "two", "three", "four", "five"};

    private final WDocumentService documentService;
    private final WAnythingService anythingService;
    private final ObjectMapper objectMapper;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting fill-model-random-values job: jobId={}", job.getId());

            String documentId = getRequiredParameter(job, "documentId");
            String worldId = getRequiredParameter(job, "worldId");

            // 1. Load composition
            WDocument document = loadDocument(worldId, documentId);
            if (document == null) {
                return JobResult.failure("Document not found: " + documentId);
            }

            HexComposition composition = extractComposition(document);
            if (composition == null) {
                return JobResult.failure("Failed to parse composition from document: " + documentId);
            }

            // 2. Derive region worldId for flora/fauna lookups
            WorldId wid = WorldId.of(worldId)
                    .orElseThrow(() -> new JobExecutionException("Invalid worldId: " + worldId));
            String regionWorldId = wid.toRegionCollection().getId();

            // 3. Load flora definitions and group by biome prefix
            Map<String, List<String>> biomeFloraMap = buildBiomeDefinitionMap(regionWorldId, "flora");
            log.info("Loaded flora definitions for {} biome prefixes", biomeFloraMap.size());

            // 4. Load fauna definitions and group by biome prefix
            Map<String, List<String>> biomeFaunaMap = buildBiomeDefinitionMap(regionWorldId, "fauna");
            log.info("Loaded fauna definitions for {} biome prefixes", biomeFaunaMap.size());

            // 5. Fill missing values
            Random random = new Random();
            int floraFilled = 0;
            int faunaFilled = 0;
            int densityFilled = 0;
            int categoryFilled = 0;

            Map<String, FeatureHexGrid> registry = composition.getFeatureHexGridRegistry();
            for (FeatureHexGrid grid : registry.values()) {
                Map<String, String> params = grid.getParameters();
                String biomeType = params.get("g_builder");
                if (biomeType == null) {
                    continue;
                }

                boolean floraSet = false;
                boolean faunaSet = false;

                // Fill gf_flora if not set
                if (params.get("gf_flora") == null || params.get("gf_flora").isBlank()) {
                    List<String> candidates = biomeFloraMap.get(biomeType);
                    if (candidates != null && !candidates.isEmpty()) {
                        String chosen = candidates.get(random.nextInt(candidates.size()));
                        params.put("gf_flora", chosen);
                        floraFilled++;
                        floraSet = true;
                    } else {
                        log.warn("No flora definitions found for biome '{}'", biomeType);
                    }
                } else {
                    floraSet = true;
                }

                // Fill gf_fauna if not set
                if (params.get("gf_fauna") == null || params.get("gf_fauna").isBlank()) {
                    List<String> candidates = biomeFaunaMap.get(biomeType);
                    if (candidates != null && !candidates.isEmpty()) {
                        String chosen = candidates.get(random.nextInt(candidates.size()));
                        params.put("gf_fauna", chosen);
                        faunaFilled++;
                        faunaSet = true;
                    } else {
                        log.warn("No fauna definitions found for biome '{}'", biomeType);
                    }
                } else {
                    faunaSet = true;
                }

                // Fill density and category if flora or fauna is set
                if (floraSet || faunaSet) {
                    String densityStr = params.get("gf_density");
                    if (densityStr == null || densityStr.isBlank()) {
                        double density = 0.001 + random.nextDouble() * (0.05 - 0.001);
                        params.put("gf_density", String.format("%.3f", density));
                        densityFilled++;
                    } else {
                        try {
                            double density = Double.parseDouble(densityStr);
                            if (density > 0.1) {
                                params.put("gf_density", "0.1");
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Invalid gf_density value '{}', keeping as-is", densityStr);
                        }
                    }

                    if (params.get("gf_category") == null || params.get("gf_category").isBlank()) {
                        params.put("gf_category", CATEGORIES[random.nextInt(CATEGORIES.length)]);
                        categoryFilled++;
                    }
                }
            }

            log.info("Fill results: flora={}, fauna={}, density={}, category={}",
                    floraFilled, faunaFilled, densityFilled, categoryFilled);

            // 6. Save enriched composition as new document
            String newDocumentId = saveComposition(worldId, document.getName(), composition);

            log.info("Saved enriched composition: documentId={}", newDocumentId);

            return JobResult.success(Map.of(
                    "documentId", newDocumentId,
                    "floraFilled", floraFilled,
                    "faunaFilled", faunaFilled,
                    "densityFilled", densityFilled,
                    "categoryFilled", categoryFilled
            ));

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fill model random values job failed", e);
            throw new JobExecutionException("Job execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build a map from biome prefix to list of WAnything names.
     * E.g. for collection "flora" with entries named "forest", "forest_dense", "plains":
     * { "forest" -> ["forest", "forest_dense"], "plains" -> ["plains"] }
     */
    private Map<String, List<String>> buildBiomeDefinitionMap(String regionWorldId, String collection) {
        List<WAnything> definitions = anythingService.findByWorldIdAndCollection(regionWorldId, collection);
        Map<String, List<String>> biomeMap = new HashMap<>();

        for (WAnything def : definitions) {
            String name = def.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            // Extract biome prefix: everything before the first underscore, or the full name
            String prefix = name.contains("_") ? name.substring(0, name.indexOf('_')) : name;
            biomeMap.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
        }

        return biomeMap;
    }

    private WDocument loadDocument(String worldId, String documentId) {
        try {
            WorldId wid = WorldId.of(worldId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

            return documentService.findByDocumentId(wid, COMPOSED_COLLECTION, documentId)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Failed to load document: worldId={}, documentId={}", worldId, documentId, e);
            return null;
        }
    }

    private HexComposition extractComposition(WDocument document) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            HexComposition composition = mapper.readValue(document.getContent(), HexComposition.class);

            // Convert featureHexGrids list to registry map
            if (composition.getFeatureHexGrids() != null && !composition.getFeatureHexGrids().isEmpty()) {
                Map<String, FeatureHexGrid> registry = composition.getFeatureHexGridRegistry();
                for (FeatureHexGrid grid : composition.getFeatureHexGrids()) {
                    String key = TypeUtil.toStringHexCoord(grid.getCoordinate());
                    registry.put(key, grid);
                }
                log.info("Converted {} FeatureHexGrids from list to registry", composition.getFeatureHexGrids().size());
            }

            return composition;
        } catch (Exception e) {
            log.error("Failed to extract composition from document", e);
            return null;
        }
    }

    private String saveComposition(String worldId, String sourceDocumentName, HexComposition composition) throws Exception {
        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid worldId: " + worldId));

        String newDocumentId = UUID.randomUUID().toString();

        // Convert registry map to list for JSON serialization
        if (composition.getFeatureHexGridRegistry() != null && !composition.getFeatureHexGridRegistry().isEmpty()) {
            composition.setFeatureHexGrids(new ArrayList<>(composition.getFeatureHexGridRegistry().values()));
        }

        String compositionJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(composition);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("enrichedAt", Instant.now().toString());
        metadata.put("sourceDocumentName", sourceDocumentName);
        metadata.put("totalGrids", String.valueOf(
                composition.getFeatureHexGrids() != null ? composition.getFeatureHexGrids().size() : 0));

        WDocument saved = documentService.save(wid, COMPOSED_COLLECTION, newDocumentId, doc -> {
            doc.setName(sourceDocumentName);
            doc.setTitle(composition.getName() != null ? composition.getName() : "Enriched Composition");
            doc.setFormat("json");
            doc.setContent(compositionJson);
            doc.setMetadata(metadata);
            doc.setType("composer-composed");
            doc.setReadOnly(false);
        });

        return saved.getDocumentId();
    }

    private String getRequiredParameter(WJob job, String paramName) throws JobExecutionException {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + paramName);
        }
        return value;
    }
}
