package edu.usc.compass.teams.dao;

import edu.usc.compass.common.ApiException;
import edu.usc.compass.teams.model.TeamModels.Invitation;
import edu.usc.compass.teams.model.TeamModels.InvitationStatus;
import edu.usc.compass.teams.model.TeamRole;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL for team_invitations. Owner: Solomon. */
public class InvitationDao {

    public long insert(Connection c, long teamId, String inviteeEmail, TeamRole role, long invitedBy, Instant expiresAt) throws SQLException {
        String sql = """
                INSERT INTO team_invitations (
                    team_id,
                    invitee_email,
                    role,
                    invited_by,
                    expires_at
                )
                VALUES (?, ?, ?, ?, ?)
                RETURNING invitation_id
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setString(2, inviteeEmail);
            ps.setString(3, role.name());
            ps.setLong(4, invitedBy);
            ps.setObject(
                    5,
                    expiresAt.atOffset(ZoneOffset.UTC)
            );
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Creating the invitation did not return an invitation ID."
                    );
                }
    
                return rs.getLong("invitation_id");
            }
        }
    }

    public Optional<Invitation> findForUpdate(Connection c, long invitationId) throws SQLException {
        String sql = """
                SELECT i.invitation_id,
                       i.team_id,
                       t.name AS team_name,
                       i.invitee_email,
                       i.role,
                       i.status,
                       i.invited_by,
                       i.created_at,
                       i.expires_at,
                       i.responded_at
                FROM team_invitations i
                JOIN teams t
                  ON t.team_id = i.team_id
                WHERE i.invitation_id = ?
                FOR UPDATE OF i
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, invitationId);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
    
                return Optional.of(mapInvitation(rs));
            }
        }
    }

    public List<Invitation> findByTeam(Connection c, long teamId) throws SQLException {
        String sql = """
                SELECT i.invitation_id,
                       i.team_id,
                       t.name AS team_name,
                       i.invitee_email,
                       i.role,
                       i.status,
                       i.invited_by,
                       i.created_at,
                       i.expires_at,
                       i.responded_at
                FROM team_invitations i
                JOIN teams t
                  ON t.team_id = i.team_id
                WHERE i.team_id = ?
                ORDER BY i.created_at DESC, i.invitation_id DESC
                """;
    
        List<Invitation> invitations = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    invitations.add(mapInvitation(rs));
                }
            }
        }
    
        return invitations;
    }

    /** Case-insensitive email match. */
    public List<Invitation> findPendingByEmail(Connection c, String email) throws SQLException {
        String sql = """
                SELECT i.invitation_id,
                       i.team_id,
                       t.name AS team_name,
                       i.invitee_email,
                       i.role,
                       i.status,
                       i.invited_by,
                       i.created_at,
                       i.expires_at,
                       i.responded_at
                FROM team_invitations i
                JOIN teams t
                  ON t.team_id = i.team_id
                WHERE lower(i.invitee_email) = lower(?)
                  AND i.status = 'PENDING'
                ORDER BY i.created_at DESC, i.invitation_id DESC
                """;
    
        List<Invitation> invitations = new ArrayList<>();
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
    
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    invitations.add(mapInvitation(rs));
                }
            }
        }
    
        return invitations;
    }

    public boolean hasPending(Connection c, long teamId, String email) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM team_invitations
                    WHERE team_id = ?
                      AND lower(invitee_email) = lower(?)
                      AND status = 'PENDING'
                ) AS has_pending
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setString(2, email);
    
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Checking pending invitations did not return a result."
                    );
                }
    
                return rs.getBoolean("has_pending");
            }
        }
    }

    public void setStatus(Connection c, long invitationId, InvitationStatus status, Instant respondedAt) throws SQLException {
        String sql = """
                UPDATE team_invitations
                SET status = ?,
                    responded_at = ?
                WHERE invitation_id = ?
                """;
    
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
    
            if (respondedAt == null) {
                ps.setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setObject(
                        2,
                        respondedAt.atOffset(ZoneOffset.UTC)
                );
            }
    
            ps.setLong(3, invitationId);
    
            int affectedRows = ps.executeUpdate();
    
            if (affectedRows == 0) {
                throw ApiException.notFound();
            }
        }
    }

    private Invitation mapInvitation(ResultSet rs) throws SQLException {
        OffsetDateTime createdAt =
                rs.getObject("created_at", OffsetDateTime.class);
    
        OffsetDateTime expiresAt =
                rs.getObject("expires_at", OffsetDateTime.class);
    
        OffsetDateTime respondedAt =
                rs.getObject("responded_at", OffsetDateTime.class);
    
        return new Invitation(
                rs.getLong("invitation_id"),
                rs.getLong("team_id"),
                rs.getString("team_name"),
                rs.getString("invitee_email"),
                TeamRole.valueOf(rs.getString("role")),
                InvitationStatus.valueOf(rs.getString("status")),
                rs.getLong("invited_by"),
                createdAt.toInstant(),
                expiresAt.toInstant(),
                respondedAt == null ? null : respondedAt.toInstant()
        );
    }
}
