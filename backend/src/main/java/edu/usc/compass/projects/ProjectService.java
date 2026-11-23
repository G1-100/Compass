package edu.usc.compass.projects;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.projects.ProjectDtos.CreateProjectRequest;
import edu.usc.compass.projects.ProjectDtos.UpdateProjectRequest;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.projects.model.ProjectModels.Project;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Project CRUD, archive and search. Owner: Solomon. Tests: Regression 2. */
public class ProjectService {

    private final Database db;
    private final ProjectDao projectDao;
    private final PermissionService permissions;

    public ProjectService(Database db, ProjectDao projectDao, PermissionService permissions) {
        this.db = db;
        this.projectDao = projectDao;
        this.permissions = permissions;
    }

    /**
     * SHARED CONTRACT — use this in any project-scoped service:
     * <pre>long teamId = projectService.requirePermission(c, ctx, projectId, Permission.MANAGE_TASKS);</pre>
     * Looks up the owning team (404 if missing), runs the permission check (403), returns the team id.
     */
    public long requirePermission(Connection c, AuthContext ctx, long projectId, Permission permission) throws SQLException {
        long teamId = projectDao.findTeamId(c, projectId);
        permissions.require(ctx, permission, teamId);
        return teamId;
    }

    /** GET /api/projects?q= */
    public List<Project> search(AuthContext ctx, String query) {
        String normalizedQuery = query == null
                ? ""
                : query.trim();
    
        return db.withConnection(c ->
                projectDao.searchForUser(
                        c,
                        ctx.userId(),
                        ctx.isSystemAdmin(),
                        normalizedQuery
                )
        );
    }

    /** GET /api/teams/{teamId}/projects?includeArchived= */
    public List<Project> listForTeam(AuthContext ctx, long teamId, boolean includeArchived) {
        permissions.require(
                ctx,
                Permission.VIEW_WORKSPACE,
                teamId
        );
    
        return db.withConnection(c ->
                projectDao.findByTeam(
                        c,
                        teamId,
                        includeArchived
                )
        );
    }

    /** POST /api/teams/{teamId}/projects. Team lead. Validate name, description, target date, status. */
    public Project create(AuthContext ctx, long teamId, CreateProjectRequest req) {
        throw Todo.of("Solomon", "ProjectService.create");
    }

    /** GET /api/projects/{projectId}. Team member. */
    public Project get(AuthContext ctx, long projectId) {
        return db.withConnection(c -> {
            requirePermission(
                    c,
                    ctx,
                    projectId,
                    Permission.VIEW_WORKSPACE
            );
    
            return projectDao.findById(c, projectId)
                    .orElseThrow(ApiException::notFound);
        });
    }

    /** PATCH /api/projects/{projectId}. Team lead; version-checked; archived projects are read-only (409). */
    public Project update(AuthContext ctx, long projectId, UpdateProjectRequest req) {
        throw Todo.of("Solomon", "ProjectService.update");
    }

    /** POST /api/projects/{projectId}/archive. Team lead; sets archived_at. */
    public Project archive(AuthContext ctx, long projectId, VersionRequest req) {
        throw Todo.of("Solomon", "ProjectService.archive");
    }
}
