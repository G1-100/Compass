package edu.usc.compass.checkins;

import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.jobs.Job;
import edu.usc.compass.notifications.NotificationService;

import java.time.Clock;
import java.time.Duration;

/**
 * Sends check-in reminder emails. Owner: Graham. Tests: Stress 7.
 * Each member gets one reminder per check-in at (scheduledAt - early_notif_time), default 24 hours before.
 * Use CheckinDao.claimReminder so overlapping runs never double-send.
 */
public class CheckinReminderJob implements Job {

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
        Todo.noop("Graham", "CheckinReminderJob.run");
    }
}
