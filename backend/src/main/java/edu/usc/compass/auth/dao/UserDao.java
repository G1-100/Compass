package edu.usc.compass.auth.dao;

import edu.usc.compass.auth.AuthDtos.TeamMembershipSummary;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.teams.model.TeamRole;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** SQL for the users table. Owner: Gavin. Other features may call the find* methods. */
public class UserDao {

    private static final String COLUMNS =
            "user_id, email, display_name, system_role, activated, created_at, version";

    public Optional<User> findById(Connection c, long userId) {
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM users WHERE user_id = ?",
                ps -> ps.setLong(1, userId), UserDao::map);
    }

    /** Case-insensitive email lookup. */
    public Optional<User> findByEmail(Connection c, String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM users WHERE lower(email) = lower(?)",
                ps -> ps.setString(1, email.trim()), UserDao::map);
    }

    public Optional<User> findByGoogleSub(Connection c, String googleSub) {
        return Jdbc.one(c, "SELECT " + COLUMNS + " FROM users WHERE google_sub = ?",
                ps -> ps.setString(1, googleSub), UserDao::map);
    }

    /** Batch lookup for display names (used by tasks, check-ins, guest view). */
    public List<User> findByIds(Connection c, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return Jdbc.list(c, "SELECT " + COLUMNS + " FROM users WHERE user_id = ANY (?) ORDER BY user_id",
                ps -> {
                    Array arr = ps.getConnection().createArrayOf("bigint", userIds.toArray());
                    ps.setArray(1, arr);
                }, UserDao::map);
    }

    /** Returns the password hash, or empty for Google-only accounts. Never expose it outside auth. */
    public Optional<String> findPasswordHash(Connection c, long userId) {
        return Jdbc.one(c, "SELECT password_hash FROM users WHERE user_id = ?",
                ps -> ps.setLong(1, userId), rs -> rs.getString("password_hash"));
    }

    /**
     * INSERT; returns the new user_id. passwordHash or googleSub may be null.
     * A duplicate email (including a race between two registrations) becomes a 400 on the email field.
     */
    public long insert(Connection c, String email, String passwordHash, String googleSub, String displayName, boolean activated) {
        try {
            return Jdbc.one(c, """
                            INSERT INTO users (email, password_hash, google_sub, display_name, activated)
                            VALUES (?, ?, ?, ?, ?)
                            RETURNING user_id""",
                    ps -> {
                        ps.setString(1, email);
                        ps.setString(2, passwordHash);
                        ps.setString(3, googleSub);
                        ps.setString(4, displayName);
                        ps.setBoolean(5, activated);
                    }, rs -> rs.getLong(1)).orElseThrow();
        } catch (RuntimeException e) {
            if (Jdbc.isUniqueViolation(e)) {
                throw ApiException.validation("email", "An account with this email already exists.");
            }
            throw e;
        }
    }

    public void setActivated(Connection c, long userId) {
        Jdbc.update(c, "UPDATE users SET activated = TRUE, version = version + 1 WHERE user_id = ? AND NOT activated",
                ps -> ps.setLong(1, userId));
    }

    public void updatePasswordHash(Connection c, long userId, String passwordHash) {
        Jdbc.update(c, "UPDATE users SET password_hash = ?, version = version + 1 WHERE user_id = ?", ps -> {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
        });
    }

    public void linkGoogleSub(Connection c, long userId, String googleSub) {
        Jdbc.update(c, "UPDATE users SET google_sub = ?, version = version + 1 WHERE user_id = ?", ps -> {
            ps.setString(1, googleSub);
            ps.setLong(2, userId);
        });
    }

    /** UPDATE ... WHERE user_id = ? AND version = ?; returns false if the version did not match. */
    public boolean updateDisplayName(Connection c, long userId, String displayName, int expectedVersion) {
        return Jdbc.update(c,
                "UPDATE users SET display_name = ?, version = version + 1 WHERE user_id = ? AND version = ?", ps -> {
                    ps.setString(1, displayName);
                    ps.setLong(2, userId);
                    ps.setInt(3, expectedVersion);
                }) == 1;
    }

    public boolean updateSystemRole(Connection c, long userId, SystemRole role, int expectedVersion) {
        return Jdbc.update(c,
                "UPDATE users SET system_role = ?, version = version + 1 WHERE user_id = ? AND version = ?", ps -> {
                    ps.setString(1, role.name());
                    ps.setLong(2, userId);
                    ps.setInt(3, expectedVersion);
                }) == 1;
    }

    public List<User> list(Connection c, PageRequest page) {
        return Jdbc.list(c, "SELECT " + COLUMNS + " FROM users ORDER BY user_id LIMIT ? OFFSET ?", ps -> {
            ps.setInt(1, page.limit());
            ps.setInt(2, page.offset());
        }, UserDao::map);
    }

    public long count(Connection c) {
        return Jdbc.one(c, "SELECT count(*) FROM users", ps -> { }, rs -> rs.getLong(1)).orElse(0L);
    }

    /**
     * Teams the user belongs to, with names, for GET /api/auth/me. Read-only join on Solomon's tables;
     * kept here because only auth needs the name + role pair.
     */
    public List<TeamMembershipSummary> findTeamSummaries(Connection c, long userId) {
        return Jdbc.list(c, """
                        SELECT t.team_id, t.name, m.role
                        FROM team_memberships m
                        JOIN teams t ON t.team_id = m.team_id
                        WHERE m.user_id = ?
                        ORDER BY t.name, t.team_id""",
                ps -> ps.setLong(1, userId),
                rs -> new TeamMembershipSummary(rs.getLong("team_id"), rs.getString("name"),
                        TeamRole.valueOf(rs.getString("role"))));
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("user_id"),
                rs.getString("email"),
                rs.getString("display_name"),
                SystemRole.valueOf(rs.getString("system_role")),
                rs.getBoolean("activated"),
                Jdbc.getInstant(rs, "created_at"),
                rs.getInt("version"));
    }
}
