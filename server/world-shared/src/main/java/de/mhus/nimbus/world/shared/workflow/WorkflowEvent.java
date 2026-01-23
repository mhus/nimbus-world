package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Event sent to a workflow for processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEvent implements JournalRecord {

    public static final String START = "START";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    /**
     * Event name/type.
     */
    private String event;

    /**
     * Event data payload.
     */
    @Builder.Default
    private Map<String, String> data = new HashMap<>();
}
