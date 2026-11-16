package edu.usc.compass.notifications;

import java.util.List;

/** Request bodies for notification routes (Detailed Design 3.5.11). Owner: Raegen. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** read null = all. */
    public record NotificationFilter(Boolean read, Long projectId, Long taskId) {
    }

    public record MarkReadRequest(boolean isRead) {
    }

    /** earlyNotifMinutes null = default (24 hours before a check-in). */
    public record UpdatePreferencesRequest(boolean emailEnabled, boolean inAppEnabled, Integer earlyNotifMinutes) {
    }

    /** recipientUserIds empty = every member of the project's team. */
    public record CustomNotificationRequest(String message, List<Long> recipientUserIds, boolean alsoEmail) {
    }
}
