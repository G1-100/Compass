package edu.usc.compass.notifications.dao;

import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.notifications.NotificationDtos.NotificationFilter;
import edu.usc.compass.notifications.model.NotificationModels.Notification;
import edu.usc.compass.notifications.model.NotificationModels.NotificationPreference;
import edu.usc.compass.notifications.model.NotificationModels.NotificationType;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** SQL for notifications and notification_preferences. Owner: Raegen. */
public class NotificationDao {

    public long insert(Connection c, long userId, NotificationType type, Long projectId, Long taskId, String message) {
        throw Todo.of("Raegen", "NotificationDao.insert");
    }

    public List<Notification> findForUser(Connection c, long userId, NotificationFilter filter, PageRequest page) {
        throw Todo.of("Raegen", "NotificationDao.findForUser");
    }

    public long countForUser(Connection c, long userId, NotificationFilter filter) {
        throw Todo.of("Raegen", "NotificationDao.countForUser");
    }

    public long countUnread(Connection c, long userId) {
        throw Todo.of("Raegen", "NotificationDao.countUnread");
    }

    /** UPDATE ... WHERE notif_id = ? AND user_id = ?; false if not the user's notification. */
    public boolean setRead(Connection c, long notifId, long userId, boolean isRead) {
        throw Todo.of("Raegen", "NotificationDao.setRead");
    }

    public Optional<Notification> findById(Connection c, long notifId, long userId) {
        throw Todo.of("Raegen", "NotificationDao.findById");
    }

    public Optional<NotificationPreference> findPreferences(Connection c, long userId) {
        throw Todo.of("Raegen", "NotificationDao.findPreferences");
    }

    public void upsertPreferences(Connection c, NotificationPreference prefs) {
        throw Todo.of("Raegen", "NotificationDao.upsertPreferences");
    }
}
