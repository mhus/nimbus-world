package de.mhus.nimbus.shared.types;

import de.mhus.nimbus.generated.types.Block;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.util.Optional;

/**
 * Define a block with its properties.
 * Format: blockTypeId@s:state@o:x,y,z@r:rx,ry@l:level@f:faceVisibility
 * Example: "n:g@s:open@o:0,1,0@r:0,90@l:2@f:north,south,fix"
 */
@Getter
public class BlockDef {
    private String blockTypeId;
    private int state;
    private int[] offsets;
    private double[] rotations;
    private Integer level;
    private Integer faceVisibility;

    private BlockDef(String blockTypeId, int state, int[] offsets, double[] rotations, Integer level, Integer faceVisibility) {
        this.blockTypeId = blockTypeId;
        this.state = state;
        this.offsets = offsets;
        this.rotations = rotations;
        this.level = level;
        this.faceVisibility = faceVisibility;
    }

    public void fillBlock(Block block) {
        block.setBlockTypeId(blockTypeId);
        block.setStatus(state);
        if (offsets != null) {
            block.setOffsets(java.util.Arrays.stream(offsets).mapToObj(i -> (float)i).toList());
        }
        if (rotations != null) {
            var builder = de.mhus.nimbus.generated.types.RotationXY.builder();
            if (rotations.length > 0)
                builder.x(rotations[0]);
            if (rotations.length > 1)
                builder.y(rotations[1]);
            block.setRotation(builder.build());
        }
        if (level != null) {
            block.setLevel(level);
        }
        if (faceVisibility != null) {
            block.setFaceVisibility(faceVisibility);
        }
    }

    public static BlockDef value(String blockId, int state, int[] offsets, double[] rotations, Integer level, Integer faceVisibility) {
        return new BlockDef(blockId, state, offsets, rotations, level, faceVisibility);
    }

    public static Optional<BlockDef> of(String blockDef) {
        if (blockDef == null) return Optional.empty();
        try {
            String[] parts = blockDef.split("@");
            String blockTypeId = parts[0];
            if (!validateBlockTypeId(blockTypeId))
                return Optional.empty();
            int state = 0;
            int[] offsets = null;
            double[] rotations = null;
            Integer level = null;
            Integer faceVisibility = null;
            for (String part : parts) {
                if (part.equals(parts[0])) continue; // blockId
                if (part.startsWith("s:")) {
                    state = switch (part.substring(2).toLowerCase()) {
                        case "default" -> 0;
                        case "open" -> 1;
                        case "closed" -> 2;
                        case "locked" -> 3;
                        case "destroyed" -> 5;
                        case "winter" -> 10;
                        case "spring" -> 11;
                        case "summer" -> 12;
                        case "autumn" -> 13;
                        default -> Integer.parseInt(part.substring(2));
                    };
                } else if (part.startsWith("o:")) {
                    String[] offsetParts = part.substring(2).split(",");
                    offsets = new int[offsetParts.length];
                    for (int j = 0; j < offsetParts.length; j++) {
                        offsets[j] = Integer.parseInt(offsetParts[j]);
                    }
                } else if (part.startsWith("r:")) {
                    String[] rotationParts = part.substring(2).split(",");
                    rotations = new double[rotationParts.length];
                    for (int j = 0; j < rotationParts.length; j++) {
                        rotations[j] = Double.parseDouble(rotationParts[j]);
                    }
                } else if (part.startsWith("l:")) {
                    level = Integer.parseInt(part.substring(2));
                } else if (part.startsWith("f:")) {
                    String s = part.substring(2).toLowerCase();
                    if (s.matches("[0-9]+"))
                        faceVisibility = Integer.parseInt(part.substring(2));
                    else {
                        int f = 0;
                        if (s.contains("north")) f |= 1 << 0;
                        if (s.contains("south")) f |= 1 << 1;
                        if (s.contains("east")) f |= 1 << 2;
                        if (s.contains("west")) f |= 1 << 3;
                        if (s.contains("up")) f |= 1 << 4;
                        if (s.contains("down")) f |= 1 << 5;
                        if (s.contains("fix")) f |= 1 << 6;
                        faceVisibility = f;
                    }
                }
                // Unbekannte Prefixes werden ignoriert
            }
            return Optional.of(new BlockDef(blockTypeId, state, offsets, rotations, level, faceVisibility));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean validateBlockTypeId(String blockId) {
        // Einfache Validierung: Block-ID darf nur Buchstaben, Zahlen, Unterstriche und Doppelpunkte enthalten
        return Strings.isNotBlank(blockId) && blockId.matches("[a-zA-Z0-9_:]+");
    }

}
