package edu.usc.compass.teams.dao;

import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.teams.model.TeamModels.AuditEvent;
import edu.usc.compass.teams.model.TeamModels.AuditEventType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL for audit_log. Owner: Solomon. */
public class AuditDao {

    public void insert(Connection c, long teamId, Long actorUserId, Long targetUserId, AuditEventType type, String details) throws SQLException {
        String sql = """
                INSERT INTO audit_log (
                    team_id,
                    actor_user_id,
                    target_user_id,
                    event_type,
                    details
                )
                VALUES (?, ?, ?, ?, ?)
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            if (actorUserId == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, actorUserId);
            }
    
            if (targetUserId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, targetUserId);
            }
    
            ps.setString(4, type.name());
            ps.setString(5, details);
    
            int affectedRows = ps.executeUpdate();
    
            if (affectedRows != 1) {
                throw new SQLException(
                        "Creating the audit event did not insert exactly one row."
                );
            }
        }
    }

    public List<AuditEvent> findByTeam(Connection c, long teamId, PageRequest page) throws SQLException {
        String sql = """
                SELECT audit_id,
                       team_id,
                       actor_user_id,
                       target_user_id,
                       event_type,
                       details,
                       created_at
                FROM audit_log
                WHERE team_id = ?
                ORDER BY created_at DESC, audit_id DESC
                LIMIT ? OFFSET ?
                """;
    
        List<AuditEvent> events = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setInt(2, page.limit());
            ps.setInt(3, page.offset());
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapAuditEvent(rs));
                }
            }
        }
    
        return events;
    }

    public long countByTeam(Connection c, long teamId) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS total
                FROM audit_log
                WHERE team_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Counting audit events did not return a result."
                    );
                }
    
                return rs.getLong("total");
            }
        }
    }

    private AuditEvent mapAuditEvent(ResultSet rs) throws SQLException {
        OffsetDateTime createdAt =
                rs.getObject("created_at", OffsetDateTime.class);
    
        return new AuditEvent(
                rs.getLong("audit_id"),
                rs.getLong("team_id"),
                rs.getObject("actor_user_id", Long.class),
                rs.getObject("target_user_id", Long.class),
                AuditEventType.valueOf(rs.getString("event_type")),
                rs.getString("details"),
                createdAt.toInstant()
        );
    }
}
