package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.workflow.JournalRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompositionDocIdRecord implements JournalRecord {
    private String value;
}
