package de.mhus.nimbus.world.shared.migration.layerterrain;

import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.world.shared.migration.chunk.WChunkStorageMigrator_1_0_0_to_1_0_1;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WLayerTerrainStorageMigrator_1_0_0_to_1_0_1 implements SchemaMigrator {

    private final EngineMapper mapper;

    @Override
    public String getEntityType() {
        return "WLayerTerrainStorage";
    }

    @Override
    public SchemaVersion getFromVersion() {
        return SchemaVersion.create("1.0.0");
    }

    @Override
    public SchemaVersion getToVersion() {
        return SchemaVersion.create("1.0.1");
    }

    @Override
    public String migrate(String entityJson) throws Exception {

        if (!Strings.CS.containsAny(
                entityJson,
                "rotationX"
                ,"rotationY"
                ,"textures"
        )) return entityJson;

        var json = mapper.readTree(entityJson);

        var blocks = json.get("blocks"); // array of blocks
        if (blocks != null && blocks.isArray()) {
            for (var blockNode : blocks) {
                processLayerTerrainBlock(blockNode, mapper);
            }
        }
        return mapper.writeValueAsString(json);
    }

    /**
     * Verarbeitet einen LayerTerrain-Block-Knoten mit dem zusätzlichen 'block'-Knoten
     */
    private static void processLayerTerrainBlock(com.fasterxml.jackson.databind.JsonNode blockNode, EngineMapper mapper) {
        var blockBlockNode = blockNode.get("block");
        if (blockBlockNode == null) return;

        // Verwende die statischen Methoden aus WChunkStorageMigrator für die eigentliche Verarbeitung
        WChunkStorageMigrator_1_0_0_to_1_0_1.processBlockModifiers(blockBlockNode, mapper);
    }
}
