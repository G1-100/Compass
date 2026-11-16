package edu.usc.compass.notifications;

import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.Todo;

/**
 * Sends email over SMTP with Jakarta Mail (org.eclipse.angus:angus-mail is in pom.xml).
 * Locally point SMTP_HOST/SMTP_PORT at Mailpit (localhost:1025) and view mail at http://localhost:8025.
 * Owner: Raegen.
 */
public class SmtpMailer implements Mailer {

    private final AppConfig config;

    public SmtpMailer(AppConfig config) {
        this.config = config;
    }

    /** Build a jakarta.mail.Session from config (host, port, auth if username set, STARTTLS), then Transport.send. */
    @Override
    public void send(String toEmail, String subject, String body) throws Exception {
        throw Todo.of("Raegen", "SmtpMailer.send");
    }
}
