package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class JobIdRecord implements JournalStringRecord {

    @Getter
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
