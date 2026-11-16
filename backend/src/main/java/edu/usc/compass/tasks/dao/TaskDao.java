package edu.usc.compass.tasks.dao;

import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.TaskDtos.AssigneeInput;
import edu.usc.compass.tasks.TaskDtos.TaskFilter;
import edu.usc.compass.tasks.model.EffortLevel;
import edu.usc.compass.tasks.model.TaskModels.AssignmentRow;
import edu.usc.compass.tasks.model.TaskModels.SuggestionStatus;
import edu.usc.compass.tasks.model.TaskModels.Task;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SQL for tasks and task_assignees. Owner: Adam.
 * Methods marked SHARED are called by other features (sprints, milestones, workload, dashboard, guest view);
 * keep their signatures stable.
 */
public class TaskDao {

    /** SHARED — already implemented. The project a task belongs to, or 404. */
    public long findProjectId(Connection c, long taskId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT project_id FROM tasks WHERE task_id = ?")) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound();
                }
                return rs.getLong(1);
            }
        }
    }

    /** One task with its assignees. overdue/lateRisk may be filled in by the service. */
    public Optional<Task> findById(Connection c, long taskId) {
        throw Todo.of("Adam", "TaskDao.findById");
    }

    /** Same as findById but SELECT ... FOR UPDATE. */
    public Optional<Task> findForUpdate(Connection c, long taskId) {
        throw Todo.of("Adam", "TaskDao.findForUpdate");
    }

    /** Task board query with filters, sorting and paging. Excludes PENDING/REJECTED suggestions. */
    public List<Task> findByProject(Connection c, long projectId, TaskFilter filter, PageRequest page) {
        throw Todo.of("Adam", "TaskDao.findByProject");
    }

    public long countByProject(Connection c, long projectId, TaskFilter filter) {
        throw Todo.of("Adam", "TaskDao.countByProject");
    }

    /** The user's assigned tasks across all their teams. */
    public List<Task> findAssignedToUser(Connection c, long userId, TaskFilter filter, PageRequest page) {
        throw Todo.of("Adam", "TaskDao.findAssignedToUser");
    }

    /** SHARED (milestones, guest view). Tasks by id, in any order. */
    public List<Task> findByIds(Connection c, List<Long> taskIds) {
        throw Todo.of("Adam", "TaskDao.findByIds");
    }

    /** SHARED (sprints). All tasks currently in a sprint. */
    public List<Task> findBySprint(Connection c, long sprintId) {
        throw Todo.of("Adam", "TaskDao.findBySprint");
    }

    /** SHARED (workload, dashboard). Every assignment of a not-Done, non-suggestion task in the project. */
    public List<AssignmentRow> findActiveAssignments(Connection c, long projectId) {
        throw Todo.of("Adam", "TaskDao.findActiveAssignments");
    }

    /** SHARED (drift). Incomplete tasks assigned to the user with dueDate on or before the given date. */
    public List<Task> findIncompleteAssigned(Connection c, long projectId, long userId, LocalDate dueOnOrBefore) {
        throw Todo.of("Adam", "TaskDao.findIncompleteAssigned");
    }

    /** SHARED (drift). Latest time the user changed a task status in this project (from task_activity). */
    public Optional<Instant> findLastStatusChangeBy(Connection c, long projectId, long userId) {
        throw Todo.of("Adam", "TaskDao.findLastStatusChangeBy");
    }

    /** INSERT a task; suggestionStatus/suggestedBy null for normal tasks. Returns task_id. */
    public long insert(Connection c, long projectId, Long sprintId, String title, String description, LocalDate dueDate,
                       EffortLevel effort, SuggestionStatus suggestionStatus, Long suggestedBy, long createdBy) {
        throw Todo.of("Adam", "TaskDao.insert");
    }

    /** Version-checked full update of editable fields; bumps version and updated_at. False on mismatch. */
    public boolean update(Connection c, Task updated, int expectedVersion) {
        throw Todo.of("Adam", "TaskDao.update");
    }

    public boolean updateStatus(Connection c, long taskId, TaskStatus status, int expectedVersion) {
        throw Todo.of("Adam", "TaskDao.updateStatus");
    }

    public void setSuggestionStatus(Connection c, long taskId, SuggestionStatus status) {
        throw Todo.of("Adam", "TaskDao.setSuggestionStatus");
    }

    /** Deletes all task_assignees rows for the task and inserts the new list. */
    public void replaceAssignees(Connection c, long taskId, List<AssigneeInput> assignees) {
        throw Todo.of("Adam", "TaskDao.replaceAssignees");
    }

    /** SHARED (sprints). sprintId null = backlog. Keeps assignees, comments, blockers and history. */
    public void moveToSprint(Connection c, long taskId, Long sprintId) {
        throw Todo.of("Adam", "TaskDao.moveToSprint");
    }

    public void delete(Connection c, long taskId) {
        throw Todo.of("Adam", "TaskDao.delete");
    }
}
