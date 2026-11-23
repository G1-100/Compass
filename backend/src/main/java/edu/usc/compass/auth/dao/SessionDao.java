package edu.usc.compass.auth.dao;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

/** SQL for the sessions table. Owner: Gavin. */
public class SessionDao {

    /** Minimal session row. */
    public record SessionRow(String sessionIdHash, long userId, Instant lastActivityAt, boolean invalidated) {
    }

    public void insert(Connection c, String sessionIdHash, long userId, Instant now) {
        Jdbc.update(c, """
                INSERT INTO sessions (session_id_hash, user_id, created_at, last_activity_at)
                VALUES (?, ?, ?, ?)""", ps -> {
            ps.setString(1, sessionIdHash);
            ps.setLong(2, userId);
            Jdbc.setInstant(ps, 3, now);
            Jdbc.setInstant(ps, 4, now);
        });
    }

    public Optional<SessionRow> findByHash(Connection c, String sessionIdHash) {
        return Jdbc.one(c,
                "SELECT session_id_hash, user_id, last_activity_at, invalidated FROM sessions WHERE session_id_hash = ?",
                ps -> ps.setString(1, sessionIdHash),
                rs -> new SessionRow(rs.getString("session_id_hash"), rs.getLong("user_id"),
                        Jdbc.getInstant(rs, "last_activity_at"), rs.getBoolean("invalidated")));
    }

    public void touch(Connection c, String sessionIdHash, Instant now) {
        Jdbc.update(c, "UPDATE sessions SET last_activity_at = ? WHERE session_id_hash = ?", ps -> {
            Jdbc.setInstant(ps, 1, now);
            ps.setString(2, sessionIdHash);
        });
    }

    public void invalidate(Connection c, String sessionIdHash) {
        Jdbc.update(c, "UPDATE sessions SET invalidated = TRUE WHERE session_id_hash = ?",
                ps -> ps.setString(1, sessionIdHash));
    }

    public void invalidateAllForUser(Connection c, long userId) {
        Jdbc.update(c, "UPDATE sessions SET invalidated = TRUE WHERE user_id = ? AND NOT invalidated",
                ps -> ps.setLong(1, userId));
    }
}
