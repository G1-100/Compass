package edu.usc.compass.tasks.model;

import edu.usc.compass.workload.model.WorkloadModels.LateRiskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Task feature records and enums (tables tasks, task_assignees, task_comments, task_activity). Owner: Adam. */
public final class TaskModels {

    private TaskModels() {
    }

    public enum TaskStatus {
        NOT_STARTED, IN_PROGRESS, BLOCKED, DONE
    }

    /** NULL in the DB = a normal task; otherwise the task is (or was) a member's suggestion. */
    public enum SuggestionStatus {
        PENDING, APPROVED, REJECTED
    }

    /** @param allocationPercent null = effort split equally among assignees */
    public record Assignee(long userId, String displayName, BigDecimal allocationPercent) {
    }

    /**
     * A task as returned by the API. sprintId null = backlog.
     *
     * @param overdue  incomplete and dueDate is before today (computed when loaded)
     * @param lateRisk from LateRiskDetector (Liv); UNKNOWN until that is implemented
     */
    public record Task(long taskId, long projectId, Long sprintId, String title, String description, LocalDate dueDate,
                       EffortLevel effort, TaskStatus status, SuggestionStatus suggestionStatus, Long suggestedBy,
                       long createdBy, Instant createdAt, Instant updatedAt, int version, List<Assignee> assignees,
                       boolean overdue, LateRiskStatus lateRisk) {
    }

    public record Comment(long commentId, long taskId, Long authorId, String authorName, String commentText, Instant createdAt) {
    }

    public enum ActivityType {
        TASK_CREATED, TASK_EDITED, STATUS_CHANGED, ASSIGNEES_CHANGED, COMMENT_ADDED,
        SUGGESTION_SUBMITTED, SUGGESTION_APPROVED, SUGGESTION_REJECTED,
        TASK_MOVED_TO_SPRINT, TASK_MOVED_TO_NEXT_SPRINT, TASK_RETURNED_TO_BACKLOG,
        SPRINT_STARTED, SPRINT_COMPLETED, TASK_DELETED
    }

    public record ActivityEntry(long activityId, Long taskId, Long sprintId, Long actorUserId, String actorName,
                                ActivityType activityType, String oldValue, String newValue, Instant createdAt) {
    }

    /**
     * SHARED CONTRACT — one active (not Done) task assignment, for workload/drift/late-risk (Liv) and the
     * dashboard (Solomon). Returned by TaskDao.findActiveAssignments.
     */
    public record AssignmentRow(long taskId, long userId, EffortLevel effort, TaskStatus status, LocalDate dueDate,
                                BigDecimal allocationPercent, int assigneeCount) {
    }
}
