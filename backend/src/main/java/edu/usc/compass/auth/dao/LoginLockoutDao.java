package edu.usc.compass.auth.dao;

import edu.usc.compass.auth.model.AuthEnums.LockoutAction;
import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

/** SQL for login_lockouts. Owner: Gavin. */
public class LoginLockoutDao {

    public record LockoutRow(String email, LockoutAction action, int failedCount, Instant windowStart, Instant lockedUntil) {
    }

    /** SELECT ... FOR UPDATE so concurrent failures are counted one at a time. */
    public Optional<LockoutRow> findForUpdate(Connection c, String email, LockoutAction action) {
        throw Todo.of("Gavin", "LoginLockoutDao.findForUpdate");
    }

    /** INSERT ... ON CONFLICT (email, action) DO UPDATE. */
    public void upsert(Connection c, LockoutRow row) {
        throw Todo.of("Gavin", "LoginLockoutDao.upsert");
    }

    public void delete(Connection c, String email, LockoutAction action) {
        throw Todo.of("Gavin", "LoginLockoutDao.delete");
    }
}
