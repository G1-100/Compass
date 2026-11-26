package edu.usc.compass.workload;

import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.Rules;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.Assignee;
import edu.usc.compass.tasks.model.TaskModels.SuggestionStatus;
import edu.usc.compass.tasks.model.TaskModels.Task;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;
import edu.usc.compass.workload.dao.WorkloadDao;
import edu.usc.compass.workload.model.WorkloadModels.Availability;
import edu.usc.compass.workload.model.WorkloadModels.LateRiskStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** DetectLateRisk (Detailed Design 3.2.4). Owner: Liv. Tests: Stress 14. */
public class LateRiskDetector {

    /**
     * Assumed effort-to-hours conversion used by the capacity estimate: one effort point is about two hours of
     * work (Minor 2h, Moderate 6h, High 10h, Extreme 16h). The design's EstimateRemainingCapacity is not
     * spelled out, so change this one number if the Detailed Design says otherwise.
     */
    static final double HOURS_PER_POINT = 2.0;

    /** Guards the strict "points > capacity" comparison against floating-point noise. */
    private static final double EPSILON = 1e-9;

    private final CheckinDao checkinDao;
    private final WorkloadDao workloadDao;
    private final TaskDao taskDao;

    public LateRiskDetector(CheckinDao checkinDao, WorkloadDao workloadDao, TaskDao taskDao) {
        this.checkinDao = checkinDao;
        this.workloadDao = workloadDao;
        this.taskDao = taskDao;
    }

    /**
     * SHARED CONTRACT (Adam shows the result on every task). Rules, in order:
     * Done -> NO_RISK; past due -> OVERDUE; due more than LATE_RISK_WINDOW_DAYS away -> NO_IMMEDIATE_RISK;
     * unresolved check-in blocker linked to the task -> AT_RISK_UNRESOLVED_BLOCKER;
     * effort points > assignees' remaining capacity before the due date -> AT_RISK_INSUFFICIENT_CAPACITY;
     * else DUE_SOON_ON_TRACK.
     * A task with no due date can never be late, so it is NO_IMMEDIATE_RISK.
     */
    public LateRiskStatus detectLateRisk(Connection c, Task task, LocalDate today) {
        try {
            return evaluate(c, task, today);
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    private LateRiskStatus evaluate(Connection c, Task task, LocalDate today) throws SQLException {
        if (task.status() == TaskStatus.DONE) {
            return LateRiskStatus.NO_RISK;
        }
        if (task.dueDate() == null) {
            return LateRiskStatus.NO_IMMEDIATE_RISK;
        }

        long daysUntilDue = ChronoUnit.DAYS.between(today, task.dueDate());
        if (daysUntilDue < 0) {
            return LateRiskStatus.OVERDUE;
        }
        if (daysUntilDue > Rules.LATE_RISK_WINDOW_DAYS) {
            return LateRiskStatus.NO_IMMEDIATE_RISK;
        }

        if (!checkinDao.findUnresolvedBlockersForTask(c, task.taskId()).isEmpty()) {
            return LateRiskStatus.AT_RISK_UNRESOLVED_BLOCKER;
        }

        double totalCapacity = 0;
        for (Assignee assignee : task.assignees()) {
            totalCapacity += estimateRemainingCapacity(c, task, assignee.userId(), daysUntilDue);
        }

        if (task.effort().points() > totalCapacity + EPSILON) {
            return LateRiskStatus.AT_RISK_INSUFFICIENT_CAPACITY;
        }
        return LateRiskStatus.DUE_SOON_ON_TRACK;
    }

    /**
     * EstimateRemainingCapacity, in effort points: the member's reported weekly hours scaled to the days left
     * (counting the due date itself), converted to points, minus their share of other incomplete tasks that
     * are due on or before this one. Never negative. A member with no reported availability has none.
     */
    private double estimateRemainingCapacity(Connection c, Task task, long userId, long daysUntilDue)
            throws SQLException {
        double hoursPerWeek = workloadDao.findAvailability(c, task.projectId(), userId)
                .map(Availability::hoursPerWeek)
                .map(h -> h.doubleValue())
                .orElse(0.0);

        double availableHours = hoursPerWeek * (daysUntilDue + 1) / 7.0;
        double capacityPoints = availableHours / HOURS_PER_POINT;

        double committedPoints = 0;
        for (Task other : taskDao.findIncompleteAssigned(c, task.projectId(), userId, task.dueDate())) {
            if (other.taskId() == task.taskId() || isUnapprovedSuggestion(other)) {
                continue;
            }
            committedPoints += pointsFor(other, userId);
        }

        return Math.max(0.0, capacityPoints - committedPoints);
    }

    private static boolean isUnapprovedSuggestion(Task task) {
        SuggestionStatus status = task.suggestionStatus();
        return status == SuggestionStatus.PENDING || status == SuggestionStatus.REJECTED;
    }

    /** The user's share of a task's effort points: by allocationPercent when set, otherwise an equal split. */
    private static double pointsFor(Task task, long userId) {
        for (Assignee assignee : task.assignees()) {
            if (assignee.userId() == userId) {
                double share = assignee.allocationPercent() != null
                        ? assignee.allocationPercent().doubleValue() / 100.0
                        : 1.0 / Math.max(1, task.assignees().size());
                return task.effort().points() * share;
            }
        }
        return 0.0;
    }
}
