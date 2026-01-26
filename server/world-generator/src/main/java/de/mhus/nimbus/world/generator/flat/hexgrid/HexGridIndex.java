package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.chat.WChatService;
import de.mhus.nimbus.world.shared.world.WHexGrid;

import java.util.List;
import java.util.Map;

public class HexGridIndex {
    private final Map<String, WHexGrid> index = new java.util.HashMap<>();

    public HexGridIndex(List<WHexGrid> grids) {
        for (var grid : grids) {
            index.put(grid.getPosition(), grid);
        }
    }

    public WHexGrid getGrid(String position) {
        return index.get(position);
    }

    public WHexGrid getGrid(HexVector2 position) {
        return index.get(TypeUtil.toStringHexCoord(position));
    }

}
