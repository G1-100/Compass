package edu.usc.compass.workload.dao;

import edu.usc.compass.workload.model.WorkloadModels.Availability;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL for member_availability. Owner: Liv. */
public class WorkloadDao {

    private static final String COLUMNS = "project_id, user_id, hours_per_week, reports_overload, updated_at";

    /** INSERT ... ON CONFLICT (project_id, user_id) DO UPDATE. */
    public void upsertAvailability(Connection c, long projectId, long userId, BigDecimal hoursPerWeek,
                                   boolean reportsOverload) throws SQLException {
        String sql = "INSERT INTO member_availability (project_id, user_id, hours_per_week, reports_overload, updated_at) "
                + "VALUES (?, ?, ?, ?, now()) "
                + "ON CONFLICT (project_id, user_id) DO UPDATE SET "
                + "hours_per_week = EXCLUDED.hours_per_week, "
                + "reports_overload = EXCLUDED.reports_overload, "
                + "updated_at = now()";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setLong(2, userId);
            ps.setBigDecimal(3, hoursPerWeek);
            ps.setBoolean(4, reportsOverload);
            ps.executeUpdate();
        }
    }

    public List<Availability> findAvailability(Connection c, long projectId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM member_availability WHERE project_id = ? ORDER BY user_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Availability> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        }
    }

    public Optional<Availability> findAvailability(Connection c, long projectId, long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM member_availability WHERE project_id = ? AND user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Ids of active (non-archived) projects, for the daily risk sweep. */
    public List<Long> findActiveProjectIds(Connection c) throws SQLException {
        String sql = "SELECT project_id FROM projects WHERE archived_at IS NULL ORDER BY project_id";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }

    private static Availability map(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new Availability(
                rs.getLong("project_id"),
                rs.getLong("user_id"),
                rs.getBigDecimal("hours_per_week"),
                rs.getBoolean("reports_overload"),
                updatedAt == null ? null : updatedAt.toInstant());
    }
}
