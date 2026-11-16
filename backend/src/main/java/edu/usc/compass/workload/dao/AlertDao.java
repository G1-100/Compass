package edu.usc.compass.workload.dao;

import edu.usc.compass.common.Severity;
import edu.usc.compass.common.Todo;
import edu.usc.compass.workload.model.WorkloadModels.Alert;
import edu.usc.compass.workload.model.WorkloadModels.AlertStatus;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** SQL for workload_alerts. Owner: Liv. */
public class AlertDao {

    /**
     * INSERT ... ON CONFLICT DO NOTHING against uq_alert_open, so overlapping job runs never create duplicates.
     * Returns the new id, or empty if an equivalent open alert already exists.
     */
    public Optional<Long> insertIfNoneOpen(Connection c, long projectId, Long userId, Long taskId, AlertType type,
                                           Severity severity, String reason, String ruleVersion) {
        throw Todo.of("Liv", "AlertDao.insertIfNoneOpen");
    }

    public void resolveOpen(Connection c, long projectId, Long userId, Long taskId, AlertType type) {
        throw Todo.of("Liv", "AlertDao.resolveOpen");
    }

    /** status null = all. Newest first. */
    public List<Alert> findByProject(Connection c, long projectId, AlertStatus status) {
        throw Todo.of("Liv", "AlertDao.findByProject");
    }

    public Optional<Alert> findById(Connection c, long alertId) {
        throw Todo.of("Liv", "AlertDao.findById");
    }

    public void setStatus(Connection c, long alertId, AlertStatus status, long actingUserId) {
        throw Todo.of("Liv", "AlertDao.setStatus");
    }
}
