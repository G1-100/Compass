package edu.usc.compass.workload;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Rules;
import edu.usc.compass.common.Severity;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.notifications.model.NotificationModels.NotificationRequest;
import edu.usc.compass.notifications.model.NotificationModels.NotificationType;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.teams.model.TeamRole;
import edu.usc.compass.workload.WorkloadDtos.UpdateAlertRequest;
import edu.usc.compass.workload.dao.AlertDao;
import edu.usc.compass.workload.model.WorkloadModels.Alert;
import edu.usc.compass.workload.model.WorkloadModels.AlertStatus;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Workload alerts (overload, capacity, drift, late risk). Owner: Liv. */
public class AlertService {

    /** notifications.message is VARCHAR(1000). */
    private static final int MAX_NOTIFICATION_LENGTH = 1000;

    private final Database db;
    private final AlertDao alertDao;
    private final ProjectService projects;
    private final NotificationService notifications;
    private final ProjectDao projectDao;
    private final MembershipDao membershipDao;

    public AlertService(Database db, AlertDao alertDao, ProjectService projects, NotificationService notifications,
                        ProjectDao projectDao, MembershipDao membershipDao) {
        this.db = db;
        this.alertDao = alertDao;
        this.projects = projects;
        this.notifications = notifications;
        this.projectDao = projectDao;
        this.membershipDao = membershipDao;
    }

    /**
     * Creates an alert only if no equivalent open one exists (stores reason + Rules.CURRENT_RULE_VERSION),
     * then notifies the member and the team leads (WORKLOAD_ALERT). Returns true if a new alert was created.
     * High-severity alerts are also emailed.
     */
    public boolean raise(Connection c, long projectId, Long userId, Long taskId, AlertType type, Severity severity,
                         String reason) throws SQLException {
        Optional<Long> created = alertDao.insertIfNoneOpen(c, projectId, userId, taskId, type, severity, reason,
                Rules.CURRENT_RULE_VERSION);
        if (created.isEmpty()) {
            return false;
        }

        Set<Long> recipients = new LinkedHashSet<>();
        if (userId != null) {
            recipients.add(userId);
        }
        long teamId = projectDao.findTeamId(c, projectId);
        for (Membership member : membershipDao.findByTeam(c, teamId)) {
            if (member.role() == TeamRole.LEAD) {
                recipients.add(member.userId());
            }
        }
        if (!recipients.isEmpty()) {
            notifications.notify(c, new NotificationRequest(new ArrayList<>(recipients),
                    NotificationType.WORKLOAD_ALERT, projectId, taskId, truncate(reason),
                    severity == Severity.HIGH));
        }
        return true;
    }

    /** Resolves the open alert of this type for the member/task, if any (the condition cleared). */
    public void resolve(Connection c, long projectId, Long userId, Long taskId, AlertType type) throws SQLException {
        alertDao.resolveOpen(c, projectId, userId, taskId, type);
    }

    /**
     * Resolves every open alert of this type whose task is not in {@code stillAtRiskTaskIds}. Used by the daily
     * sweep so alerts for tasks that were finished, re-dated or deleted from view do not stay open forever.
     */
    public void resolveTaskAlertsExcept(Connection c, long projectId, AlertType type, Set<Long> stillAtRiskTaskIds)
            throws SQLException {
        for (Alert alert : alertDao.findOpenByType(c, projectId, type)) {
            if (alert.taskId() != null && !stillAtRiskTaskIds.contains(alert.taskId())) {
                alertDao.resolveOpen(c, projectId, alert.userId(), alert.taskId(), type);
            }
        }
    }

    /** GET /api/projects/{projectId}/alerts?status=. Team lead. */
    public List<Alert> list(AuthContext ctx, long projectId, AlertStatus status) {
        return db.withConnection(c -> {
            projects.requirePermission(c, ctx, projectId, Permission.VIEW_WORKLOAD);
            return alertDao.findByProject(c, projectId, status);
        });
    }

    /**
     * PATCH /api/projects/{projectId}/alerts/{alertId}. Team lead. Acknowledge or resolve.
     * OPEN -> ACKNOWLEDGED or RESOLVED, ACKNOWLEDGED -> RESOLVED. A resolved alert cannot be changed (409).
     */
    public Alert update(AuthContext ctx, long projectId, long alertId, UpdateAlertRequest req) {
        return db.inTransaction(c -> {
            projects.requirePermission(c, ctx, projectId, Permission.VIEW_WORKLOAD);

            if (req == null || req.status() == null) {
                throw ApiException.validation("status", "Status is required.");
            }
            AlertStatus requested = req.status();
            if (requested != AlertStatus.ACKNOWLEDGED && requested != AlertStatus.RESOLVED) {
                throw ApiException.validation("status", "Status must be ACKNOWLEDGED or RESOLVED.");
            }

            // An alert in another project is reported as missing, not forbidden.
            Alert current = alertDao.findById(c, alertId)
                    .filter(a -> a.projectId() == projectId)
                    .orElseThrow(ApiException::notFound);

            if (current.status() == AlertStatus.RESOLVED) {
                throw ApiException.invalidState("This alert is already resolved.");
            }
            if (current.status() == requested) {
                return current;
            }

            alertDao.setStatus(c, alertId, requested, ctx.userId());
            return alertDao.findById(c, alertId).orElseThrow(ApiException::notFound);
        });
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_NOTIFICATION_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_NOTIFICATION_LENGTH - 3) + "...";
    }
}
