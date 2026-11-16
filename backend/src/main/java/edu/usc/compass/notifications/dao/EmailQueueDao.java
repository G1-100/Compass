package edu.usc.compass.notifications.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.notifications.model.NotificationModels.QueuedEmail;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;

/** SQL for email_queue. Owner: Raegen. */
public class EmailQueueDao {

    public void insert(Connection c, String toEmail, String subject, String body) {
        throw Todo.of("Raegen", "EmailQueueDao.insert");
    }

    /** Atomically claim up to {@code limit} due PENDING rows (FOR UPDATE SKIP LOCKED) and mark them SENDING. */
    public List<QueuedEmail> claimDue(Connection c, Instant now, int limit) {
        throw Todo.of("Raegen", "EmailQueueDao.claimDue");
    }

    public void markSent(Connection c, long emailId, Instant now) {
        throw Todo.of("Raegen", "EmailQueueDao.markSent");
    }

    /** attempts + 1; status back to PENDING with nextAttemptAt, or FAILED if out of attempts. */
    public void markFailed(Connection c, long emailId, String error, Instant nextAttemptAt, boolean giveUp) {
        throw Todo.of("Raegen", "EmailQueueDao.markFailed");
    }
}
