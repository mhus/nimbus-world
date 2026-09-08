package de.mhus.nimbus.world.shared.workflow;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowParameters implements JournalRecord {

    private Map<String, Object> parameters;
}
