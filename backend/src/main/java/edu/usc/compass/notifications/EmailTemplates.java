package edu.usc.compass.notifications;

import edu.usc.compass.common.Todo;
import edu.usc.compass.notifications.model.NotificationModels.EmailMessage;

import java.time.Instant;

/**
 * Builds the subject and plain-text body for every email. Links point at PUBLIC_SITE_URL (the Vercel site),
 * never the servlet host, and never contain session ids, codes or invitation details.
 * Owner: Raegen. Other features call these; until implemented they return a placeholder message.
 */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    /** "Your Compass activation code is 123456. It expires in 1 hour." */
    public static EmailMessage activationCode(String code) {
        Todo.noop("Raegen", "EmailTemplates.activationCode");
        return new EmailMessage("Your Compass activation code", "Code: " + code);
    }

    public static EmailMessage passwordResetCode(String code) {
        Todo.noop("Raegen", "EmailTemplates.passwordResetCode");
        return new EmailMessage("Reset your Compass password", "Code: " + code);
    }

    /** Link is siteUrl + "/invitations" (no token — the invite is matched by email after sign-in). */
    public static EmailMessage invitation(String teamName, String inviterName, String siteUrl) {
        Todo.noop("Raegen", "EmailTemplates.invitation");
        return new EmailMessage("You're invited to " + teamName, siteUrl + "/invitations");
    }

    public static EmailMessage checkinReminder(String teamName, Instant scheduledAt, String location, String siteUrl) {
        Todo.noop("Raegen", "EmailTemplates.checkinReminder");
        return new EmailMessage("Check-in reminder: " + teamName, String.valueOf(scheduledAt));
    }

    /** Email version of any in-app notification. */
    public static EmailMessage fromNotification(String message, String siteUrl) {
        Todo.noop("Raegen", "EmailTemplates.fromNotification");
        return new EmailMessage("Compass notification", message);
    }
}
