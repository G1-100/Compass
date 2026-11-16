package edu.usc.compass.tasks;

import edu.usc.compass.tasks.model.EffortLevel;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request bodies and query filters for task routes (Detailed Design 3.5.5). Owner: Adam. */
public final class TaskDtos {

    private TaskDtos() {
    }

    /** sprintId null = backlog. Status always starts NOT_STARTED. */
    public record CreateTaskRequest(String title, String description, LocalDate dueDate, EffortLevel effort, Long sprintId) {
    }

    /**
     * PATCH: fields left out stay unchanged. To move a task to the backlog send "moveToBacklog": true
     * (JSON null cannot be told apart from "not sent" with plain records).
     */
    public record UpdateTaskRequest(String title, String description, LocalDate dueDate, EffortLevel effort,
                                    Long sprintId, Boolean moveToBacklog, int version) {
    }

    public record UpdateStatusRequest(TaskStatus status, int version) {
    }

    /** allocationPercent values are optional; when given for any assignee they must total 100. */
    public record AssigneeInput(long userId, BigDecimal allocationPercent) {
    }

    public record ReplaceAssigneesRequest(List<AssigneeInput> assignees, int version) {
    }

    public record SuggestTaskRequest(String title, String description, LocalDate dueDate, EffortLevel effort) {
    }

    /** Optional edits applied at approval time (any field may be null). */
    public record ApproveSuggestionRequest(String title, String description, LocalDate dueDate, EffortLevel effort,
                                           Long sprintId, int version) {
    }

    public record AddCommentRequest(String commentText) {
    }

    public enum TaskSort {
        dueDate, effort, sprint
    }

    /**
     * Parsed from the query string of GET /api/projects/{id}/tasks and GET /api/users/me/tasks.
     * ?status= ?assigneeId= ?sprintId= ?backlog=true ?overdue=true ?q= ?sort=dueDate|effort|sprint ?order=asc|desc
     */
    public record TaskFilter(TaskStatus status, Long assigneeId, Long sprintId, boolean backlog, boolean overdue,
                             String q, TaskSort sort, boolean descending) {
    }
}
