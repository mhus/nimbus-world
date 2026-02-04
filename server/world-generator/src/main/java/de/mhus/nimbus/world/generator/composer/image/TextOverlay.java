package de.mhus.nimbus.world.generator.composer.image;

import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * ImageOverlay implementation for drawing text using a simple bitmap font.
 * Supports uppercase letters A-Z, digits 0-9, and special characters: -_.;,/
 */
@Getter
@Setter
public class TextOverlay implements ImageOverlay {

    private String text;
    private int x;
    private int y;
    private Color color = Color.WHITE;
    private int scale = 1;

    public static final int CHAR_WIDTH = 5;
    public static final int CHAR_HEIGHT = 7;
    public static final int CHAR_SPACING = 1;

    // Bitmap font definition: each character is 5x7 pixels
    // 1 = pixel on, 0 = pixel off
    public static final Map<Character, int[][]> FONT = new HashMap<>();

    static {
        // Letters A-Z
        FONT.put('A', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        FONT.put('B', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0}
        });
        FONT.put('C', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('D', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0}
        });
        FONT.put('E', new int[][]{
            {1,1,1,1,1},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,1}
        });
        FONT.put('F', new int[][]{
            {1,1,1,1,1},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0}
        });
        FONT.put('G', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,0},
            {1,0,1,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('H', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        FONT.put('I', new int[][]{
            {1,1,1,1,1},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {1,1,1,1,1}
        });
        FONT.put('J', new int[][]{
            {0,0,0,0,1},
            {0,0,0,0,1},
            {0,0,0,0,1},
            {0,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('K', new int[][]{
            {1,0,0,0,1},
            {1,0,0,1,0},
            {1,0,1,0,0},
            {1,1,0,0,0},
            {1,0,1,0,0},
            {1,0,0,1,0},
            {1,0,0,0,1}
        });
        FONT.put('L', new int[][]{
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,1}
        });
        FONT.put('M', new int[][]{
            {1,0,0,0,1},
            {1,1,0,1,1},
            {1,0,1,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        FONT.put('N', new int[][]{
            {1,0,0,0,1},
            {1,1,0,0,1},
            {1,0,1,0,1},
            {1,0,0,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        FONT.put('O', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('P', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0}
        });
        FONT.put('Q', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,1,0,1},
            {1,0,0,1,0},
            {0,1,1,0,1}
        });
        FONT.put('R', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0},
            {1,0,1,0,0},
            {1,0,0,1,0},
            {1,0,0,0,1}
        });
        FONT.put('S', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,0},
            {0,1,1,1,0},
            {0,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('T', new int[][]{
            {1,1,1,1,1},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0}
        });
        FONT.put('U', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('V', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0}
        });
        FONT.put('W', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,1,0,1},
            {1,1,0,1,1},
            {1,0,0,0,1}
        });
        FONT.put('X', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0},
            {0,1,0,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        FONT.put('Y', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0}
        });
        FONT.put('Z', new int[][]{
            {1,1,1,1,1},
            {0,0,0,0,1},
            {0,0,0,1,0},
            {0,0,1,0,0},
            {0,1,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,1}
        });

        // Digits 0-9
        FONT.put('0', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,1,1},
            {1,0,1,0,1},
            {1,1,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('1', new int[][]{
            {0,0,1,0,0},
            {0,1,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,1,1,1,0}
        });
        FONT.put('2', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {0,0,0,0,1},
            {0,0,0,1,0},
            {0,0,1,0,0},
            {0,1,0,0,0},
            {1,1,1,1,1}
        });
        FONT.put('3', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {0,0,0,0,1},
            {0,0,1,1,0},
            {0,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('4', new int[][]{
            {0,0,0,1,0},
            {0,0,1,1,0},
            {0,1,0,1,0},
            {1,0,0,1,0},
            {1,1,1,1,1},
            {0,0,0,1,0},
            {0,0,0,1,0}
        });
        FONT.put('5', new int[][]{
            {1,1,1,1,1},
            {1,0,0,0,0},
            {1,1,1,1,0},
            {0,0,0,0,1},
            {0,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('6', new int[][]{
            {0,0,1,1,0},
            {0,1,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('7', new int[][]{
            {1,1,1,1,1},
            {0,0,0,0,1},
            {0,0,0,1,0},
            {0,0,1,0,0},
            {0,1,0,0,0},
            {0,1,0,0,0},
            {0,1,0,0,0}
        });
        FONT.put('8', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        FONT.put('9', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,1},
            {0,0,0,0,1},
            {0,0,0,1,0},
            {0,1,1,0,0}
        });

        // Special characters: -_.;
        FONT.put('-', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {1,1,1,1,1},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0}
        });
        FONT.put('_', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {1,1,1,1,1}
        });
        FONT.put('.', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,1,1,0,0},
            {0,1,1,0,0}
        });
        FONT.put(';', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,1,1,0,0},
            {0,1,1,0,0},
            {0,0,0,0,0},
            {0,1,1,0,0},
            {0,1,0,0,0}
        });
        FONT.put(',', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,1,1,0,0},
            {0,1,0,0,0}
        });
        FONT.put('/', new int[][]{
            {0,0,0,0,1},
            {0,0,0,0,1},
            {0,0,0,1,0},
            {0,0,1,0,0},
            {0,1,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0}
        });
        FONT.put(' ', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,0,0,0,0}
        });
    }

    public TextOverlay(String text, int x, int y) {
        this.text = text;
        this.x = x;
        this.y = y;
    }

    public TextOverlay(String text, int x, int y, Color color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public TextOverlay(String text, int x, int y, Color color, int scale) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
        this.scale = scale;
    }

    @Override
    public void paint(Graphics2D g, HexGridCompositeImageCreator.CartesianBounds bounds) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Transform world coordinates to image coordinates
        int imageX = (int) Math.round(x - bounds.getMinX());
        int imageY = (int) Math.round(y - bounds.getMinZ());

        g.setColor(color);

        int currentX = imageX;
        String upperText = text.toUpperCase();

        for (int i = 0; i < upperText.length(); i++) {
            char c = upperText.charAt(i);
            int[][] charBitmap = FONT.get(c);

            if (charBitmap != null) {
                drawCharacter(g, charBitmap, currentX, imageY);
                currentX += (CHAR_WIDTH + CHAR_SPACING) * scale;
            } else {
                // Unknown character, skip with space width
                currentX += (CHAR_WIDTH + CHAR_SPACING) * scale;
            }
        }
    }

    private void drawCharacter(Graphics2D g, int[][] bitmap, int startX, int startY) {
        for (int row = 0; row < CHAR_HEIGHT; row++) {
            for (int col = 0; col < CHAR_WIDTH; col++) {
                if (bitmap[row][col] == 1) {
                    if (scale == 1) {
                        g.fillRect(startX + col, startY + row, 1, 1);
                    } else {
                        g.fillRect(
                            startX + col * scale,
                            startY + row * scale,
                            scale,
                            scale
                        );
                    }
                }
            }
        }
    }

    /**
     * Calculate the width of the rendered text in pixels
     */
    public int getTextWidth() {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() * (CHAR_WIDTH + CHAR_SPACING) * scale - CHAR_SPACING * scale;
    }

    /**
     * Calculate the height of the rendered text in pixels
     */
    public int getTextHeight() {
        return CHAR_HEIGHT * scale;
    }
}
