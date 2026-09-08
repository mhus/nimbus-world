package de.mhus.nimbus.world.shared.workflow;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateJobRecord implements JournalRecord {

    private String jobId;
    private String executor;
    private String type;
    private String location;
    private Map<String, String> parameters;
}
