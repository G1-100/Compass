package edu.usc.compass.teams.dao;

import edu.usc.compass.common.ApiException;
import edu.usc.compass.teams.model.TeamModels.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL for teams. Owner: Solomon. */
public class TeamDao {

    public long insert(Connection c, String name, long createdBy) {
        String sql = """
                INSERT INTO teams (
                    name,
                    created_by
                )
                VALUES (?, ?)
                RETURNING team_id
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setLong(2, createdBy);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Creating the team did not return a team ID."
                    );
                }
    
                return rs.getLong("team_id");
            }
        }
    }

    public Optional<Team> findById(Connection c, long teamId) throws SQLException {
        String sql = """
                SELECT team_id,
                       name,
                       created_by,
                       created_at,
                       version
                FROM teams
                WHERE team_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
    
                return Optional.of(mapTeam(rs));
            }
        }
    }

    public List<Team> findAll(Connection c) throws SQLException {
        String sql = """
                SELECT team_id,
                       name,
                       created_by,
                       created_at,
                       version
                FROM teams
                ORDER BY lower(name), team_id
                """;
    
        List<Team> teams = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
    
            while (rs.next()) {
                teams.add(mapTeam(rs));
            }
        }
    
        return teams;
    }

    /** Returns false on version mismatch. */
    public boolean rename(Connection c, long teamId, String name, int expectedVersion) throws SQLException {
        String sql = """
                UPDATE teams
                SET name = ?,
                    version = version + 1
                WHERE team_id = ?
                  AND version = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setLong(2, teamId);
            ps.setInt(3, expectedVersion);
    
            return ps.executeUpdate() == 1;
        }
    }

    /** ON DELETE CASCADE in schema.sql removes memberships, invitations, projects and check-ins. */
    public void delete(Connection c, long teamId) throws SQLException {
        String sql = """
                DELETE FROM teams
                WHERE team_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            int affectedRows = ps.executeUpdate();
    
            if (affectedRows == 0) {
                throw ApiException.notFound();
            }
        }
    }

    private Team mapTeam(ResultSet rs) throws SQLException {
        OffsetDateTime createdAt =
                rs.getObject("created_at", OffsetDateTime.class);
    
        return new Team(
                rs.getLong("team_id"),
                rs.getString("name"),
                rs.getLong("created_by"),
                createdAt.toInstant(),
                rs.getInt("version")
        );
    }
}
