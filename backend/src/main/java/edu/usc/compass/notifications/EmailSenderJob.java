package edu.usc.compass.notifications;

import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.jobs.Job;
import edu.usc.compass.notifications.dao.EmailQueueDao;

import java.time.Clock;
import java.time.Duration;

/**
 * Background sender for email_queue. Owner: Raegen. Tests: Stress 6.
 * Each run: claim a batch of PENDING rows whose next_attempt_at has passed (UPDATE ... SET status='SENDING'
 * ... FOR UPDATE SKIP LOCKED so overlapping runs never send twice), send each via Mailer, then mark SENT,
 * or on failure increment attempts and either reschedule (backoff) or mark FAILED after Rules.MAX_EMAIL_ATTEMPTS.
 */
public class EmailSenderJob implements Job {

    private final Database db;
    private final EmailQueueDao emailQueueDao;
    private final Mailer mailer;
    private final Clock clock;

    public EmailSenderJob(Database db, EmailQueueDao emailQueueDao, Mailer mailer, Clock clock) {
        this.db = db;
        this.emailQueueDao = emailQueueDao;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "email-sender";
    }

    @Override
    public Duration interval() {
        return Duration.ofSeconds(30);
    }

    @Override
    public void run() {
        Todo.noop("Raegen", "EmailSenderJob.run");
    }
}
