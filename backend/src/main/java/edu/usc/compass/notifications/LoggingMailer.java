package edu.usc.compass.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development mailer that prints emails to the console instead of sending them. */
public class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    @Override
    public void send(String toEmail, String subject, String body) {
        log.info("[MAIL] to={} subject={}\n{}", toEmail, subject, body);
    }
}
