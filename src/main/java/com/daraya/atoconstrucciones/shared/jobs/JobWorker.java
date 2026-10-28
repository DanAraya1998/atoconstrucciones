package com.daraya.atoconstrucciones.shared.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ato.jobs.enabled", havingValue="true", matchIfMissing=true)
public class JobWorker {
    private final JobService jobs;
    public JobWorker(JobService jobs) { this.jobs = jobs; }
    @Scheduled(fixedDelayString="${ato.jobs.poll-ms:2000}")
    public void poll() {
        for (var lease : jobs.claim()) {
            try { jobs.complete(lease); }
            catch (RuntimeException exception) { jobs.fail(lease, "EXECUTION_FAILED"); }
        }
    }
    @Configuration @EnableScheduling
    static class Scheduling {}
}
