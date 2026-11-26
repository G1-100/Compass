package edu.usc.compass.workload.dao;

import edu.usc.compass.common.Severity;
import edu.usc.compass.workload.model.WorkloadModels.Alert;
import edu.usc.compass.workload.model.WorkloadModels.AlertStatus;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL for workload_alerts. Owner: Liv. */
public class AlertDao {

    private static final String COLUMNS = "alert_id, project_id, user_id, task_id, alert_type, severity, reason, "
            + "rule_version, status, created_at, acknowledged_by, acknowledged_at, resolved_at";

    /**
     * INSERT ... ON CONFLICT DO NOTHING against uq_alert_open, so overlapping job runs never create duplicates.
     * uq_alert_open covers every status except RESOLVED, so an ACKNOWLEDGED alert still blocks a new one.
     * Returns the new id, or empty if an equivalent open alert already exists.
     */
    public Optional<Long> insertIfNoneOpen(Connection c, long projectId, Long userId, Long taskId, AlertType type,
                                           Severity severity, String reason, String ruleVersion) throws SQLException {
        String sql = "INSERT INTO workload_alerts (project_id, user_id, task_id, alert_type, severity, reason, rule_version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING alert_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            setNullableLong(ps, 2, userId);
            setNullableLong(ps, 3, taskId);
            ps.setString(4, type.name());
            ps.setString(5, severity.name());
            ps.setString(6, reason);
            ps.setString(7, ruleVersion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    /** Marks every open or acknowledged alert of this type for the member/task as RESOLVED. */
    public void resolveOpen(Connection c, long projectId, Long userId, Long taskId, AlertType type) throws SQLException {
        // COALESCE mirrors uq_alert_open, so a null user or task matches a null user or task.
        String sql = "UPDATE workload_alerts SET status = 'RESOLVED', resolved_at = now() "
                + "WHERE project_id = ? AND alert_type = ? AND COALESCE(user_id, 0) = ? AND COALESCE(task_id, 0) = ? "
                + "AND status <> 'RESOLVED'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setString(2, type.name());
            ps.setLong(3, userId == null ? 0L : userId);
            ps.setLong(4, taskId == null ? 0L : taskId);
            ps.executeUpdate();
        }
    }

    /** status null = all. Newest first. */
    public List<Alert> findByProject(Connection c, long projectId, AlertStatus status) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM workload_alerts WHERE project_id = ? "
                + (status == null ? "" : "AND status = ? ")
                + "ORDER BY created_at DESC, alert_id DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            if (status != null) {
                ps.setString(2, status.name());
            }
            return readAll(ps);
        }
    }

    /** Alerts of one type that are not RESOLVED (OPEN or ACKNOWLEDGED). Used to clean up stale alerts. */
    public List<Alert> findOpenByType(Connection c, long projectId, AlertType type) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM workload_alerts "
                + "WHERE project_id = ? AND alert_type = ? AND status <> 'RESOLVED' ORDER BY alert_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setString(2, type.name());
            return readAll(ps);
        }
    }

    public Optional<Alert> findById(Connection c, long alertId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM workload_alerts WHERE alert_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, alertId);
            List<Alert> rows = readAll(ps);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
    }

    /** ACKNOWLEDGED records who and when; RESOLVED records when. OPEN is never set from here. */
    public void setStatus(Connection c, long alertId, AlertStatus status, long actingUserId) throws SQLException {
        switch (status) {
            case ACKNOWLEDGED -> {
                String sql = "UPDATE workload_alerts SET status = 'ACKNOWLEDGED', acknowledged_by = ?, "
                        + "acknowledged_at = now() WHERE alert_id = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, actingUserId);
                    ps.setLong(2, alertId);
                    ps.executeUpdate();
                }
            }
            case RESOLVED -> {
                String sql = "UPDATE workload_alerts SET status = 'RESOLVED', resolved_at = now() WHERE alert_id = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, alertId);
                    ps.executeUpdate();
                }
            }
            default -> throw new IllegalArgumentException("Alerts can only be set to ACKNOWLEDGED or RESOLVED");
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static List<Alert> readAll(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Alert> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        }
    }

    private static Alert map(ResultSet rs) throws SQLException {
        return new Alert(
                rs.getLong("alert_id"),
                rs.getLong("project_id"),
                getNullableLong(rs, "user_id"),
                getNullableLong(rs, "task_id"),
                AlertType.valueOf(rs.getString("alert_type")),
                Severity.valueOf(rs.getString("severity")),
                rs.getString("reason"),
                rs.getString("rule_version"),
                AlertStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("created_at")),
                getNullableLong(rs, "acknowledged_by"),
                toInstant(rs.getTimestamp("acknowledged_at")),
                toInstant(rs.getTimestamp("resolved_at")));
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
