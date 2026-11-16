package edu.usc.compass.tasks;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.TaskDtos.ApproveSuggestionRequest;
import edu.usc.compass.tasks.TaskDtos.SuggestTaskRequest;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.Task;

/**
 * Member task suggestions that a lead approves or rejects. Suggestions are tasks rows with
 * suggestion_status = PENDING and never appear on the board until APPROVED.
 * Owner: Adam. Tests: Black box 5, Regression 9 & 10.
 */
public class SuggestionService {

    private final Database db;
    private final TaskDao taskDao;
    private final ProjectService projects;
    private final PermissionService permissions;
    private final ActivityService activity;
    private final NotificationService notifications;

    public SuggestionService(Database db, TaskDao taskDao, ProjectService projects, PermissionService permissions,
                             ActivityService activity, NotificationService notifications) {
        this.db = db;
        this.taskDao = taskDao;
        this.projects = projects;
        this.permissions = permissions;
        this.activity = activity;
        this.notifications = notifications;
    }

    /** POST /api/projects/{projectId}/tasks/suggestions. Team member. Stored as PENDING; leads notified. */
    public Task suggest(AuthContext ctx, long projectId, SuggestTaskRequest req) {
        throw Todo.of("Adam", "SuggestionService.suggest");
    }

    /**
     * POST /api/tasks/{taskId}/approve. Team lead. Lock the row; only PENDING can be approved (409 otherwise,
     * so a double click never creates two tasks). Apply optional edits, set APPROVED.
     */
    public Task approve(AuthContext ctx, long taskId, ApproveSuggestionRequest req) {
        throw Todo.of("Adam", "SuggestionService.approve");
    }

    /** Called by TaskService.delete for a PENDING suggestion: mark REJECTED and record one rejection activity. */
    public void reject(AuthContext ctx, long taskId) {
        throw Todo.of("Adam", "SuggestionService.reject");
    }
}
