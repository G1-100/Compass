package edu.usc.compass.projects;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.Validator;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.projects.ProjectDtos.CreateProjectRequest;
import edu.usc.compass.projects.ProjectDtos.UpdateProjectRequest;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.projects.model.ProjectModels.Project;
import edu.usc.compass.projects.model.ProjectModels.ProjectStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
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
        permissions.require(
                ctx,
                Permission.MANAGE_PROJECTS,
                teamId
        );
    
        if (req == null) {
            throw ApiException.badRequest(
                    "A project request body is required."
            );
        }
    
        String name = normalizeRequiredText(req.name());
        String description = normalizeOptionalText(req.description());
    
        new Validator()
                .required("name", name)
                .maxLength("name", name, 100)
                .maxLength("description", description, 2000)
                .required("status", req.status())
                .check(
                        req.targetDate() == null
                                || !req.targetDate().isBefore(LocalDate.now()),
                        "targetDate",
                        "Target date cannot be in the past."
                )
                .throwIfInvalid();
    
        return db.inTransaction(c -> {
            long projectId = projectDao.insert(
                    c,
                    teamId,
                    name,
                    description,
                    req.targetDate(),
                    req.status(),
                    ctx.userId()
            );
    
            return projectDao.findById(c, projectId)
                    .orElseThrow(() -> new IllegalStateException(
                            "The newly created project could not be retrieved."
                    ));
        });
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
        if (req == null) {
            throw ApiException.badRequest(
                    "A project request body is required."
            );
        }
    
        return db.inTransaction(c -> {
            requirePermission(
                    c,
                    ctx,
                    projectId,
                    Permission.MANAGE_PROJECTS
            );
    
            Project existing = projectDao.findById(c, projectId)
                    .orElseThrow(ApiException::notFound);
    
            if (existing.archivedAt() != null) {
                throw ApiException.invalidState(
                        "Archived projects cannot be edited."
                );
            }
    
            String name = req.name() == null
                    ? existing.name()
                    : normalizeRequiredText(req.name());
    
            String description = req.description() == null
                    ? existing.description()
                    : normalizeOptionalText(req.description());
    
            LocalDate targetDate = req.targetDate() == null
                    ? existing.targetDate()
                    : req.targetDate();
    
            ProjectStatus status = req.status() == null
                    ? existing.status()
                    : req.status();
    
            new Validator()
                    .required("name", name)
                    .maxLength("name", name, 100)
                    .maxLength("description", description, 2000)
                    .required("status", status)
                    .check(
                            req.targetDate() == null
                                    || !req.targetDate().isBefore(LocalDate.now()),
                            "targetDate",
                            "Target date cannot be in the past."
                    )
                    .check(
                            req.version() >= 1,
                            "version",
                            "Version must be at least 1."
                    )
                    .throwIfInvalid();
    
            Project updated = new Project(
                    existing.projectId(),
                    existing.teamId(),
                    name,
                    description,
                    targetDate,
                    status,
                    existing.archivedAt(),
                    existing.createdBy(),
                    existing.createdAt(),
                    existing.version()
            );
    
            boolean saved = projectDao.update(
                    c,
                    updated,
                    req.version()
            );
    
            if (!saved) {
                Project current = projectDao.findById(c, projectId)
                        .orElseThrow(ApiException::notFound);
    
                throw ApiException.versionConflict(current);
            }
    
            return projectDao.findById(c, projectId)
                    .orElseThrow(() -> new IllegalStateException(
                            "The updated project could not be retrieved."
                    ));
        });
    }

    /** POST /api/projects/{projectId}/archive. Team lead; sets archived_at. */
    public Project archive(AuthContext ctx, long projectId, VersionRequest req) {
        if (req == null) {
            throw ApiException.badRequest(
                    "A version request body is required."
            );
        }
    
        new Validator()
                .check(
                        req.version() >= 1,
                        "version",
                        "Version must be at least 1."
                )
                .throwIfInvalid();
    
        return db.inTransaction(c -> {
            requirePermission(
                    c,
                    ctx,
                    projectId,
                    Permission.MANAGE_PROJECTS
            );
    
            Project existing = projectDao.findById(c, projectId)
                    .orElseThrow(ApiException::notFound);
    
            if (existing.archivedAt() != null) {
                throw ApiException.invalidState(
                        "This project is already archived."
                );
            }
    
            boolean archived = projectDao.archive(
                    c,
                    projectId,
                    req.version()
            );
    
            if (!archived) {
                Project current = projectDao.findById(c, projectId)
                        .orElseThrow(ApiException::notFound);
    
                throw ApiException.versionConflict(current);
            }
    
            return projectDao.findById(c, projectId)
                    .orElseThrow(() -> new IllegalStateException(
                            "The archived project could not be retrieved."
                    ));
        });
    }

    private static String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }
    
    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
    
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
