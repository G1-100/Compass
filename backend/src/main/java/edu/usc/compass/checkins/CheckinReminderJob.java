package edu.usc.compass.checkins;

import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.Rules;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.jobs.Job;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.notifications.model.NotificationModels.NotificationRequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static edu.usc.compass.notifications.model.NotificationModels.NotificationType.CHECKIN_REMINDER;

/**
 * Sends check-in reminder emails. Owner: Graham. Tests: Stress 7.
 * Each member gets one reminder per check-in at (scheduledAt - early_notif_time), default 24 hours before.
 * A claim and its notification are committed together.
 */
public class CheckinReminderJob implements Job {

    private static final int BATCH_SIZE = 250;

    private final Database db;
    private final CheckinDao checkinDao;
    private final NotificationService notifications;
    private final Clock clock;

    public CheckinReminderJob(Database db, CheckinDao checkinDao, NotificationService notifications, Clock clock) {
        this.db = db;
        this.checkinDao = checkinDao;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "checkin-reminders";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(5);
    }

    /** Find SCHEDULED check-ins whose reminder time has passed, claim each member's reminder, notify + email. */
    @Override
    public void run() {
        var dueCheckins = db.withConnection(c -> checkinDao.findCheckinsWithDueReminders(c, Instant.now(clock),
                Rules.DEFAULT_CHECKIN_REMINDER_LEAD));
        for (long checkinId : dueCheckins) {
            int claimed;
            do {
                claimed = db.inTransaction(c -> queueReminders(c, checkinId));
            } while (claimed == BATCH_SIZE);
        }
    }

    private int queueReminders(Connection c, long checkinId) throws SQLException {
        var current = checkinDao.findForShare(c, checkinId);
        if (current.isEmpty()) return 0;
        var checkin = current.get();
        Instant now = Instant.now(clock);
        var recipients = checkinDao.claimDueReminders(c, checkin, now, Rules.DEFAULT_CHECKIN_REMINDER_LEAD, BATCH_SIZE);
        if (!recipients.isEmpty()) {
            notifications.notify(c, new NotificationRequest(recipients, CHECKIN_REMINDER, null, null,
                    "Reminder: your check-in is scheduled for " + checkin.scheduledAt() + ".", true));
        }
        return recipients.size();
    }
}
