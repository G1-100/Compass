package edu.usc.compass.notifications;

import edu.usc.compass.common.Todo;
import edu.usc.compass.notifications.dao.EmailQueueDao;
import edu.usc.compass.notifications.model.NotificationModels.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * SHARED CONTRACT — the only way any feature sends email. Inserts an email_queue row inside the caller's
 * transaction and returns immediately; EmailSenderJob delivers it later. A failed send never affects the
 * original request. Owner: Raegen.
 */
public class EmailQueue {

    private static final Logger log = LoggerFactory.getLogger(EmailQueue.class);

    private final EmailQueueDao emailQueueDao;

    public EmailQueue(EmailQueueDao emailQueueDao) {
        this.emailQueueDao = emailQueueDao;
    }

    /**
     * Queue one email. Stub: logs the email instead (so during development you can read activation and reset
     * codes in the Tomcat console) and inserts nothing.
     */
    public void enqueue(Connection c, String toEmail, EmailMessage message) {
        // TODO(Raegen): emailQueueDao.insert(c, toEmail, message.subject(), message.body());
        Todo.noop("Raegen", "EmailQueue.enqueue");
        log.info("[EMAIL STUB] to={} subject={}\n{}", toEmail, message.subject(), message.body());
    }
}
