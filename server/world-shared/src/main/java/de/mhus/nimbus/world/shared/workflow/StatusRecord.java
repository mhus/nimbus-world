package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Workflow status stored in journal.
 * The last journal entry of type "status" represents the current workflow status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusRecord implements JournalStringRecord {

    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String TERMINATED = "TERMINATED";
    public static final String CREATED = "CREATED";


    /**
     * Current status of the workflow.
     * Examples: "CREATED", "RUNNING", "COMPLETED", "FAILED", "PAUSED"
     */
    private String status;

    @Override
    public String entryToString() {
        return status;
    }

    @Override
    public void stringToRecord(String data) {
        this.status = data;
    }
}
