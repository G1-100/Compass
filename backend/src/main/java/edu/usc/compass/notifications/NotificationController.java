package edu.usc.compass.notifications;

import edu.usc.compass.common.web.Router;
import edu.usc.compass.notifications.NotificationDtos.CustomNotificationRequest;
import edu.usc.compass.notifications.NotificationDtos.MarkReadRequest;
import edu.usc.compass.notifications.NotificationDtos.NotificationFilter;
import edu.usc.compass.notifications.NotificationDtos.UpdatePreferencesRequest;

import java.util.Map;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Notifications (Detailed Design 3.5.11). Owner: Raegen. */
public final class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    public void register(Router r) {
        r.get("/api/notifications", SIGNED_IN, ctx -> notifications.list(ctx.auth(), new NotificationFilter(
                ctx.query("read").map(Boolean::parseBoolean).orElse(null),
                ctx.queryLong("projectId").orElse(null),
                ctx.queryLong("taskId").orElse(null)), ctx.page()));
        r.get("/api/notifications/unread-count", SIGNED_IN, ctx -> Map.of("count", notifications.unreadCount(ctx.auth())));
        r.patch("/api/notifications/{notifId}", SIGNED_IN, ctx -> notifications.markRead(ctx.auth(), ctx.pathLong("notifId"),
                ctx.body(MarkReadRequest.class).isRead()));
        r.get("/api/users/me/notification-preferences", SIGNED_IN, ctx -> notifications.getPreferences(ctx.auth()));
        r.put("/api/users/me/notification-preferences", SIGNED_IN,
                ctx -> notifications.updatePreferences(ctx.auth(), ctx.body(UpdatePreferencesRequest.class)));
        r.post("/api/projects/{projectId}/notifications", SIGNED_IN, ctx -> {
            notifications.sendCustom(ctx.auth(), ctx.pathLong("projectId"), ctx.body(CustomNotificationRequest.class));
            return null;
        });
    }
}
