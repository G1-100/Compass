package edu.usc.compass.workload;

import edu.usc.compass.common.Rules;
import edu.usc.compass.common.Severity;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.jobs.Job;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.AssignmentRow;
import edu.usc.compass.tasks.model.TaskModels.Task;
import edu.usc.compass.workload.dao.WorkloadDao;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;
import edu.usc.compass.workload.model.WorkloadModels.LateRiskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Daily sweep: for every active project run DriftDetector, and LateRiskDetector for incomplete tasks due
 * within LATE_RISK_WINDOW_DAYS (raising/resolving LATE_RISK alerts). Owner: Liv. Tests: Stress 14.
 * Must be safe when runs overlap (AlertDao.insertIfNoneOpen handles duplicates).
 *
 * Each project is processed in its own transaction, so one failing project never stops the sweep.
 * Limitation: tasks with no assignee are not visited, because the task list comes from assignments.
 */
public class RiskDetectionJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(RiskDetectionJob.class);

    private final Database db;
    private final WorkloadDao workloadDao;
    private final DriftDetector drift;
    private final LateRiskDetector lateRisk;
    private final AlertService alerts;
    private final Clock clock;
    private final TaskDao taskDao;

    public RiskDetectionJob(Database db, WorkloadDao workloadDao, DriftDetector drift, LateRiskDetector lateRisk,
                            AlertService alerts, Clock clock, TaskDao taskDao) {
        this.db = db;
        this.workloadDao = workloadDao;
        this.drift = drift;
        this.lateRisk = lateRisk;
        this.alerts = alerts;
        this.clock = clock;
        this.taskDao = taskDao;
    }

    @Override
    public String name() {
        return "risk-detection";
    }

    @Override
    public Duration interval() {
        return Duration.ofHours(24);
    }

    @Override
    public void run() throws Exception {
        LocalDate today = LocalDate.now(clock);
        List<Long> projectIds = db.withConnection(c -> workloadDao.findActiveProjectIds(c));

        int failed = 0;
        for (long projectId : projectIds) {
            try {
                db.inTransaction(c -> {
                    drift.detectWorkloadDrift(c, projectId, today);
                    sweepLateRisk(c, projectId, today);
                    return Boolean.TRUE;
                });
            } catch (RuntimeException e) {
                failed++;
                log.error("Risk detection failed for project {}", projectId, e);
            }
        }
        log.info("Risk detection finished: {} projects, {} failed", projectIds.size(), failed);
    }

    /** Raises LATE_RISK alerts for at-risk tasks in the window and resolves alerts that no longer apply. */
    private void sweepLateRisk(Connection c, long projectId, LocalDate today) throws SQLException {
        LocalDate windowEnd = today.plusDays(Rules.LATE_RISK_WINDOW_DAYS);

        // Incomplete tasks due within the window, plus overdue ones (they are certainly late).
        Set<Long> candidateTaskIds = new LinkedHashSet<>();
        for (AssignmentRow row : taskDao.findActiveAssignments(c, projectId)) {
            if (row.dueDate() != null && !row.dueDate().isAfter(windowEnd)) {
                candidateTaskIds.add(row.taskId());
            }
        }

        Set<Long> atRiskTaskIds = new HashSet<>();
        if (!candidateTaskIds.isEmpty()) {
            for (Task task : taskDao.findByIds(c, new ArrayList<>(candidateTaskIds))) {
                LateRiskStatus status = lateRisk.detectLateRisk(c, task, today);
                Severity severity = severityFor(status);
                if (severity != null && severity != Severity.LOW) {
                    atRiskTaskIds.add(task.taskId());
                    alerts.raise(c, projectId, null, task.taskId(), AlertType.LATE_RISK, severity,
                            reasonFor(task, status));
                }
            }
        }

        // Anything still open that is not at risk now (done, re-dated, unblocked, back on track) is resolved.
        alerts.resolveTaskAlertsExcept(c, projectId, AlertType.LATE_RISK, atRiskTaskIds);
    }

    /** null = not at risk. */
    private static Severity severityFor(LateRiskStatus status) {
        return switch (status) {
            case OVERDUE -> Severity.HIGH;
            case AT_RISK_UNRESOLVED_BLOCKER, AT_RISK_INSUFFICIENT_CAPACITY -> Severity.MEDIUM;
            case NO_RISK, DUE_SOON_ON_TRACK, NO_IMMEDIATE_RISK -> Severity.LOW;
            default -> null;
        };
    }

    private static String reasonFor(Task task, LateRiskStatus status) {
        String title = "Task \"" + task.title() + "\"";
        return switch (status) {
            case OVERDUE -> title + " was due on " + task.dueDate() + " and is not done.";
            case AT_RISK_UNRESOLVED_BLOCKER -> title + " is due on " + task.dueDate()
                    + " and has an unresolved blocker.";
            case AT_RISK_INSUFFICIENT_CAPACITY -> title + " is due on " + task.dueDate()
                    + " but its assignees do not have enough remaining capacity to finish it.";
            case DUE_SOON_ON_TRACK -> title + " is due on" + task.dueDate()
            		+ " and is on track to be finished in time.";
            case NO_IMMEDIATE_RISK, NO_RISK -> title + " is not at risk.";
            default -> title + " is at risk.";
        };
    }
}
