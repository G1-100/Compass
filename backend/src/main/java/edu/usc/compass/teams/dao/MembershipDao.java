package edu.usc.compass.teams.dao;

import edu.usc.compass.common.ApiException;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.teams.model.TeamRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL for team_memberships. Owner: Solomon. SHARED: auth, tasks, check-ins and workload read from it. */
public class MembershipDao {

    /** team_id -> role for every team the user is in. Used by AuthService.authenticate on every request. */
    public Map<Long, TeamRole> findRolesForUser(Connection c, long userId) throws SQLException {
        String sql = """
                SELECT team_id,
                       role
                FROM team_memberships
                WHERE user_id = ?
                ORDER BY team_id
                """;
    
        Map<Long, TeamRole> roles = new LinkedHashMap<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long teamId = rs.getLong("team_id");
                    TeamRole role =
                            TeamRole.valueOf(rs.getString("role"));
    
                    roles.put(teamId, role);
                }
            }
        }
    
        return roles;
    }

    public List<Membership> findByTeam(Connection c, long teamId) throws SQLException {
        String sql = """
                SELECT tm.membership_id,
                       tm.team_id,
                       tm.user_id,
                       u.display_name,
                       tm.role,
                       tm.joined_at,
                       tm.version
                FROM team_memberships tm
                JOIN users u
                  ON u.user_id = tm.user_id
                WHERE tm.team_id = ?
                ORDER BY lower(u.display_name), tm.user_id
                """;
    
        List<Membership> memberships = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    memberships.add(mapMembership(rs));
                }
            }
        }
    
        return memberships;
    }

    public Optional<Membership> find(Connection c, long teamId, long userId) throws SQLException {
        String sql = """
                SELECT tm.membership_id,
                       tm.team_id,
                       tm.user_id,
                       u.display_name,
                       tm.role,
                       tm.joined_at,
                       tm.version
                FROM team_memberships tm
                JOIN users u
                  ON u.user_id = tm.user_id
                WHERE tm.team_id = ?
                  AND tm.user_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setLong(2, userId);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
    
                return Optional.of(mapMembership(rs));
            }
        }
    }

    /** SELECT ... WHERE team_id = ? AND role = 'LEAD' FOR UPDATE (Last-Lead Protection). */
    public List<Membership> findLeadsForUpdate(Connection c, long teamId) throws SQLException {
        String sql = """
                SELECT tm.membership_id,
                       tm.team_id,
                       tm.user_id,
                       u.display_name,
                       tm.role,
                       tm.joined_at,
                       tm.version
                FROM team_memberships tm
                JOIN users u
                  ON u.user_id = tm.user_id
                WHERE tm.team_id = ?
                  AND tm.role = 'LEAD'
                ORDER BY tm.membership_id
                FOR UPDATE OF tm
                """;
    
        List<Membership> leads = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    leads.add(mapMembership(rs));
                }
            }
        }
    
        return leads;
    }

    public Optional<Membership> findForUpdate(Connection c, long teamId, long userId) throws SQLException {
        String sql = """
                SELECT tm.membership_id,
                       tm.team_id,
                       tm.user_id,
                       u.display_name,
                       tm.role,
                       tm.joined_at,
                       tm.version
                FROM team_memberships tm
                JOIN users u
                  ON u.user_id = tm.user_id
                WHERE tm.team_id = ?
                  AND tm.user_id = ?
                FOR UPDATE OF tm
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setLong(2, userId);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
    
                return Optional.of(mapMembership(rs));
            }
        }
    }

    public long insert(Connection c, long teamId, long userId, TeamRole role) throws SQLException {
        String sql = """
                INSERT INTO team_memberships (
                    team_id,
                    user_id,
                    role
                )
                VALUES (?, ?, ?)
                RETURNING membership_id
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setLong(2, userId);
            ps.setString(3, role.name());
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Creating the membership did not return a membership ID."
                    );
                }
    
                return rs.getLong("membership_id");
            }
        }
    }

    /** Sets role and version = newVersion. */
    public void updateRole(Connection c, long membershipId, TeamRole role, int newVersion) throws SQLException {
        String sql = """
                UPDATE team_memberships
                SET role = ?,
                    version = ?
                WHERE membership_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setInt(2, newVersion);
            ps.setLong(3, membershipId);
    
            int affectedRows = ps.executeUpdate();
    
            if (affectedRows == 0) {
                throw ApiException.notFound();
            }
        }
    }

    public void delete(Connection c, long membershipId) throws SQLException {
        String sql = """
                DELETE FROM team_memberships
                WHERE membership_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, membershipId);
    
            int affectedRows = ps.executeUpdate();
    
            if (affectedRows == 0) {
                throw ApiException.notFound();
            }
        }
    }

    private Membership mapMembership(ResultSet rs) throws SQLException {
        OffsetDateTime joinedAt =
                rs.getObject("joined_at", OffsetDateTime.class);
    
        return new Membership(
                rs.getLong("membership_id"),
                rs.getLong("team_id"),
                rs.getLong("user_id"),
                rs.getString("display_name"),
                TeamRole.valueOf(rs.getString("role")),
                joinedAt.toInstant(),
                rs.getInt("version")
        );
    }
}
