package edu.usc.compass.common.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs registered {@link Job}s on their intervals. A failing run is logged and retried next interval.
 * Started by AppContext when COMPASS_JOBS_ENABLED is true and a database is configured.
 * PLATFORM — complete.
 */
public final class JobScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final List<Job> jobs = new ArrayList<>();
    private ScheduledExecutorService executor;

    public JobScheduler register(Job job) {
        jobs.add(job);
        return this;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newScheduledThreadPool(Math.max(1, jobs.size()));
        for (Job job : jobs) {
            long seconds = job.interval().toSeconds();
            executor.scheduleWithFixedDelay(() -> runSafely(job), seconds, seconds, TimeUnit.SECONDS);
            log.info("Scheduled job {} every {}s", job.name(), seconds);
        }
    }

    private static void runSafely(Job job) {
        try {
            job.run();
        } catch (Exception e) {
            log.error("Job {} failed", job.name(), e);
        }
    }

    @Override
    public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
