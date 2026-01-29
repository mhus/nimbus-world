package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class CallerJobRecord implements JournalStringRecord {

    private String jobId;

    @Override
    public String entryToString() {
        return jobId;
    }

    @Override
    public void stringToRecord(String data) {
        this.jobId = data;
    }
}
