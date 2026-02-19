package de.mhus.nimbus.world.generator.composer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.build.HexGridSchemaImageCreator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class HexGridSchemaImageCreatorTest {

    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        outputDir = Paths.get("target/test-output/hex-schema");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testCreateSchemaImageFromComposedExample() throws Exception {
        File jsonFile = new File("src/test/resources/composed-example.json");
        assertTrue(jsonFile.exists(), "composed-example.json should exist");

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        HexComposition composition = mapper.readValue(jsonFile, HexComposition.class);

        assertNotNull(composition, "Composition should be loaded");
        assertNotNull(composition.getFeatureHexGrids(), "featureHexGrids should not be null");
        assertFalse(composition.getFeatureHexGrids().isEmpty(), "featureHexGrids should not be empty");

        log.info("Loaded composition with {} featureHexGrids", composition.getFeatureHexGrids().size());

        HexGridSchemaImageCreator creator = HexGridSchemaImageCreator.builder()
            .composition(composition)
            .hexGridSize(400)
            .outputDirectory(outputDir.toString())
            .imageName("composed-example")
            .build();

        HexGridSchemaImageCreator.SchemaImageResult result = creator.createSchemaImage();

        assertTrue(result.isSuccess(), "Schema image creation should succeed");
        assertNotNull(result.getImage(), "Image should not be null");
        assertTrue(result.getRenderedGridCount() > 0, "Should render at least one grid");
        assertNotNull(result.getOutputFile(), "Output file should be created");
        assertTrue(result.getOutputFile().exists(), "Output file should exist on disk");

        log.info("Created schema image: {} ({}x{} pixels, {} grids)",
            result.getOutputFile().getAbsolutePath(),
            result.getImageWidth(), result.getImageHeight(),
            result.getRenderedGridCount());
    }
}
