package edu.usc.compass.auth.dao;

import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

/** SQL for the sessions table. Owner: Gavin. */
public class SessionDao {

    /** Minimal session row. */
    public record SessionRow(String sessionIdHash, long userId, Instant lastActivityAt, boolean invalidated) {
    }

    public void insert(Connection c, String sessionIdHash, long userId, Instant now) {
        throw Todo.of("Gavin", "SessionDao.insert");
    }

    public Optional<SessionRow> findByHash(Connection c, String sessionIdHash) {
        throw Todo.of("Gavin", "SessionDao.findByHash");
    }

    public void touch(Connection c, String sessionIdHash, Instant now) {
        throw Todo.of("Gavin", "SessionDao.touch");
    }

    public void invalidate(Connection c, String sessionIdHash) {
        throw Todo.of("Gavin", "SessionDao.invalidate");
    }

    public void invalidateAllForUser(Connection c, long userId) {
        throw Todo.of("Gavin", "SessionDao.invalidateAllForUser");
    }
}
