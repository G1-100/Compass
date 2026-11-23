package edu.usc.compass.checkins.dao;

import edu.usc.compass.checkins.CheckinDtos.BlockerInput;
import edu.usc.compass.checkins.model.CheckinModels.Blocker;
import edu.usc.compass.checkins.model.CheckinModels.Checkin;
import edu.usc.compass.checkins.model.CheckinModels.CheckinStatus;
import edu.usc.compass.checkins.model.CheckinModels.CheckinUpdate;
import edu.usc.compass.common.Severity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** SQL for check-ins, updates, blockers and reminders. Owner: Graham. */
public class CheckinDao {

    private static final String UPDATES = """
            SELECT u.update_id, u.checkin_id, u.user_id, person.display_name, u.completed_work,
                   u.upcoming_tasks, u.submitted_at, u.version,
                   b.blocker_id, b.description, b.severity, b.task_id, b.resolved_at, b.resolved_by
            FROM checkin_updates u
            JOIN users person ON person.user_id = u.user_id
            LEFT JOIN checkin_blockers b ON b.update_id = u.update_id
            """;

    private static final String DUE_RECIPIENTS = """
            FROM team_memberships m
            LEFT JOIN notification_preferences p ON p.user_id = m.user_id
            WHERE m.team_id = ci.team_id
              AND (COALESCE(p.email_enabled, true) OR COALESCE(p.in_app_enabled, true))
              AND ci.scheduled_at - ?::timestamptz <= COALESCE(p.early_notif_time, ? * interval '1 second')
              AND NOT EXISTS (
                  SELECT 1 FROM checkin_reminders r WHERE r.checkin_id = ci.checkin_id AND r.user_id = m.user_id
              )
            """;

    public Optional<Checkin> findById(Connection c, long checkinId) throws SQLException {
        return findCheckin(c, checkinId, "");
    }

    public Optional<Checkin> findForShare(Connection c, long checkinId) throws SQLException {
        return findCheckin(c, checkinId, " FOR SHARE");
    }

    public Optional<Checkin> findForUpdate(Connection c, long checkinId) throws SQLException {
        return findCheckin(c, checkinId, " FOR UPDATE");
    }

    private Optional<Checkin> findCheckin(Connection c, long checkinId, String lock) throws SQLException {
        try (var ps = c.prepareStatement("SELECT * FROM checkins WHERE checkin_id = ?" + lock)) {
            ps.setLong(1, checkinId);
            return readOneCheckin(ps);
        }
    }

    /** upcoming = scheduled_at >= now ordered ascending; past = < now ordered descending. */
    public List<Checkin> findByTeam(Connection c, long teamId, boolean upcoming, Instant now) throws SQLException {
        String timeFilter = upcoming ? """
                scheduled_at >= ? ORDER BY scheduled_at, checkin_id
                """ : """
                scheduled_at < ? ORDER BY scheduled_at DESC, checkin_id DESC
                """;
        try (var ps = c.prepareStatement("SELECT * FROM checkins WHERE team_id = ? AND " + timeFilter)) {
            ps.setLong(1, teamId);
            ps.setObject(2, utc(now));
            try (var rows = ps.executeQuery()) {
                List<Checkin> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(readCheckin(rows));
                }
                return result;
            }
        }
    }

    public Optional<Checkin> insert(Connection c, long teamId, Instant scheduledAt, String description,
                                    String location, long createdBy) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO checkins (team_id, scheduled_at, description, location, created_by)
                SELECT team_id, ?, ?, ?, ? FROM teams WHERE team_id = ?
                RETURNING *
                """)) {
            ps.setObject(1, utc(scheduledAt));
            ps.setString(2, description);
            ps.setString(3, location);
            ps.setLong(4, createdBy);
            ps.setLong(5, teamId);
            return readOneCheckin(ps);
        }
    }

    public Optional<Checkin> update(Connection c, long checkinId, Instant scheduledAt, String description,
                                    String location, int expectedVersion) throws SQLException {
        try (var ps = c.prepareStatement("""
                UPDATE checkins
                SET scheduled_at = ?, description = ?, location = ?, version = version + 1
                WHERE checkin_id = ? AND version = ?
                RETURNING *
                """)) {
            ps.setObject(1, utc(scheduledAt));
            ps.setString(2, description);
            ps.setString(3, location);
            ps.setLong(4, checkinId);
            ps.setInt(5, expectedVersion);
            return readOneCheckin(ps);
        }
    }

    public Optional<Checkin> setStatus(Connection c, long checkinId, CheckinStatus status,
                                       Instant completedAt, int expectedVersion) throws SQLException {
        try (var ps = c.prepareStatement("""
                UPDATE checkins SET status = ?, completed_at = ?, version = version + 1
                WHERE checkin_id = ? AND version = ?
                RETURNING *
                """)) {
            ps.setString(1, status.name());
            ps.setObject(2, utc(completedAt));
            ps.setLong(3, checkinId);
            ps.setInt(4, expectedVersion);
            return readOneCheckin(ps);
        }
    }

    public List<CheckinUpdate> findUpdates(Connection c, long checkinId) throws SQLException {
        return readUpdates(c, "WHERE u.checkin_id = ?", checkinId);
    }

    public Optional<CheckinUpdate> findMemberUpdateForUpdate(Connection c, long checkinId, long userId) throws SQLException {
        try (var ps = c.prepareStatement("""
                SELECT update_id FROM checkin_updates WHERE checkin_id = ? AND user_id = ? FOR UPDATE
                """)) {
            ps.setLong(1, checkinId);
            ps.setLong(2, userId);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                // Take a fresh snapshot after acquiring the row lock so the blockers match the saved version.
                return Optional.of(findUpdateById(c, rows.getLong(1)));
            }
        }
    }

    public CheckinUpdate findUpdateById(Connection c, long updateId) throws SQLException {
        return readUpdates(c, "WHERE u.update_id = ?", updateId).getFirst();
    }

    public Optional<Long> insertUpdate(Connection c, long checkinId, long userId, String completedWork,
                                       String upcomingTasks, Instant now) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO checkin_updates (checkin_id, user_id, completed_work, upcoming_tasks, submitted_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (checkin_id, user_id) DO NOTHING
                RETURNING update_id
                """)) {
            ps.setLong(1, checkinId);
            ps.setLong(2, userId);
            ps.setString(3, completedWork);
            ps.setString(4, upcomingTasks);
            ps.setObject(5, utc(now));
            try (var rows = ps.executeQuery()) {
                return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
            }
        }
    }

    public boolean updateProgress(Connection c, long updateId, int expectedVersion, String completedWork,
                                  String upcomingTasks, Instant now) throws SQLException {
        try (var ps = c.prepareStatement("""
                UPDATE checkin_updates
                SET completed_work = ?, upcoming_tasks = ?, submitted_at = ?, version = version + 1
                WHERE update_id = ? AND version = ?
                """)) {
            ps.setString(1, completedWork);
            ps.setString(2, upcomingTasks);
            ps.setObject(3, utc(now));
            ps.setLong(4, updateId);
            ps.setInt(5, expectedVersion);
            return ps.executeUpdate() == 1;
        }
    }

    public void deleteOmittedBlockers(Connection c, long updateId, Collection<Long> retainedIds) throws SQLException {
        var ids = c.createArrayOf("int8", retainedIds.toArray(Long[]::new));
        try (var ps = c.prepareStatement("""
                DELETE FROM checkin_blockers WHERE update_id = ? AND NOT (blocker_id = ANY (?))
                """)) {
            ps.setLong(1, updateId);
            ps.setArray(2, ids);
            ps.executeUpdate();
        } finally {
            ids.free();
        }
    }

    public void insertBlocker(Connection c, long updateId, BlockerInput blocker) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO checkin_blockers (description, severity, task_id, update_id) VALUES (?, ?, ?, ?)
                """)) {
            bindBlocker(ps, blocker);
            ps.setLong(4, updateId);
            ps.executeUpdate();
        }
    }

    public void updateBlocker(Connection c, long updateId, BlockerInput blocker) throws SQLException {
        try (var ps = c.prepareStatement("""
                UPDATE checkin_blockers
                SET description = ?, severity = ?, task_id = ?, resolved_at = NULL, resolved_by = NULL
                WHERE update_id = ? AND blocker_id = ?
                """)) {
            bindBlocker(ps, blocker);
            ps.setLong(4, updateId);
            ps.setLong(5, blocker.blockerId());
            ps.executeUpdate();
        }
    }

    /** Use the same update row lock for resolution and progress edits. */
    public Optional<Long> lockBlockerAuthor(Connection c, long checkinId, long blockerId) throws SQLException {
        try (var ps = c.prepareStatement("""
                SELECT u.user_id
                FROM checkin_updates u
                JOIN checkin_blockers b ON b.update_id = u.update_id
                WHERE u.checkin_id = ? AND b.blocker_id = ?
                FOR UPDATE OF u
                """)) {
            ps.setLong(1, checkinId);
            ps.setLong(2, blockerId);
            try (var rows = ps.executeQuery()) {
                return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
            }
        }
    }

    public Optional<Blocker> findBlocker(Connection c, long blockerId) throws SQLException {
        try (var ps = c.prepareStatement("SELECT * FROM checkin_blockers WHERE blocker_id = ?")) {
            ps.setLong(1, blockerId);
            try (var rows = ps.executeQuery()) {
                return rows.next() ? Optional.of(readBlocker(rows)) : Optional.empty();
            }
        }
    }

    public Blocker resolveBlocker(Connection c, long blockerId, long resolvedBy, Instant now) throws SQLException {
        try (var ps = c.prepareStatement("""
                UPDATE checkin_blockers SET resolved_by = ?, resolved_at = ? WHERE blocker_id = ? RETURNING *
                """)) {
            ps.setLong(1, resolvedBy);
            ps.setObject(2, utc(now));
            ps.setLong(3, blockerId);
            try (var rows = ps.executeQuery()) {
                rows.next();
                return readBlocker(rows);
            }
        }
    }

    /** SHARED (Liv, late risk). Unresolved blockers linked to a task. */
    public List<Blocker> findUnresolvedBlockersForTask(Connection c, long taskId) {
        try (var ps = c.prepareStatement("""
                SELECT * FROM checkin_blockers WHERE task_id = ? AND resolved_at IS NULL ORDER BY blocker_id
                """)) {
            ps.setLong(1, taskId);
            try (var rows = ps.executeQuery()) {
                List<Blocker> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(readBlocker(rows));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read unresolved check-in blockers", e);
        }
    }

    /** SHARED (Liv, drift). The user's most recent progress update submission in this team. */
    public Optional<Instant> findLastUpdateTime(Connection c, long teamId, long userId) {
        try (var ps = c.prepareStatement("""
                SELECT max(u.submitted_at) AS last_update
                FROM checkin_updates u
                JOIN checkins ci ON ci.checkin_id = u.checkin_id
                WHERE ci.team_id = ? AND u.user_id = ?
                """)) {
            ps.setLong(1, teamId);
            ps.setLong(2, userId);
            try (var rows = ps.executeQuery()) {
                rows.next();
                return Optional.ofNullable(instant(rows, "last_update"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the last check-in update time", e);
        }
    }

    public List<Long> findRecipients(Connection c, long teamId, boolean leadsOnly) throws SQLException {
        try (var ps = c.prepareStatement("""
                SELECT user_id FROM team_memberships WHERE team_id = ? AND (NOT ? OR role = 'LEAD') ORDER BY user_id
                """)) {
            ps.setLong(1, teamId);
            ps.setBoolean(2, leadsOnly);
            return readIds(ps);
        }
    }

    public List<Long> findCheckinsWithDueReminders(Connection c, Instant now, Duration defaultLead) throws SQLException {
        try (var ps = c.prepareStatement("""
                SELECT ci.checkin_id FROM checkins ci
                WHERE ci.status = 'SCHEDULED' AND ci.scheduled_at > ?
                  AND EXISTS (SELECT 1
                """ + DUE_RECIPIENTS + ") ORDER BY ci.checkin_id")) {
            ps.setObject(1, utc(now));
            ps.setObject(2, utc(now));
            ps.setLong(3, defaultLead.toSeconds());
            return readIds(ps);
        }
    }

    /** Claim eligible members in a consistent order while the caller holds a shared lock on the check-in. */
    public List<Long> claimDueReminders(Connection c, Checkin checkin, Instant now, Duration defaultLead,
                                        int limit) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO checkin_reminders (checkin_id, user_id, sent_at)
                SELECT ci.checkin_id, due.user_id, ?
                FROM checkins ci
                CROSS JOIN LATERAL (SELECT m.user_id
                """ + DUE_RECIPIENTS + """
                    ORDER BY m.user_id LIMIT ?
                ) due
                WHERE ci.checkin_id = ? AND ci.status = 'SCHEDULED' AND ci.scheduled_at > ?
                ORDER BY due.user_id
                ON CONFLICT (checkin_id, user_id) DO NOTHING
                RETURNING user_id
                """)) {
            ps.setObject(1, utc(now));
            ps.setObject(2, utc(now));
            ps.setLong(3, defaultLead.toSeconds());
            ps.setInt(4, limit);
            ps.setLong(5, checkin.checkinId());
            ps.setObject(6, utc(now));
            return readIds(ps);
        }
    }

    public void deleteReminders(Connection c, long checkinId) throws SQLException {
        try (var ps = c.prepareStatement("DELETE FROM checkin_reminders WHERE checkin_id = ?")) {
            ps.setLong(1, checkinId);
            ps.executeUpdate();
        }
    }

    private static List<CheckinUpdate> readUpdates(Connection c, String filter, long id) throws SQLException {
        try (var ps = c.prepareStatement(UPDATES + filter
                + " ORDER BY u.submitted_at DESC, u.update_id DESC, b.blocker_id")) {
            ps.setLong(1, id);
            try (var rows = ps.executeQuery()) {
                List<CheckinUpdate> result = new ArrayList<>();
                CheckinUpdate current = null;
                while (rows.next()) {
                    long updateId = rows.getLong("update_id");
                    if (current == null || current.updateId() != updateId) {
                        current = new CheckinUpdate(updateId, rows.getLong("checkin_id"), rows.getLong("user_id"),
                                rows.getString("display_name"), rows.getString("completed_work"),
                                rows.getString("upcoming_tasks"), instant(rows, "submitted_at"), rows.getInt("version"),
                                new ArrayList<>());
                        result.add(current);
                    }
                    if (rows.getObject("blocker_id") != null) {
                        current.blockers().add(readBlocker(rows));
                    }
                }
                return result;
            }
        }
    }

    private static Optional<Checkin> readOneCheckin(PreparedStatement ps) throws SQLException {
        try (var rows = ps.executeQuery()) {
            return rows.next() ? Optional.of(readCheckin(rows)) : Optional.empty();
        }
    }

    private static Checkin readCheckin(ResultSet rows) throws SQLException {
        return new Checkin(rows.getLong("checkin_id"), rows.getLong("team_id"), rows.getString("description"),
                instant(rows, "scheduled_at"), rows.getString("location"), CheckinStatus.valueOf(rows.getString("status")),
                rows.getLong("created_by"), instant(rows, "created_at"), instant(rows, "completed_at"),
                rows.getInt("version"));
    }

    private static Blocker readBlocker(ResultSet rows) throws SQLException {
        return new Blocker(rows.getLong("blocker_id"), rows.getLong("update_id"), rows.getString("description"),
                Severity.valueOf(rows.getString("severity")), rows.getObject("task_id", Long.class),
                instant(rows, "resolved_at"), rows.getObject("resolved_by", Long.class));
    }

    private static void bindBlocker(PreparedStatement ps, BlockerInput blocker) throws SQLException {
        ps.setString(1, blocker.description());
        ps.setString(2, blocker.severity().name());
        ps.setObject(3, blocker.taskId());
    }

    private static List<Long> readIds(PreparedStatement ps) throws SQLException {
        try (var rows = ps.executeQuery()) {
            List<Long> result = new ArrayList<>();
            while (rows.next()) {
                result.add(rows.getLong(1));
            }
            return result;
        }
    }

    private static Instant instant(ResultSet rows, String field) throws SQLException {
        OffsetDateTime value = rows.getObject(field, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
