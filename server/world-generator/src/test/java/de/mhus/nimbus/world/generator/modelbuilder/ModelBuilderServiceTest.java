package de.mhus.nimbus.world.generator.modelbuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.world.WWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelBuilderServiceTest {

    private ModelBuilderService service;
    private WWorld world;
    private WLayer layer;

    @BeforeEach
    void setUp() {
        // Real part builders
        List<ModelPartBuilder> builders = List.of(
                new RootModelPartBuilder(),
                new LogModelPartBuilder(),
                new LeafModelPartBuilder()
        );
        service = new ModelBuilderService(builders, new ObjectMapper(), null);

        // Real WWorld with chunkSize 16
        WorldInfo worldInfo = new WorldInfo();
        worldInfo.setChunkSize(16);
        world = WWorld.builder()
                .worldId("test-world")
                .publicData(worldInfo)
                .build();

        layer = WLayer.builder()
                .worldId("test-world")
                .name("test-layer")
                .layerDataId("layer-data-1")
                .build();
    }

    @Test
    void buildModel_singleRootStep_writesBlocksToChunkData() throws ModelBuilderException {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("root")
                                .type("root")
                                .parameters(Map.of("blockType", "n:g", "depth", 3))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("root").build()
                ))
                .build();

        Vector3Int startPos = Vector3Int.builder().x(5).y(10).z(5).build();
        ModelBuilderContext ctx = service.buildModel(world, layer, model, startPos, Map.of());

        assertThat(ctx.getBlockCount()).isEqualTo(3);
        assertThat(ctx.getChunkDataMap()).isNotEmpty();

        // All blocks at x=5,z=5 -> chunkKey = "0:0" (16er chunks)
        LayerChunkData chunkData = ctx.getChunkDataMap().get("0:0");
        assertThat(chunkData).isNotNull();
        assertThat(chunkData.getBlocks()).hasSize(3);

        // Verify y positions: 10, 9, 8
        var yPositions = chunkData.getBlocks().stream()
                .map(lb -> lb.getBlock().getPosition().getY())
                .sorted()
                .toList();
        assertThat(yPositions).containsExactly(8, 9, 10);

        // Verify blockTypeId
        chunkData.getBlocks().forEach(lb ->
                assertThat(lb.getBlock().getBlockTypeId()).isEqualTo("n:g")
        );
    }

    @Test
    void buildModel_blocksInDifferentChunks_separatedCorrectly() throws ModelBuilderException {
        // log builder: paints vertical column from cursor upward
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("trunk")
                                .type("log")
                                .parameters(Map.of("blockType", "n:w", "heightFrom", 2, "heightTo", 2))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("trunk").build()
                ))
                .build();

        // Start at chunk boundary: x=15 is chunk 0, x=16 would be chunk 1
        Vector3Int startPos = Vector3Int.builder().x(15).y(5).z(0).build();
        ModelBuilderContext ctx = service.buildModel(world, layer, model, startPos, Map.of());

        assertThat(ctx.getBlockCount()).isEqualTo(2);
        // All blocks in chunk "0:0" since x=15 < 16
        assertThat(ctx.getChunkDataMap()).containsKey("0:0");
    }

    @Test
    void buildModel_parameterSubstitution_works() throws ModelBuilderException {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("root")
                                .type("root")
                                .parameters(Map.of("blockType", "$1", "depth", 1))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("root").build()
                ))
                .build();

        Vector3Int startPos = Vector3Int.builder().x(0).y(5).z(0).build();
        ModelBuilderContext ctx = service.buildModel(world, layer, model, startPos, Map.of("1", "n:s"));

        assertThat(ctx.getBlockCount()).isEqualTo(1);
        var block = ctx.getChunkDataMap().get("0:0").getBlocks().get(0).getBlock();
        assertThat(block.getBlockTypeId()).isEqualTo("n:s");
    }

    @Test
    void buildModel_stepParameterOverridesDefinition() throws ModelBuilderException {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("root")
                                .type("root")
                                .parameters(Map.of("blockType", "n:g", "depth", 5))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder()
                                .step("root")
                                .parameters(Map.of("depth", 2))
                                .build()
                ))
                .build();

        Vector3Int startPos = Vector3Int.builder().x(0).y(10).z(0).build();
        ModelBuilderContext ctx = service.buildModel(world, layer, model, startPos, Map.of());

        assertThat(ctx.getBlockCount()).isEqualTo(2);
    }

    @Test
    void buildModel_noSteps_throws() {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .steps(List.of())
                .build();

        Vector3Int startPos = Vector3Int.builder().x(0).y(0).z(0).build();

        assertThatThrownBy(() -> service.buildModel(world, layer, model, startPos, Map.of()))
                .isInstanceOf(ModelBuilderException.class)
                .hasMessageContaining("no steps");
    }

    @Test
    void buildModel_invalidBlockType_throws() {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("root")
                                .type("root")
                                .parameters(Map.of("blockType", "!!!invalid!!!"))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("root").build()
                ))
                .build();

        Vector3Int startPos = Vector3Int.builder().x(0).y(0).z(0).build();

        assertThatThrownBy(() -> service.buildModel(world, layer, model, startPos, Map.of()))
                .isInstanceOf(ModelBuilderException.class)
                .hasMessageContaining("Invalid blockType");
    }

    @Test
    void buildModel_levelSetOnContext_appliedToBlocks() throws ModelBuilderException {
        // Use a custom step that sets level on context
        ModelPartBuilder levelSetter = new ModelPartBuilder() {
            @Override
            public String name() { return "level-test"; }

            @Override
            public void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException {
                context.setBlockType(step.getString("blockType"));
                context.setLevel(step.getInt("level", 0));
                context.paintAtCursor();
            }
        };

        ModelBuilderService svc = new ModelBuilderService(List.of(levelSetter), new ObjectMapper(), null);

        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("block")
                                .type("level-test")
                                .parameters(Map.of("blockType", "n:g", "level", 3))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("block").build()
                ))
                .build();

        Vector3Int startPos = Vector3Int.builder().x(0).y(0).z(0).build();
        ModelBuilderContext ctx = svc.buildModel(world, layer, model, startPos, Map.of());

        assertThat(ctx.getBlockCount()).isEqualTo(1);
        var block = ctx.getChunkDataMap().get("0:0").getBlocks().get(0).getBlock();
        assertThat(block.getLevel()).isEqualTo(3);
    }

    @Test
    void buildModel_negativeCoordinates_correctChunkKey() throws ModelBuilderException {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("root")
                                .type("root")
                                .parameters(Map.of("blockType", "n:g", "depth", 1))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("root").build()
                ))
                .build();

        // x=-5, z=-5 -> chunk -1:-1 (Math.floorDiv)
        Vector3Int startPos = Vector3Int.builder().x(-5).y(10).z(-5).build();
        ModelBuilderContext ctx = service.buildModel(world, layer, model, startPos, Map.of());

        assertThat(ctx.getBlockCount()).isEqualTo(1);
        assertThat(ctx.getChunkDataMap()).containsKey("-1:-1");

        LayerChunkData chunkData = ctx.getChunkDataMap().get("-1:-1");
        assertThat(chunkData.getCx()).isEqualTo(-1);
        assertThat(chunkData.getCz()).isEqualTo(-1);
    }

    @Test
    void buildModel_multipleSteps_accumulateBlocks() throws ModelBuilderException {
        ModelBuilderModel model = ModelBuilderModel.builder()
                .definitions(List.of(
                        ModelBuilderModel.StepDefinition.builder()
                                .name("trunk")
                                .type("log")
                                .parameters(Map.of("blockType", "n:w", "heightFrom", 4, "heightTo", 4))
                                .build(),
                        ModelBuilderModel.StepDefinition.builder()
                                .name("crown")
                                .type("leaf")
                                .parameters(Map.of("blockType", "n:l", "size", 2, "density", 1.0))
                                .build()
                ))
                .steps(List.of(
                        ModelBuilderModel.Step.builder().step("trunk").build(),
                        ModelBuilderModel.Step.builder().step("crown").build()
                ))
                .build();

        Vector3Int startPos = Vector3Int.builder().x(8).y(0).z(8).build();
        ModelBuilderContext ctx = service.buildModel(world, layer, model, startPos, Map.of());

        // trunk=4 blocks, leaf=sphere radius 2 with density 1.0 -> many blocks
        assertThat(ctx.getBlockCount()).isGreaterThan(4);
        assertThat(ctx.getChunkDataMap()).isNotEmpty();

        System.out.println(ModelBuilderDump.dump(ctx));
    }

    @Test
    void buildFromDescriptor_blockStack_paintsVertically() throws ModelBuilderException {
        Vector3Int startPos = Vector3Int.builder().x(5).y(10).z(5).build();
        ModelBuilderContext ctx = service.buildFromDescriptor(world, layer, "block:n:g,n:w", null, startPos);

        assertThat(ctx.getBlockCount()).isEqualTo(2);
        assertThat(ctx.getChunkDataMap()).isNotEmpty();

        LayerChunkData chunkData = ctx.getChunkDataMap().get("0:0");
        assertThat(chunkData).isNotNull();
        assertThat(chunkData.getBlocks()).hasSize(2);

        var blocks = chunkData.getBlocks().stream()
                .sorted((a, b) -> Integer.compare(a.getBlock().getPosition().getY(), b.getBlock().getPosition().getY()))
                .toList();
        assertThat(blocks.get(0).getBlock().getPosition().getY()).isEqualTo(10);
        assertThat(blocks.get(0).getBlock().getBlockTypeId()).isEqualTo("n:g");
        assertThat(blocks.get(1).getBlock().getPosition().getY()).isEqualTo(11);
        assertThat(blocks.get(1).getBlock().getBlockTypeId()).isEqualTo("n:w");
    }

    @Test
    void buildFromDescriptor_singleBlock() throws ModelBuilderException {
        Vector3Int startPos = Vector3Int.builder().x(0).y(5).z(0).build();
        ModelBuilderContext ctx = service.buildFromDescriptor(world, layer, "block:n:g", null, startPos);

        assertThat(ctx.getBlockCount()).isEqualTo(1);
        var block = ctx.getChunkDataMap().get("0:0").getBlocks().get(0).getBlock();
        assertThat(block.getBlockTypeId()).isEqualTo("n:g");
        assertThat(block.getPosition().getY()).isEqualTo(5);
    }
}
