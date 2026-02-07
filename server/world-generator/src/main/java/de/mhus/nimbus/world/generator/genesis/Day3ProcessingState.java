package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.workflow.JournalRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Day3ProcessingState implements JournalRecord {
    private List<HexCoordinate> coordinates;
    private List<String> flatIds; // flatId for each coordinate
    private String currentPhase; // createAll, groundAll, blenderAll, terrainAll, exportAll, imagesAll
    private int currentIndex;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HexCoordinate {
        private int q;
        private int r;
    }
}
