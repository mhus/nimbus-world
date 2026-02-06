package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HelloWorldJobExecutor implements JobExecutor {
    @Override
    public String getExecutorName() {
        return "hello-world";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        long sleep = CastUtil.tolong(job.getParameters().get("sleep"), 0) * 1000;
        double errorRate = CastUtil.todouble(job.getParameters().get("errorRate"), 0);
        if (sleep > 0) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JobExecutionException(job, "Job interrupted", e);
            }
        }
        if (errorRate > 0 && Math.random() < errorRate) {
            throw new JobExecutionException(job, "Simulated error");
        }
        return JobResult.success(Map.of(
                "message", "Hello, World!"
        ));
    }
}
