package edu.usc.compass.notifications.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Notification and email records (tables notifications, notification_preferences, email_queue). Owner: Raegen. */
public final class NotificationModels {

    private NotificationModels() {
    }

    /** SHARED CONTRACT — add a value when your feature needs a new kind of notification. */
    public enum NotificationType {
        TASK_ASSIGNED, TASK_UNASSIGNED, TASK_UPDATED, TASK_STATUS_CHANGED, MENTION,
        SUGGESTION_SUBMITTED, SUGGESTION_APPROVED, SUGGESTION_REJECTED,
        SPRINT_COMPLETED, TASK_MOVED,
        CHECKIN_SCHEDULED, CHECKIN_UPDATED, CHECKIN_CANCELED, CHECKIN_COMPLETED, CHECKIN_REMINDER,
        INVITATION_RECEIVED, MEMBER_JOINED, MEMBER_LEFT, MEMBER_REMOVED,
        WORKLOAD_ALERT, CUSTOM
    }

    public record Notification(long notifId, long userId, NotificationType notifType, Long projectId, Long taskId,
                               String message, boolean isRead, Instant readAt, Instant emailSentAt, Instant createdAt) {
    }

    /** earlyNotifTime null = default 24 hours before a check-in. */
    public record NotificationPreference(long userId, boolean emailEnabled, boolean inAppEnabled, Duration earlyNotifTime) {
    }

    /**
     * SHARED CONTRACT — what other features pass to NotificationService.notify.
     *
     * @param alsoEmail queue an email too (respecting each recipient's email preference)
     */
    public record NotificationRequest(List<Long> recipientUserIds, NotificationType type, Long projectId, Long taskId,
                                      String message, boolean alsoEmail) {
    }

    public enum EmailStatus {
        PENDING, SENDING, SENT, FAILED
    }

    public record QueuedEmail(long emailId, String toEmail, String subject, String bodyText, EmailStatus status,
                              int attempts, Instant nextAttemptAt, String lastError) {
    }

    /** Subject + plain-text body, built by EmailTemplates. */
    public record EmailMessage(String subject, String body) {
    }
}
