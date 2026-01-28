package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.workflow.JournalRecord;
import de.mhus.nimbus.world.shared.workflow.JournalStringRecord;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ModelId implements JournalStringRecord {

    private String modelId;

    @Override
    public String entryToString() {
        return modelId;
    }

    @Override
    public void stringToRecord(String data) {
        this.modelId = data;
    }
}
