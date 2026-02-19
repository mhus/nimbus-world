package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.workflow.JournalRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Day2PlanningState implements JournalRecord {
    private String compositionDocumentId;
    private String totalGrids;
}
