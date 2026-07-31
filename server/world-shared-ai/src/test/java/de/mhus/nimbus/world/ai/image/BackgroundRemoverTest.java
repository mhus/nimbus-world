package de.mhus.nimbus.world.ai.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BackgroundRemover}. Fully offline: builds a synthetic image and verifies
 * the edge-flood-fill produces a real alpha channel without punching holes into the subject.
 */
class BackgroundRemoverTest {

    private static final int SIZE = 40;
    private static final int WHITE = 0xFFFFFF;
    private static final int BLUE = 0x0000FF; // far from white -> clearly foreground

    /** White canvas with a solid blue square and one enclosed white pixel inside the square. */
    private BufferedImage sampleImage() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                img.setRGB(x, y, WHITE);
            }
        }
        for (int y = 12; y <= 27; y++) {
            for (int x = 12; x <= 27; x++) {
                img.setRGB(x, y, BLUE);
            }
        }
        // Enclosed background-colored pixel deep inside the blue square.
        img.setRGB(20, 20, WHITE);
        return img;
    }

    private static int alpha(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >> 24) & 0xFF;
    }

    @Test
    void makesBorderConnectedBackgroundTransparent() {
        BufferedImage out = BackgroundRemover.removeBackground(sampleImage(), BackgroundRemover.DEFAULT_THRESHOLD);

        assertThat(out.getColorModel().hasAlpha()).as("result has an alpha channel").isTrue();
        // All four corners belong to the border-connected background -> fully transparent.
        assertThat(alpha(out, 0, 0)).isZero();
        assertThat(alpha(out, SIZE - 1, 0)).isZero();
        assertThat(alpha(out, 0, SIZE - 1)).isZero();
        assertThat(alpha(out, SIZE - 1, SIZE - 1)).isZero();
    }

    @Test
    void keepsForegroundFullyOpaque() {
        BufferedImage out = BackgroundRemover.removeBackground(sampleImage(), BackgroundRemover.DEFAULT_THRESHOLD);

        // A deep-interior blue pixel stays fully opaque and keeps its color.
        assertThat(alpha(out, 15, 15)).isEqualTo(255);
        assertThat(out.getRGB(15, 15) & 0x00FFFFFF).isEqualTo(BLUE);
    }

    @Test
    void doesNotPunchHolesIntoEnclosedBackgroundColoredPixels() {
        BufferedImage out = BackgroundRemover.removeBackground(sampleImage(), BackgroundRemover.DEFAULT_THRESHOLD);

        // The white pixel enclosed by the blue square is NOT border-connected, so it must remain
        // fully opaque even though it matches the background color (edge flood-fill, not color-key).
        assertThat(alpha(out, 20, 20)).as("enclosed background-colored pixel must stay opaque").isEqualTo(255);
    }

    @Test
    void removePngRoundTripProducesTransparentPng() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(sampleImage(), "png", bos);

        byte[] result = BackgroundRemover.removePng(bos.toByteArray(), BackgroundRemover.DEFAULT_THRESHOLD);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(decoded.getRGB(0, 0), true).getAlpha()).isZero();
        assertThat(new Color(decoded.getRGB(15, 15), true).getAlpha()).isEqualTo(255);
    }
}
