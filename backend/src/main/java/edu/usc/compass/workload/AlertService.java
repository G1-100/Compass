package edu.usc.compass.workload;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Severity;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.workload.WorkloadDtos.UpdateAlertRequest;
import edu.usc.compass.workload.dao.AlertDao;
import edu.usc.compass.workload.model.WorkloadModels.Alert;
import edu.usc.compass.workload.model.WorkloadModels.AlertStatus;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;

import java.sql.Connection;
import java.util.List;

/** Workload alerts (overload, capacity, drift, late risk). Owner: Liv. */
public class AlertService {

    private final Database db;
    private final AlertDao alertDao;
    private final ProjectService projects;
    private final NotificationService notifications;

    public AlertService(Database db, AlertDao alertDao, ProjectService projects, NotificationService notifications) {
        this.db = db;
        this.alertDao = alertDao;
        this.projects = projects;
        this.notifications = notifications;
    }

    /**
     * Creates an alert only if no equivalent open one exists (stores reason + Rules.CURRENT_RULE_VERSION),
     * then notifies the member and the team leads (WORKLOAD_ALERT). Returns true if a new alert was created.
     */
    public boolean raise(Connection c, long projectId, Long userId, Long taskId, AlertType type, Severity severity, String reason) {
        throw Todo.of("Liv", "AlertService.raise");
    }

    /** Resolves the open alert of this type for the member/task, if any (the condition cleared). */
    public void resolve(Connection c, long projectId, Long userId, Long taskId, AlertType type) {
        throw Todo.of("Liv", "AlertService.resolve");
    }

    /** GET /api/projects/{projectId}/alerts?status=. Team lead. */
    public List<Alert> list(AuthContext ctx, long projectId, AlertStatus status) {
        throw Todo.of("Liv", "AlertService.list");
    }

    /** PATCH /api/projects/{projectId}/alerts/{alertId}. Team lead. Acknowledge or resolve. */
    public Alert update(AuthContext ctx, long projectId, long alertId, UpdateAlertRequest req) {
        throw Todo.of("Liv", "AlertService.update");
    }
}
