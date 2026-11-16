package edu.usc.compass.tasks;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.TaskDtos.CreateTaskRequest;
import edu.usc.compass.tasks.TaskDtos.ReplaceAssigneesRequest;
import edu.usc.compass.tasks.TaskDtos.TaskFilter;
import edu.usc.compass.tasks.TaskDtos.UpdateStatusRequest;
import edu.usc.compass.tasks.TaskDtos.UpdateTaskRequest;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.Task;
import edu.usc.compass.workload.LateRiskDetector;
import edu.usc.compass.workload.WorkloadService;

import java.time.Clock;

/**
 * Task CRUD, status changes and assignment (Detailed Design 3.2).
 * Every change: version check (409), activity entry, notify assignees, and (for effort/assignee changes)
 * workloadService.onAssignmentsChanged.
 * Owner: Adam. Tests: Black box 4 & 8, White box 7, Regression 8 & 12, Stress 1 & 18.
 */
public class TaskService {

    private final Database db;
    private final TaskDao taskDao;
    private final ProjectService projects;
    private final PermissionService permissions;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final WorkloadService workload;
    private final LateRiskDetector lateRisk;
    private final Clock clock;

    public TaskService(Database db, TaskDao taskDao, ProjectService projects, PermissionService permissions,
                       ActivityService activity, NotificationService notifications, WorkloadService workload,
                       LateRiskDetector lateRisk, Clock clock) {
        this.db = db;
        this.taskDao = taskDao;
        this.projects = projects;
        this.permissions = permissions;
        this.activity = activity;
        this.notifications = notifications;
        this.workload = workload;
        this.lateRisk = lateRisk;
        this.clock = clock;
    }

    /** GET /api/projects/{projectId}/tasks. Team member. Each task includes overdue + lateRisk. */
    public Page<Task> list(AuthContext ctx, long projectId, TaskFilter filter, PageRequest page) {
        throw Todo.of("Adam", "TaskService.list");
    }

    /** GET /api/users/me/tasks. The user's tasks across all teams. */
    public Page<Task> listMine(AuthContext ctx, TaskFilter filter, PageRequest page) {
        throw Todo.of("Adam", "TaskService.listMine");
    }

    /**
     * POST /api/projects/{projectId}/tasks. Team lead. Validate title (required, max 200), description
     * (max 5000), effort (required), sprint belongs to the project. Invalid input creates nothing.
     */
    public Task create(AuthContext ctx, long projectId, CreateTaskRequest req) {
        throw Todo.of("Adam", "TaskService.create");
    }

    /** GET /api/tasks/{taskId}. Team member. */
    public Task get(AuthContext ctx, long taskId) {
        throw Todo.of("Adam", "TaskService.get");
    }

    /** PATCH /api/tasks/{taskId}. Team lead; version-checked; assignees notified. */
    public Task update(AuthContext ctx, long taskId, UpdateTaskRequest req) {
        throw Todo.of("Adam", "TaskService.update");
    }

    /** PATCH /api/tasks/{taskId}/status. Assignee (or lead/admin) via permissions.requireAssigneeOrLead. */
    public Task updateStatus(AuthContext ctx, long taskId, UpdateStatusRequest req) {
        throw Todo.of("Adam", "TaskService.updateStatus");
    }

    /**
     * PUT /api/tasks/{taskId}/assignees. Team lead. Assignees must be team members; allocation percents,
     * if any are given, must all be given and total 100. Notify added and removed members.
     */
    public Task replaceAssignees(AuthContext ctx, long taskId, ReplaceAssigneesRequest req) {
        throw Todo.of("Adam", "TaskService.replaceAssignees");
    }

    /** DELETE /api/tasks/{taskId}. Team lead. For a PENDING suggestion this is a reject (SuggestionService.reject). */
    public void delete(AuthContext ctx, long taskId) {
        throw Todo.of("Adam", "TaskService.delete");
    }
}
