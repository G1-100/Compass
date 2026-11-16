package edu.usc.compass.checkins.dao;

import edu.usc.compass.checkins.CheckinDtos.BlockerInput;
import edu.usc.compass.checkins.model.CheckinModels.Blocker;
import edu.usc.compass.checkins.model.CheckinModels.Checkin;
import edu.usc.compass.checkins.model.CheckinModels.CheckinStatus;
import edu.usc.compass.checkins.model.CheckinModels.CheckinUpdate;
import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SQL for check-ins, updates, blockers and reminders. Owner: Graham. */
public class CheckinDao {

    public Optional<Checkin> findById(Connection c, long checkinId) {
        throw Todo.of("Graham", "CheckinDao.findById");
    }

    public Optional<Checkin> findForUpdate(Connection c, long checkinId) {
        throw Todo.of("Graham", "CheckinDao.findForUpdate");
    }

    /** upcoming = scheduled_at >= now ordered ascending; past = < now ordered descending. */
    public List<Checkin> findByTeam(Connection c, long teamId, boolean upcoming, Instant now) {
        throw Todo.of("Graham", "CheckinDao.findByTeam");
    }

    public long insert(Connection c, long teamId, Instant scheduledAt, String description, String location, long createdBy) {
        throw Todo.of("Graham", "CheckinDao.insert");
    }

    public boolean update(Connection c, long checkinId, Instant scheduledAt, String description, String location, int expectedVersion) {
        throw Todo.of("Graham", "CheckinDao.update");
    }

    public void setStatus(Connection c, long checkinId, CheckinStatus status, Instant completedAt) {
        throw Todo.of("Graham", "CheckinDao.setStatus");
    }

    public List<CheckinUpdate> findUpdates(Connection c, long checkinId) {
        throw Todo.of("Graham", "CheckinDao.findUpdates");
    }

    /** INSERT ... ON CONFLICT (checkin_id, user_id) DO UPDATE, bumping version. Returns update_id. */
    public long upsertUpdate(Connection c, long checkinId, long userId, String completedWork, String upcomingTasks) {
        throw Todo.of("Graham", "CheckinDao.upsertUpdate");
    }

    public void deleteBlockers(Connection c, long updateId) {
        throw Todo.of("Graham", "CheckinDao.deleteBlockers");
    }

    public void insertBlocker(Connection c, long updateId, BlockerInput blocker) {
        throw Todo.of("Graham", "CheckinDao.insertBlocker");
    }

    public Optional<Blocker> findBlocker(Connection c, long blockerId) {
        throw Todo.of("Graham", "CheckinDao.findBlocker");
    }

    /** Author user id of the update a blocker belongs to (for the "author or lead" check). */
    public long findBlockerAuthorId(Connection c, long blockerId) {
        throw Todo.of("Graham", "CheckinDao.findBlockerAuthorId");
    }

    public void resolveBlocker(Connection c, long blockerId, long resolvedBy, Instant now) {
        throw Todo.of("Graham", "CheckinDao.resolveBlocker");
    }

    /** SHARED (Liv, late risk). Unresolved blockers linked to a task. */
    public List<Blocker> findUnresolvedBlockersForTask(Connection c, long taskId) {
        throw Todo.of("Graham", "CheckinDao.findUnresolvedBlockersForTask");
    }

    /** SHARED (Liv, drift). The user's most recent progress update submission in this team. */
    public Optional<Instant> findLastUpdateTime(Connection c, long teamId, long userId) {
        throw Todo.of("Graham", "CheckinDao.findLastUpdateTime");
    }

    /** Reminder job: INSERT ... ON CONFLICT DO NOTHING; true if this call inserted the row (send the email). */
    public boolean claimReminder(Connection c, long checkinId, long userId) {
        throw Todo.of("Graham", "CheckinDao.claimReminder");
    }

    /** Called on reschedule so reminders go out again for the new time. */
    public void deleteReminders(Connection c, long checkinId) {
        throw Todo.of("Graham", "CheckinDao.deleteReminders");
    }
}
