package edu.usc.compass.common.jobs;

import java.time.Duration;

/**
 * A background task run on a fixed interval by {@link JobScheduler}.
 * Implemented by EmailSenderJob (Raegen), CheckinReminderJob (Graham), RiskDetectionJob (Liv).
 * Jobs must be safe to run twice at once (overlapping runs) — use DB constraints, not memory, to dedupe.
 */
public interface Job {

    String name();

    Duration interval();

    void run() throws Exception;
}
