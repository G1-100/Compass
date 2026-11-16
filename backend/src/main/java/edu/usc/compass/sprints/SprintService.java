package edu.usc.compass.sprints;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.sprints.SprintDtos.CompleteSprintRequest;
import edu.usc.compass.sprints.SprintDtos.CreateSprintRequest;
import edu.usc.compass.sprints.SprintDtos.UpdateSprintRequest;
import edu.usc.compass.sprints.dao.SprintDao;
import edu.usc.compass.sprints.model.SprintModels.Sprint;
import edu.usc.compass.sprints.model.SprintModels.SprintCompletionResult;
import edu.usc.compass.tasks.ActivityService;
import edu.usc.compass.tasks.dao.TaskDao;

import java.util.List;

/**
 * Sprint lifecycle PLANNED -> ACTIVE -> COMPLETED (Detailed Design 3.2.5).
 * Owner: Siya. Tests: Black box 9, White box 8 & 17, Regression 4, Stress 11 & 12.
 */
public class SprintService {

    private final Database db;
    private final SprintDao sprintDao;
    private final TaskDao taskDao;
    private final ProjectService projects;
    private final ActivityService activity;
    private final NotificationService notifications;

    public SprintService(Database db, SprintDao sprintDao, TaskDao taskDao, ProjectService projects,
                         ActivityService activity, NotificationService notifications) {
        this.db = db;
        this.sprintDao = sprintDao;
        this.taskDao = taskDao;
        this.projects = projects;
        this.activity = activity;
        this.notifications = notifications;
    }

    /** GET /api/projects/{projectId}/sprints. Team member. */
    public List<Sprint> list(AuthContext ctx, long projectId) {
        throw Todo.of("Siya", "SprintService.list");
    }

    /** POST /api/projects/{projectId}/sprints. Team lead. Name required; endDate after startDate. */
    public Sprint create(AuthContext ctx, long projectId, CreateSprintRequest req) {
        throw Todo.of("Siya", "SprintService.create");
    }

    /** PATCH /api/sprints/{sprintId}. Team lead; version-checked. */
    public Sprint update(AuthContext ctx, long sprintId, UpdateSprintRequest req) {
        throw Todo.of("Siya", "SprintService.update");
    }

    /** POST /api/sprints/{sprintId}/start. Team lead. Only PLANNED -> ACTIVE (409 otherwise). */
    public Sprint start(AuthContext ctx, long sprintId, VersionRequest req) {
        throw Todo.of("Siya", "SprintService.start");
    }

    /**
     * POST /api/sprints/{sprintId}/complete — the CompleteSprint pseudocode, in ONE transaction:
     * lock sprint, verify lead, version check (409), not already completed (409), split Done vs unfinished,
     * validate target (same project, PLANNED) or use backlog, move unfinished tasks
     * (TaskDao.moveToSprint) with an activity entry each, mark sprint COMPLETED with version + 1,
     * record SPRINT_COMPLETED. Any failure rolls everything back. Notify assignees of moved tasks.
     */
    public SprintCompletionResult completeSprint(AuthContext ctx, long sprintId, CompleteSprintRequest req) {
        throw Todo.of("Siya", "SprintService.completeSprint");
    }

    /** DELETE /api/sprints/{sprintId}. Team lead. Only PLANNED; its tasks return to the backlog. */
    public void delete(AuthContext ctx, long sprintId) {
        throw Todo.of("Siya", "SprintService.delete");
    }
}
