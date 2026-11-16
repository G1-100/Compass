package edu.usc.compass.notifications;

/**
 * Sends one email right now. Only EmailSenderJob calls this; everyone else uses EmailQueue.
 * Implementations: SmtpMailer (real, Raegen) and LoggingMailer (prints to console).
 */
public interface Mailer {

    /** Throws on failure so the sender job can retry. */
    void send(String toEmail, String subject, String body) throws Exception;
}
