package de.mhus.nimbus.world.ai.image.gemini;

import de.mhus.nimbus.world.ai.image.AiImage;
import de.mhus.nimbus.world.ai.image.AiImageOptions;
import de.mhus.nimbus.world.ai.image.BackgroundRemover;
import de.mhus.nimbus.world.ai.model.SimpleRateLimiter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual, network-dependent test for real Gemini image generation with transparency.
 * <p>
 * Excluded from the normal build (tagged {@code manual}); it hits the live Gemini API and needs a
 * real key. Run it explicitly with the key in the environment:
 * <pre>{@code
 *   GEMINI_API_KEY=<key> mvn -pl world-shared-ai test \
 *       -Dtest=GeminiImageModelManualTest -DexcludedGroups=
 * }</pre>
 * The key is available locally under {@code s_settings.langchain4j.gemini.apiKey} and in
 * {@code ../vance-wb/confidential/init-settings-gemini.yaml}. The generated PNG is written to
 * {@code target/gemini-manual/} for visual inspection.
 */
@Tag("manual")
class GeminiImageModelManualTest {

    private static final String MODEL = "gemini-2.5-flash-image";

    @Test
    void generatesItemIconWithRealTransparency() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "GEMINI_API_KEY not set – skipping manual Gemini image test");

        AiImageOptions options = AiImageOptions.builder()
                .width(1024)
                .height(1024)
                .transparentBackground(true)
                .build();

        GeminiImageModelImpl model = new GeminiImageModelImpl(
                "gemini:" + MODEL, MODEL, apiKey, options,
                new ObjectMapper(), new SimpleRateLimiter(15), BackgroundRemover.DEFAULT_THRESHOLD);

        AiImage image = model.generate(
                "A single medieval iron sword game item icon, hand-painted fantasy style with a bold"
                        + " dark outline, centered, filling most of the frame.");

        assertThat(image.hasBytes()).as("image bytes returned").isTrue();
        assertThat(image.getMimeType()).isEqualTo("image/png");

        // Persist for visual inspection.
        Path outDir = Path.of("target", "gemini-manual");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("gemini_transparent_sword.png");
        Files.write(outFile, image.getBytes());
        System.out.println("Wrote " + image.getWidth() + "x" + image.getHeight()
                + " PNG to " + outFile.toAbsolutePath());

        // Verify REAL transparency: an alpha channel plus actually-transparent border pixels.
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getColorModel().hasAlpha()).as("PNG has an alpha channel").isTrue();

        int w = decoded.getWidth();
        int h = decoded.getHeight();
        int fullyTransparentCorners = 0;
        int[][] corners = {{2, 2}, {w - 3, 2}, {2, h - 3}, {w - 3, h - 3}};
        for (int[] c : corners) {
            int alpha = (decoded.getRGB(c[0], c[1]) >> 24) & 0xFF;
            if (alpha == 0) {
                fullyTransparentCorners++;
            }
        }
        assertThat(fullyTransparentCorners)
                .as("all four corners should be fully transparent after background removal")
                .isEqualTo(4);
    }
}
