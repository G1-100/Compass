package edu.usc.compass.teams.model;

import java.time.Instant;

/** Team feature records and enums (tables teams, team_memberships, team_invitations, audit_log). Owner: Solomon. */
public final class TeamModels {

    private TeamModels() {
    }

    public record Team(long teamId, String name, long createdBy, Instant createdAt, int version) {
    }

    /** A team plus the requesting user's role in it (GET /api/teams). */
    public record TeamWithRole(Team team, TeamRole myRole) {
    }

    public record Membership(long membershipId, long teamId, long userId, String displayName, TeamRole role,
                             Instant joinedAt, int version) {
    }

    public enum InvitationStatus {
        PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED
    }

    public record Invitation(long invitationId, long teamId, String teamName, String inviteeEmail, TeamRole role,
                             InvitationStatus status, long invitedBy, Instant createdAt, Instant expiresAt,
                             Instant respondedAt) {

        /**
         * True only once {@code now} is strictly after expiresAt (exactly at expiresAt is NOT expired).
         * A PENDING row past expiresAt counts as EXPIRED. Tests: Unit 9.
         */
        public boolean isExpired(Instant now) {
            // TODO(Solomon): implement
            throw edu.usc.compass.common.Todo.of("Solomon", "Invitation.isExpired");
        }
    }

    /** The three ways a membership changes; all go through MembershipService.updateTeamMembership. */
    public enum MembershipAction {
        CHANGE_ROLE, REMOVE, LEAVE
    }

    public enum AuditEventType {
        ROLE_CHANGED, MEMBER_REMOVED, MEMBER_LEFT, GUEST_LINK_CREATED, GUEST_LINK_REVOKED
    }

    public record AuditEvent(long auditId, long teamId, Long actorUserId, Long targetUserId, AuditEventType eventType,
                             String details, Instant createdAt) {
    }
}
