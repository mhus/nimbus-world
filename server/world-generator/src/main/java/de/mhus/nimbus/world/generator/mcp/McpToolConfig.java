package de.mhus.nimbus.world.generator.mcp;

import de.mhus.nimbus.world.generator.mcp.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            WorldTools worldTools,
            LayerTools layerTools,
            LayerModelTools layerModelTools,
            ChunkTools chunkTools,
            TerrainTools terrainTools,
            BlockTypeTools blockTypeTools,
            AssetTools assetTools,
            JobTools jobTools,
            DocumentTools documentTools,
            FlatTools flatTools,
            AnythingTools anythingTools,
            ItemTools itemTools,
            ChestTools chestTools,
            ProgressTools progressTools,
            EntityTools entityTools,
            EntityModelTools entityModelTools,
            NpcGeneratorTools npcGeneratorTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        worldTools,
                        layerTools,
                        layerModelTools,
                        chunkTools,
                        terrainTools,
                        blockTypeTools,
                        assetTools,
                        jobTools,
                        documentTools,
                        flatTools,
                        anythingTools,
                        itemTools,
                        chestTools,
                        progressTools,
                        entityTools,
                        entityModelTools,
                        npcGeneratorTools)
                .build();
    }
}
