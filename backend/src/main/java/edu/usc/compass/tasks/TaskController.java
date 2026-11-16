package edu.usc.compass.tasks;

import edu.usc.compass.common.web.RequestContext;
import edu.usc.compass.common.web.Router;
import edu.usc.compass.tasks.TaskDtos.AddCommentRequest;
import edu.usc.compass.tasks.TaskDtos.ApproveSuggestionRequest;
import edu.usc.compass.tasks.TaskDtos.CreateTaskRequest;
import edu.usc.compass.tasks.TaskDtos.ReplaceAssigneesRequest;
import edu.usc.compass.tasks.TaskDtos.SuggestTaskRequest;
import edu.usc.compass.tasks.TaskDtos.TaskFilter;
import edu.usc.compass.tasks.TaskDtos.TaskSort;
import edu.usc.compass.tasks.TaskDtos.UpdateStatusRequest;
import edu.usc.compass.tasks.TaskDtos.UpdateTaskRequest;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Tasks (Detailed Design 3.5.5). Owner: Adam. */
public final class TaskController {

    private final TaskService tasks;
    private final SuggestionService suggestions;
    private final CommentService comments;
    private final ActivityService activity;

    public TaskController(TaskService tasks, SuggestionService suggestions, CommentService comments, ActivityService activity) {
        this.tasks = tasks;
        this.suggestions = suggestions;
        this.comments = comments;
        this.activity = activity;
    }

    public void register(Router r) {
        r.get("/api/projects/{projectId}/tasks", SIGNED_IN,
                ctx -> tasks.list(ctx.auth(), ctx.pathLong("projectId"), filter(ctx), ctx.page()));
        r.get("/api/users/me/tasks", SIGNED_IN, ctx -> tasks.listMine(ctx.auth(), filter(ctx), ctx.page()));
        r.post("/api/projects/{projectId}/tasks", SIGNED_IN, ctx -> {
            var t = tasks.create(ctx.auth(), ctx.pathLong("projectId"), ctx.body(CreateTaskRequest.class));
            return ctx.created("/api/tasks/" + t.taskId(), t);
        });
        r.post("/api/projects/{projectId}/tasks/suggestions", SIGNED_IN, ctx -> {
            var t = suggestions.suggest(ctx.auth(), ctx.pathLong("projectId"), ctx.body(SuggestTaskRequest.class));
            return ctx.created("/api/tasks/" + t.taskId(), t);
        });
        r.post("/api/tasks/{taskId}/approve", SIGNED_IN,
                ctx -> suggestions.approve(ctx.auth(), ctx.pathLong("taskId"), ctx.body(ApproveSuggestionRequest.class)));
        r.get("/api/tasks/{taskId}", SIGNED_IN, ctx -> tasks.get(ctx.auth(), ctx.pathLong("taskId")));
        r.patch("/api/tasks/{taskId}", SIGNED_IN,
                ctx -> tasks.update(ctx.auth(), ctx.pathLong("taskId"), ctx.body(UpdateTaskRequest.class)));
        r.patch("/api/tasks/{taskId}/status", SIGNED_IN,
                ctx -> tasks.updateStatus(ctx.auth(), ctx.pathLong("taskId"), ctx.body(UpdateStatusRequest.class)));
        r.put("/api/tasks/{taskId}/assignees", SIGNED_IN,
                ctx -> tasks.replaceAssignees(ctx.auth(), ctx.pathLong("taskId"), ctx.body(ReplaceAssigneesRequest.class)));
        r.delete("/api/tasks/{taskId}", SIGNED_IN, ctx -> {
            tasks.delete(ctx.auth(), ctx.pathLong("taskId"));
            return null;
        });
        r.get("/api/tasks/{taskId}/comments", SIGNED_IN, ctx -> comments.list(ctx.auth(), ctx.pathLong("taskId"), ctx.page()));
        r.post("/api/tasks/{taskId}/comments", SIGNED_IN, ctx -> {
            var c = comments.add(ctx.auth(), ctx.pathLong("taskId"), ctx.body(AddCommentRequest.class));
            return ctx.created("/api/tasks/" + c.taskId() + "/comments", c);
        });
        r.get("/api/tasks/{taskId}/activity", SIGNED_IN, ctx -> activity.list(ctx.auth(), ctx.pathLong("taskId"), ctx.page()));
    }

    /** Reads the task-board query parameters. */
    private static TaskFilter filter(RequestContext ctx) {
        return new TaskFilter(
                ctx.queryEnum("status", TaskStatus.class).orElse(null),
                ctx.queryLong("assigneeId").orElse(null),
                ctx.queryLong("sprintId").orElse(null),
                ctx.queryBool("backlog"),
                ctx.queryBool("overdue"),
                ctx.query("q").orElse(null),
                ctx.queryEnum("sort", TaskSort.class).orElse(null),
                ctx.query("order").map("desc"::equalsIgnoreCase).orElse(false));
    }
}
