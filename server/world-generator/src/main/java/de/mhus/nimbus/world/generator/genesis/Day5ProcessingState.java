package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.workflow.JournalRecord;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Day5ProcessingState implements JournalRecord {
    private List<HexCoordinate> coordinates;
    private int currentIndex;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HexCoordinate {
        private int q;
        private int r;
    }
}
