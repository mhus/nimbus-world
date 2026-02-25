package de.mhus.nimbus.world.generator.modelbuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.OptionalInt.empty;
import static java.util.OptionalInt.of;

/**
 * Service for building 3D models from JSON definitions.
 * Resolves steps against definitions, merges and substitutes parameters,
 * and delegates execution to registered ModelPartBuilder implementations.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelBuilderService {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$(\\w+)");

    private final List<ModelPartBuilder> partBuilders;
    private final ObjectMapper objectMapper;
    private final WAnythingService anythingService;
    private Map<String, ModelPartBuilder> partBuilderMap;

    private synchronized void initializePartBuildersIfNeeded() {
        if (partBuilderMap == null) {
            partBuilderMap = new HashMap<>();
            if (partBuilders != null) {
                for (ModelPartBuilder builder : partBuilders) {
                    String name = builder.name();
                    if (partBuilderMap.containsKey(name)) {
                        log.warn("Duplicate ModelPartBuilder name '{}': {} vs {}",
                                name,
                                partBuilderMap.get(name).getClass().getSimpleName(),
                                builder.getClass().getSimpleName());
                    }
                    partBuilderMap.put(name, builder);
                    log.debug("Registered ModelPartBuilder: {}", name);
                }
            }
            log.info("Initialized {} model part builders", partBuilderMap.size());
        }
    }

    /**
     * Parse a JSON string into a ModelBuilderModel.
     */
    public ModelBuilderModel parseModel(String json) throws ModelBuilderException {
        try {
            return objectMapper.readValue(json, ModelBuilderModel.class);
        } catch (Exception e) {
            throw new ModelBuilderException("Failed to parse model JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Load a model from WAnythingService by collection and name (region-scoped),
     * then build it.
     *
     * @param world        the world
     * @param layer        the target layer
     * @param collection   WAnything collection name
     * @param name         WAnything entity name
     * @param startPos     starting cursor position
     * @param parameterMap parameter substitution map ($1, $2, ...)
     * @param group        group identifier for all blocks (set on LayerBlock.group)
     * @return ModelBuilderContext with blockCount and chunkDataMap
     */
    public ModelBuilderContext buildModel(WWorld world, WLayer layer, String collection, String name,
                          Vector3Int startPos, Map<String, String> parameterMap,
                          String group) throws ModelBuilderException {
        WorldId regionWorldId = WorldId.of(world.getWorldId())
                .orElseThrow(() -> new ModelBuilderException("Invalid worldId: " + world.getWorldId()))
                .toRegionCollection();

        WAnything entity = anythingService.findByWorldIdAndCollectionAndName(
                regionWorldId.getId(), collection, name)
                .orElseThrow(() -> new ModelBuilderException(
                        "Model not found: collection=" + collection + ", name=" + name
                                + ", worldId=" + regionWorldId.getId()));

        ModelBuilderModel model = entity.getDataAs(ModelBuilderModel.class)
                .orElseThrow(() -> new ModelBuilderException(
                        "Failed to convert WAnything data to ModelBuilderModel: collection=" + collection
                                + ", name=" + name));

        return buildModel(world, layer, model, startPos, parameterMap, group);
    }

    /**
     * Build a model with a new Random instance.
     *
     * @param world    the world
     * @param layer    the target layer
     * @param model    the model definition
     * @param startPos starting cursor position
     * @param parameterMap parameter substitution map ($1, $2, ...)
     * @param group        group identifier for all blocks (set on LayerBlock.group)
     * @return ModelBuilderContext with blockCount and chunkDataMap
     */
    public ModelBuilderContext buildModel(WWorld world, WLayer layer, ModelBuilderModel model,
                          Vector3Int startPos, Map<String, String> parameterMap,
                          String group) throws ModelBuilderException {
        return buildModel(world, layer, model, startPos, parameterMap, new Random(), group);
    }

    /**
     * Build a model from the given definition.
     * Writes blocks directly into LayerChunkData maps (accessible via context.getChunkDataMap()).
     *
     * @param world        the world
     * @param layer        the target layer
     * @param model        the model definition with steps and definitions
     * @param startPos     starting cursor position
     * @param parameterMap parameter substitution map ($1, $2, ...)
     * @param random       random instance for stochastic decisions
     * @param group        group identifier for all blocks (set on LayerBlock.group)
     * @return ModelBuilderContext with blockCount and chunkDataMap
     */
    public ModelBuilderContext buildModel(WWorld world, WLayer layer, ModelBuilderModel model,
                          Vector3Int startPos, Map<String, String> parameterMap,
                          Random random, String group) throws ModelBuilderException {
        initializePartBuildersIfNeeded();

        if (model.getSteps() == null || model.getSteps().isEmpty()) {
            throw new ModelBuilderException("Model has no steps");
        }

        // Build definition lookup map
        Map<String, ModelBuilderModel.StepDefinition> definitionMap = new HashMap<>();
        if (model.getDefinitions() != null) {
            for (ModelBuilderModel.StepDefinition def : model.getDefinitions()) {
                definitionMap.put(def.getName(), def);
            }
        }

        // Create context
        ModelBuilderContext context = ModelBuilderContext.builder()
                .world(world)
                .layer(layer)
                .position(Vector3Int.builder()
                        .x(startPos.getX())
                        .y(startPos.getY())
                        .z(startPos.getZ())
                        .build())
                .random(random)
                .blockCount(0)
                .group(group)
                .fillBlockLevel(true)
                .startY(startPos.getY())
                .build();

        // Execute steps
        for (ModelBuilderModel.Step step : model.getSteps()) {
            String definitionName = step.getDefinition() != null ? step.getDefinition() : step.getStep();
            ModelBuilderModel.StepDefinition definition = definitionMap.get(definitionName);

            if (definition == null) {
                throw new ModelBuilderException("Definition not found: " + definitionName + " (step: " + step.getStep() + ")");
            }

            // Resolve the step: merge parameters and substitute $N
            ResolvedStep resolvedStep = resolveStep(step, definition, parameterMap);

            // Evaluate when-condition if present
            if (Strings.isNotBlank(step.getWhen())) {
                Map<String, Object> stepVars = new HashMap<>(resolvedStep.getParameters());
                if (parameterMap != null) {
                    for (var entry : parameterMap.entrySet()) {
                        stepVars.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
                stepVars.put("x", context.getPosition().getX());
                stepVars.put("y", context.getPosition().getY());
                stepVars.put("z", context.getPosition().getZ());
                stepVars.put("level", context.getPosition().getY() - context.getStartY());
                stepVars.put("random", context.getRandom().nextDouble());
                if (!ConditionEvaluator.evaluate(step.getWhen(), stepVars)) {
                    log.debug("Skipping step '{}': condition '{}' not met", step.getStep(), step.getWhen());
                    continue;
                }
            }

            // Find the builder
            ModelPartBuilder builder = partBuilderMap.get(resolvedStep.getType());
            if (builder == null) {
                throw new ModelBuilderException("ModelPartBuilder not found: " + resolvedStep.getType() + " (step: " + step.getStep() + ")");
            }

            log.debug("Executing step '{}' with builder '{}', params: {}", step.getStep(), resolvedStep.getType(), resolvedStep.getParameters());
            builder.buildPart(context, resolvedStep);
        }

        log.info("Model built: {} blocks placed into {} chunks", context.getBlockCount(), context.getChunkDataMap().size());
        return context;
    }

    /**
     * Build from a descriptor string. Dispatches to block stacking or model loading.
     *
     * @param world                the world
     * @param layer                the target layer
     * @param descriptor           descriptor string (e.g. "block:m:log,m:top" or "model:tree,log=m:birch_log")
     * @param collection           WAnything collection name (used for model descriptors)
     * @param startPos             starting cursor position
     * @param additionalParameters extra parameters merged into model parameters (may be null)
     * @param group                group identifier for all blocks (set on LayerBlock.group)
     * @return ModelBuilderContext with blockCount and chunkDataMap
     */
    public ModelBuilderContext buildFromDescriptor(WWorld world, WLayer layer, String descriptor,
                                                   String collection, Vector3Int startPos,
                                                   Map<String, String> additionalParameters,
                                                   String group) throws ModelBuilderException {
        WAnythingDescriptor parsed = WAnythingDescriptor.parse(descriptor);
        return switch (parsed) {
            case WAnythingDescriptor.BlockStack blockStack -> buildBlockStack(world, layer, blockStack, startPos, group);
            case WAnythingDescriptor.ModelRef modelRef -> {
                Map<String, String> mergedParams = new HashMap<>(modelRef.parameters());
                if (additionalParameters != null) {
                    mergedParams.putAll(additionalParameters);
                }
                yield buildModel(world, layer, collection, modelRef.name(), startPos, mergedParams, group);
            }
        };
    }

    /**
     * Resolve flora placement constraints for a descriptor.
     * <ul>
     *   <li>For block stacks: maxHeight = number of blocks, no water constraints</li>
     *   <li>For model refs: reads maxHeight/minWater/maxWater from metadata</li>
     * </ul>
     *
     * @param world      the world (for region-scoped model lookup)
     * @param descriptor descriptor string
     * @param collection WAnything collection name for model lookup
     * @return flora constraints (any field may be empty = unconstrained)
     */
    public FloraConstraints resolveDescriptorConstraints(WWorld world, String descriptor, String collection) {
        try {
            WAnythingDescriptor parsed = WAnythingDescriptor.parse(descriptor);
            return switch (parsed) {
                case WAnythingDescriptor.BlockStack bs ->
                        new FloraConstraints(of(bs.blockTypes().size()), empty(), empty(),
                                true, true, true, false);
                case WAnythingDescriptor.ModelRef mr ->
                        resolveModelConstraints(world, collection, mr.name());
            };
        } catch (Exception e) {
            log.debug("Cannot resolve descriptor constraints for '{}': {}", descriptor, e.getMessage());
            return FloraConstraints.UNCONSTRAINED;
        }
    }

    private FloraConstraints resolveModelConstraints(WWorld world, String collection, String name) {
        WorldId regionWorldId = WorldId.of(world.getWorldId()).orElse(null);
        if (regionWorldId == null) return FloraConstraints.UNCONSTRAINED;

        WAnything entity = anythingService.findByWorldIdAndCollectionAndName(
                regionWorldId.toRegionCollection().getId(), collection, name).orElse(null);
        if (entity == null) return FloraConstraints.UNCONSTRAINED;

        ModelBuilderModel model = entity.getDataAs(ModelBuilderModel.class).orElse(null);
        if (model == null) return FloraConstraints.UNCONSTRAINED;

        Integer maxHeight = model.getMetadataInt("maxHeight");
        Integer minWater = model.getMetadataInt("minWater");
        Integer maxWater = model.getMetadataInt("maxWater");
        boolean land = model.getMetadataBoolean("land", true);
        boolean water = model.getMetadataBoolean("water", true);
        boolean sea = model.getMetadataBoolean("sea", true);
        boolean emerse = model.getMetadataBoolean("emerse", false);
        return new FloraConstraints(
                maxHeight != null ? of(maxHeight) : empty(),
                minWater != null ? of(minWater) : empty(),
                maxWater != null ? of(maxWater) : empty(),
                land, water, sea, emerse);
    }

    private ModelBuilderContext buildBlockStack(WWorld world, WLayer layer,
                                                WAnythingDescriptor.BlockStack blockStack,
                                                Vector3Int startPos, String group) throws ModelBuilderException {
        ModelBuilderContext context = ModelBuilderContext.builder()
                .world(world)
                .layer(layer)
                .position(Vector3Int.builder()
                        .x(startPos.getX())
                        .y(startPos.getY())
                        .z(startPos.getZ())
                        .build())
                .random(new Random())
                .blockCount(0)
                .group(group)
                .fillBlockLevel(true)
                .startY(startPos.getY())
                .build();

        for (String blockType : blockStack.blockTypes()) {
            context.setBlockType(blockType);
            context.paintAtCursor();
            context.incrementY();
        }

        log.debug("BlockStack built: {} blocks", context.getBlockCount());
        return context;
    }

    /**
     * Resolve a step by merging definition defaults with step overrides,
     * then applying $N parameter substitution.
     */
    private ResolvedStep resolveStep(ModelBuilderModel.Step step,
                                     ModelBuilderModel.StepDefinition definition,
                                     Map<String, String> parameterMap) {
        // Merge parameters: definition defaults as base, step params override
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition.getParameters() != null) {
            merged.putAll(definition.getParameters());
        }
        if (step.getParameters() != null) {
            merged.putAll(step.getParameters());
        }

        // Apply $N substitution
        if (parameterMap != null && !parameterMap.isEmpty()) {
            substituteParameters(merged, parameterMap);
        }

        return ResolvedStep.builder()
                .name(step.getStep())
                .type(definition.getType())
                .parameters(merged)
                .build();
    }

    /**
     * Substitute $N placeholders in parameter values using the parameter map.
     */
    private void substituteParameters(Map<String, Object> params, Map<String, String> parameterMap) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                entry.setValue(substituteString(str, parameterMap));
            } else if (value instanceof List<?> list) {
                List<Object> substituted = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof String str) {
                        substituted.add(substituteString(str, parameterMap));
                    } else {
                        substituted.add(item);
                    }
                }
                entry.setValue(substituted);
            }
        }
    }

    /**
     * Replace $N patterns in a string with values from the parameter map.
     */
    private String substituteString(String input, Map<String, String> parameterMap) {
        Matcher matcher = PARAM_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = parameterMap.getOrDefault(key, matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
