package edu.usc.compass.projects.dao;

import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Todo;
import edu.usc.compass.projects.model.ProjectModels.Project;
import edu.usc.compass.projects.model.ProjectModels.ProjectStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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

    public Optional<Project> findById(Connection c, long projectId) {
        throw Todo.of("Solomon", "ProjectDao.findById");
    }

    public List<Project> findByTeam(Connection c, long teamId, boolean includeArchived) {
        throw Todo.of("Solomon", "ProjectDao.findByTeam");
    }

    /** Name search (ILIKE) across all teams the user belongs to (all projects for system admins). */
    public List<Project> searchForUser(Connection c, long userId, boolean isSystemAdmin, String query) {
        throw Todo.of("Solomon", "ProjectDao.searchForUser");
    }

    public long insert(Connection c, long teamId, String name, String description, LocalDate targetDate,
                       ProjectStatus status, long createdBy) {
        throw Todo.of("Solomon", "ProjectDao.insert");
    }

    /** Version-checked update; returns false on mismatch. */
    public boolean update(Connection c, Project updated, int expectedVersion) {
        throw Todo.of("Solomon", "ProjectDao.update");
    }

    public boolean archive(Connection c, long projectId, int expectedVersion) {
        throw Todo.of("Solomon", "ProjectDao.archive");
    }
}
