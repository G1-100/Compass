package edu.usc.compass.auth.dao;

import edu.usc.compass.auth.model.AuthEnums.LockoutAction;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

/** SQL for login_lockouts. Owner: Gavin. Emails are stored lower-cased by LoginLockoutService. */
public class LoginLockoutDao {

    public record LockoutRow(String email, LockoutAction action, int failedCount, Instant windowStart, Instant lockedUntil) {
    }

    /** SELECT ... FOR UPDATE so concurrent failures are counted one at a time. */
    public Optional<LockoutRow> findForUpdate(Connection c, String email, LockoutAction action) {
        return Jdbc.one(c, """
                        SELECT email, action, failed_count, window_start, locked_until
                        FROM login_lockouts
                        WHERE email = ? AND action = ?
                        FOR UPDATE""",
                ps -> {
                    ps.setString(1, email);
                    ps.setString(2, action.name());
                },
                rs -> new LockoutRow(rs.getString("email"), LockoutAction.valueOf(rs.getString("action")),
                        rs.getInt("failed_count"), Jdbc.getInstant(rs, "window_start"),
                        Jdbc.getInstant(rs, "locked_until")));
    }

    /**
     * Inserts an empty counter row if none exists. FOR UPDATE cannot lock a row that does not exist yet,
     * so recordFailure calls this first; afterwards every concurrent attempt queues on the same row lock.
     */
    public void ensureExists(Connection c, String email, LockoutAction action, Instant now) {
        Jdbc.update(c, """
                INSERT INTO login_lockouts (email, action, failed_count, window_start)
                VALUES (?, ?, 0, ?)
                ON CONFLICT (email, action) DO NOTHING""", ps -> {
            ps.setString(1, email);
            ps.setString(2, action.name());
            Jdbc.setInstant(ps, 3, now);
        });
    }

    /** INSERT ... ON CONFLICT (email, action) DO UPDATE. */
    public void upsert(Connection c, LockoutRow row) {
        Jdbc.update(c, """
                INSERT INTO login_lockouts (email, action, failed_count, window_start, locked_until)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (email, action) DO UPDATE
                SET failed_count = EXCLUDED.failed_count,
                    window_start = EXCLUDED.window_start,
                    locked_until = EXCLUDED.locked_until""", ps -> {
            ps.setString(1, row.email());
            ps.setString(2, row.action().name());
            ps.setInt(3, row.failedCount());
            Jdbc.setInstant(ps, 4, row.windowStart());
            Jdbc.setInstant(ps, 5, row.lockedUntil());
        });
    }

    public void delete(Connection c, String email, LockoutAction action) {
        Jdbc.update(c, "DELETE FROM login_lockouts WHERE email = ? AND action = ?", ps -> {
            ps.setString(1, email);
            ps.setString(2, action.name());
        });
    }
}
