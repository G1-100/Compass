package edu.usc.compass.auth.dao;

import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** SQL for the users table. Owner: Gavin. Other features may call the find* methods. */
public class UserDao {

    public Optional<User> findById(Connection c, long userId) {
        throw Todo.of("Gavin", "UserDao.findById");
    }

    /** Case-insensitive email lookup. */
    public Optional<User> findByEmail(Connection c, String email) {
        throw Todo.of("Gavin", "UserDao.findByEmail");
    }

    public Optional<User> findByGoogleSub(Connection c, String googleSub) {
        throw Todo.of("Gavin", "UserDao.findByGoogleSub");
    }

    /** Batch lookup for display names (used by tasks, check-ins, guest view). */
    public List<User> findByIds(Connection c, List<Long> userIds) {
        throw Todo.of("Gavin", "UserDao.findByIds");
    }

    /** Returns the password hash, or empty for Google-only accounts. Never expose it outside auth. */
    public Optional<String> findPasswordHash(Connection c, long userId) {
        throw Todo.of("Gavin", "UserDao.findPasswordHash");
    }

    /** INSERT; returns the new user_id. passwordHash or googleSub may be null. */
    public long insert(Connection c, String email, String passwordHash, String googleSub, String displayName, boolean activated) {
        throw Todo.of("Gavin", "UserDao.insert");
    }

    public void setActivated(Connection c, long userId) {
        throw Todo.of("Gavin", "UserDao.setActivated");
    }

    public void updatePasswordHash(Connection c, long userId, String passwordHash) {
        throw Todo.of("Gavin", "UserDao.updatePasswordHash");
    }

    public void linkGoogleSub(Connection c, long userId, String googleSub) {
        throw Todo.of("Gavin", "UserDao.linkGoogleSub");
    }

    /** UPDATE ... WHERE user_id = ? AND version = ?; returns false if the version did not match. */
    public boolean updateDisplayName(Connection c, long userId, String displayName, int expectedVersion) {
        throw Todo.of("Gavin", "UserDao.updateDisplayName");
    }

    public boolean updateSystemRole(Connection c, long userId, SystemRole role, int expectedVersion) {
        throw Todo.of("Gavin", "UserDao.updateSystemRole");
    }

    public List<User> list(Connection c, PageRequest page) {
        throw Todo.of("Gavin", "UserDao.list");
    }

    public long count(Connection c) {
        throw Todo.of("Gavin", "UserDao.count");
    }
}
