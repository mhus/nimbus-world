package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Workflow status stored in journal.
 * The last journal entry of type "status" represents the current workflow status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStart implements JournalStringEntry {

    private String workflow;

    @Override
    public String entryToString() {
        return workflow;
    }

    @Override
    public void stringToEntry(String data) {
        workflow = data;
    }

}
