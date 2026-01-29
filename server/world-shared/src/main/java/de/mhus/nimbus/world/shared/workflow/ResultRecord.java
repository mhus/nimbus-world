package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
public class ResultRecord implements JournalStringRecord {

    @Getter
    private String result;

    public ResultRecord(Map<String, Object> result) {
        this(CastUtil.mapToString(result));
    }

    @Override
    public String entryToString() {
        return result;
    }

    @Override
    public void stringToRecord(String data) {
        this.result = data;
    }
}
