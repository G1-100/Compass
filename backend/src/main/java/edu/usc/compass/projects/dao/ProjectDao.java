package edu.usc.compass.projects.dao;

import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Todo;
import edu.usc.compass.projects.model.ProjectModels.Project;
import edu.usc.compass.projects.model.ProjectModels.ProjectStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Date;
import java.sql.Statement;
import java.sql.Types;
import java.sql.SQLException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL for projects. Owner: Solomon. */
public class ProjectDao {

    /**
     * SHARED CONTRACT — already implemented so every feature can run permission checks on day one.
     * Returns the team that owns a project, or 404 if the project does not exist.
     */
    public long findTeamId(Connection c, long projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT team_id FROM projects WHERE project_id = ?")) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound();
                }
                return rs.getLong(1);
            }
        }
    }

    public Optional<Project> findById(Connection c, long projectId) throws SQLException {
        String sql = """
                SELECT project_id,
                       team_id,
                       name,
                       description,
                       target_date,
                       status,
                       archived_at,
                       created_by,
                       created_at,
                       version
                FROM projects
                WHERE project_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
    
                return Optional.of(mapProject(rs));
            }
        }
    }

    public List<Project> findByTeam(Connection c, long teamId, boolean includeArchived) throws SQLException {
        String sql;
    
        if (includeArchived) {
            sql = """
                    SELECT project_id,
                           team_id,
                           name,
                           description,
                           target_date,
                           status,
                           archived_at,
                           created_by,
                           created_at,
                           version
                    FROM projects
                    WHERE team_id = ?
                    ORDER BY created_at DESC, project_id DESC
                    """;
        } else {
            sql = """
                    SELECT project_id,
                           team_id,
                           name,
                           description,
                           target_date,
                           status,
                           archived_at,
                           created_by,
                           created_at,
                           version
                    FROM projects
                    WHERE team_id = ?
                      AND archived_at IS NULL
                    ORDER BY created_at DESC, project_id DESC
                    """;
        }
    
        List<Project> projects = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    projects.add(mapProject(rs));
                }
            }
        }
    
        return projects;
    }

    /** Name search (ILIKE) across all teams the user belongs to (all projects for system admins). */
    public List<Project> searchForUser(Connection c, long userId, boolean isSystemAdmin, String query) throws SQLException {
        String sql = """
                SELECT p.project_id,
                       p.team_id,
                       p.name,
                       p.description,
                       p.target_date,
                       p.status,
                       p.archived_at,
                       p.created_by,
                       p.created_at,
                       p.version
                FROM projects p
                WHERE p.name ILIKE ?
                  AND (
                      ?
                      OR EXISTS (
                          SELECT 1
                          FROM team_memberships tm
                          WHERE tm.team_id = p.team_id
                            AND tm.user_id = ?
                      )
                  )
                ORDER BY lower(p.name), p.project_id
                """;
    
        List<Project> projects = new ArrayList<>();
        String searchPattern = "%" + query.trim() + "%";
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, searchPattern);
            ps.setBoolean(2, isSystemAdmin);
            ps.setLong(3, userId);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    projects.add(mapProject(rs));
                }
            }
        }
    
        return projects;
    }

    public long insert(Connection c, long teamId, String name, String description, LocalDate targetDate,
                       ProjectStatus status, long createdBy) throws SQLException {
        String sql = """
                INSERT INTO projects (
                    team_id,
                    name,
                    description,
                    target_date,
                    status,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING project_id
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setObject(4, targetDate);
            ps.setString(5, status.name());
            ps.setLong(6, createdBy);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Creating the project did not return a project ID.");
                }
    
                return rs.getLong("project_id");
            }
        }
    }

    /** Version-checked update; returns false on mismatch. */
    public boolean update(Connection c, Project updated, int expectedVersion) throws SQLException {
        String sql = """
                UPDATE projects
                SET name = ?,
                    description = ?,
                    target_date = ?,
                    status = ?,
                    version = version + 1
                WHERE project_id = ?
                  AND version = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, updated.name());
            ps.setString(2, updated.description());
    
            if (updated.targetDate() == null) {
                ps.setNull(3, java.sql.Types.DATE);
            } else {
                ps.setObject(3, updated.targetDate());
            }
    
            ps.setString(4, updated.status().name());
            ps.setLong(5, updated.projectId());
            ps.setInt(6, expectedVersion);
    
            return ps.executeUpdate() == 1;
        }
    }
    
    public boolean archive(Connection c, long projectId, int expectedVersion) throws SQLException {
        String sql = """
                UPDATE projects
                SET archived_at = now(),
                    version = version + 1
                WHERE project_id = ?
                  AND version = ?
                  AND archived_at IS NULL
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setInt(2, expectedVersion);
    
            return ps.executeUpdate() == 1;
        }
    }

    private Project mapProject(ResultSet rs) throws SQLException {
        OffsetDateTime archivedAtValue =
                rs.getObject("archived_at", OffsetDateTime.class);
    
        OffsetDateTime createdAtValue =
                rs.getObject("created_at", OffsetDateTime.class);
    
        return new Project(
                rs.getLong("project_id"),
                rs.getLong("team_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("target_date", LocalDate.class),
                ProjectStatus.valueOf(rs.getString("status")),
                archivedAtValue == null ? null : archivedAtValue.toInstant(),
                rs.getLong("created_by"),
                createdAtValue.toInstant(),
                rs.getInt("version")
        );
    }
}
