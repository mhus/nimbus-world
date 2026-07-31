package de.mhus.nimbus.world.ai.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Removes a flat, near-uniform background from an image and replaces it with a real alpha channel.
 * <p>
 * The background region is grown inward from the image borders (edge flood-fill): starting from the
 * border pixels, neighbouring pixels are marked as background as long as their color stays within a
 * distance threshold of the sampled background color. Because only pixels that are <b>connected to
 * the border</b> are removed, equally colored pixels <b>inside</b> the subject are preserved — this
 * is the key difference to a global color-key, which would punch holes into the subject.
 * <p>
 * Pixels close to (but not exactly) the background color receive a partial alpha value, giving the
 * cut-out a soft, anti-aliased edge instead of a hard fringe.
 * <p>
 * This is a post-processing helper for image providers that cannot emit native transparency
 * (e.g. Google Gemini). Providers with native alpha support (e.g. OpenAI {@code gpt-image-1}) should
 * not use it.
 */
@Slf4j
public final class BackgroundRemover {

    /**
     * Default color distance (Euclidean in RGB, range 0..441) below which a border-connected pixel
     * is treated as background. 60 works well for AI images placed on a flat white/solid background.
     */
    public static final int DEFAULT_THRESHOLD = 60;

    private BackgroundRemover() {
    }

    /**
     * Decode PNG bytes, remove the background and re-encode as a PNG with an alpha channel.
     *
     * @param pngBytes  encoded source image (any format ImageIO can read)
     * @param threshold color distance threshold, see {@link #DEFAULT_THRESHOLD}
     * @return PNG bytes with a transparent background (RGBA)
     * @throws IOException if the image cannot be decoded or encoded
     */
    public static byte[] removePng(byte[] pngBytes, int threshold) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (src == null) {
            throw new IOException("Could not decode image bytes for background removal");
        }
        BufferedImage out = removeBackground(src, threshold);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    /**
     * Remove the border-connected background of an image, returning a new ARGB image.
     *
     * @param src       source image
     * @param threshold color distance threshold, see {@link #DEFAULT_THRESHOLD}
     * @return a {@link BufferedImage#TYPE_INT_ARGB} image with the background made transparent
     */
    public static BufferedImage removeBackground(BufferedImage src, int threshold) {
        int w = src.getWidth();
        int h = src.getHeight();

        // Sample the background reference color from corners and edge midpoints.
        int[][] samples = {
                {0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1},
                {w / 2, 0}, {w / 2, h - 1}, {0, h / 2}, {w - 1, h / 2}
        };
        long sr = 0, sg = 0, sb = 0;
        for (int[] s : samples) {
            int rgb = src.getRGB(s[0], s[1]);
            sr += (rgb >> 16) & 0xFF;
            sg += (rgb >> 8) & 0xFF;
            sb += rgb & 0xFF;
        }
        int br = (int) (sr / samples.length);
        int bg = (int) (sg / samples.length);
        int bb = (int) (sb / samples.length);

        boolean[] background = new boolean[w * h];
        Deque<int[]> queue = new ArrayDeque<>();

        // Seed all border pixels that look like background.
        for (int x = 0; x < w; x++) {
            seed(src, x, 0, br, bg, bb, threshold, background, queue, w);
            seed(src, x, h - 1, br, bg, bb, threshold, background, queue, w);
        }
        for (int y = 0; y < h; y++) {
            seed(src, 0, y, br, bg, bb, threshold, background, queue, w);
            seed(src, w - 1, y, br, bg, bb, threshold, background, queue, w);
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0];
            int y = p[1];
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h
                        && !background[ny * w + nx]
                        && dist(src.getRGB(nx, ny), br, bg, bb) < threshold) {
                    background[ny * w + nx] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int removed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                if (background[y * w + x]) {
                    out.setRGB(x, y, rgb & 0x00FFFFFF); // alpha = 0
                    removed++;
                } else if (touchesBackground(background, x, y, w, h)) {
                    // Only pixels on the cut edge are feathered by their distance to the background,
                    // giving a soft anti-aliased border. Interior pixels stay fully opaque even if they
                    // happen to match the background color, so enclosed areas never turn into holes.
                    double d = dist(rgb, br, bg, bb);
                    int alpha = d >= threshold ? 255 : (int) Math.round(255.0 * d / threshold);
                    out.setRGB(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
                } else {
                    out.setRGB(x, y, 0xFF000000 | (rgb & 0x00FFFFFF)); // fully opaque
                }
            }
        }
        log.debug("BackgroundRemover: bgRef=({},{},{}) threshold={} removed={}% of {}x{}",
                br, bg, bb, threshold, removed * 100L / ((long) w * h), w, h);
        return out;
    }

    private static void seed(BufferedImage src, int x, int y, int br, int bg, int bb, int threshold,
                             boolean[] mask, Deque<int[]> queue, int w) {
        if (!mask[y * w + x] && dist(src.getRGB(x, y), br, bg, bb) < threshold) {
            mask[y * w + x] = true;
            queue.add(new int[]{x, y});
        }
    }

    private static boolean touchesBackground(boolean[] background, int x, int y, int w, int h) {
        return (x > 0 && background[y * w + (x - 1)])
                || (x < w - 1 && background[y * w + (x + 1)])
                || (y > 0 && background[(y - 1) * w + x])
                || (y < h - 1 && background[(y + 1) * w + x]);
    }

    private static double dist(int rgb, int br, int bg, int bb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int dr = r - br;
        int dg = g - bg;
        int db = b - bb;
        return Math.sqrt((double) dr * dr + (double) dg * dg + (double) db * db);
    }
}
