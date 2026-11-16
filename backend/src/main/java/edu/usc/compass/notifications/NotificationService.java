package edu.usc.compass.notifications;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationDtos.CustomNotificationRequest;
import edu.usc.compass.notifications.NotificationDtos.NotificationFilter;
import edu.usc.compass.notifications.NotificationDtos.UpdatePreferencesRequest;
import edu.usc.compass.notifications.dao.NotificationDao;
import edu.usc.compass.notifications.model.NotificationModels.Notification;
import edu.usc.compass.notifications.model.NotificationModels.NotificationPreference;
import edu.usc.compass.notifications.model.NotificationModels.NotificationRequest;
import edu.usc.compass.notifications.model.NotificationModels.NotificationType;
import edu.usc.compass.projects.ProjectService;

import java.sql.Connection;

/** In-app notifications and preferences. Owner: Raegen. Tests: Stress 3, 5, 8. */
public class NotificationService {

    private final Database db;
    private final NotificationDao notificationDao;
    private final EmailQueue emailQueue;
    private final ProjectService projects;

    public NotificationService(Database db, NotificationDao notificationDao, EmailQueue emailQueue, ProjectService projects) {
        this.db = db;
        this.notificationDao = notificationDao;
        this.emailQueue = emailQueue;
        this.projects = projects;
    }

    /**
     * SHARED CONTRACT — every feature calls this to notify people, inside its own transaction.
     * For each recipient: if in-app is enabled, createNotification; if req.alsoEmail() and email is enabled,
     * enqueue EmailTemplates.fromNotification. Must return quickly (never sends email directly).
     * Stub: no-op until implemented.
     */
    public void notify(Connection c, NotificationRequest req) {
        Todo.noop("Raegen", "NotificationService.notify");
    }

    /** Inserts one notifications row (named in the Testing Plan as NotificationService.createNotification()). */
    public void createNotification(Connection c, long userId, NotificationType type, Long projectId, Long taskId, String message) {
        Todo.noop("Raegen", "NotificationService.createNotification");
    }

    /** GET /api/notifications?status=&projectId=&taskId=. Only the caller's own notifications. Newest first. */
    public Page<Notification> list(AuthContext ctx, NotificationFilter filter, PageRequest page) {
        throw Todo.of("Raegen", "NotificationService.list");
    }

    /** GET /api/notifications/unread-count -> {"count": n} */
    public long unreadCount(AuthContext ctx) {
        throw Todo.of("Raegen", "NotificationService.unreadCount");
    }

    /** PATCH /api/notifications/{id}. Only the owner (404 for anyone else). */
    public Notification markRead(AuthContext ctx, long notifId, boolean isRead) {
        throw Todo.of("Raegen", "NotificationService.markRead");
    }

    /** GET /api/users/me/notification-preferences. Defaults if no row exists. */
    public NotificationPreference getPreferences(AuthContext ctx) {
        throw Todo.of("Raegen", "NotificationService.getPreferences");
    }

    /** PUT /api/users/me/notification-preferences. */
    public NotificationPreference updatePreferences(AuthContext ctx, UpdatePreferencesRequest req) {
        throw Todo.of("Raegen", "NotificationService.updatePreferences");
    }

    /** POST /api/projects/{projectId}/notifications. Team lead sends a custom message to chosen members (or all). */
    public void sendCustom(AuthContext ctx, long projectId, CustomNotificationRequest req) {
        throw Todo.of("Raegen", "NotificationService.sendCustom");
    }
}
