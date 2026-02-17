package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateJobRecord implements JournalRecord {

    private String jobId;
    private String executor;
    private String type;
    private String location;
    private Map<String,String > parameters;
}
