package edu.usc.compass.teams;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Validator;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.notifications.model.NotificationModels.NotificationRequest;
import edu.usc.compass.notifications.model.NotificationModels.NotificationType;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamModels.AuditEventType;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.teams.model.TeamModels.MembershipAction;
import edu.usc.compass.teams.model.TeamRole;

import java.util.List;

/**
 * Role changes, removals and leaving, with Last-Lead Protection (Detailed Design 3.4).
 * Owner: Solomon. Tests: White box 6, Black box 10, Unit 1-6, Stress 9.
 */
public class MembershipService {

    private final Database db;
    private final MembershipDao membershipDao;
    private final AuditLogService auditLog;
    private final NotificationService notifications;
    private final PermissionService permissions;

    public MembershipService(Database db, MembershipDao membershipDao, AuditLogService auditLog,
                             NotificationService notifications, PermissionService permissions) {
        this.db = db;
        this.membershipDao = membershipDao;
        this.auditLog = auditLog;
        this.notifications = notifications;
        this.permissions = permissions;
    }

    /**
     * The UpdateTeamMembership function from the design, in ONE transaction:
     * 1. Lock the team's lead rows (MembershipDao.findLeadsForUpdate) and the target membership.
     * 2. Target missing -> 404. For CHANGE_ROLE/REMOVE the actor must be a lead (LEAVE does not).
     * 3. CHANGE_ROLE with a stale version -> 409 VERSION_CONFLICT.
     * 4. If the target loses the LEAD role and is the only lead -> 409 INVALID_STATE, nothing changes.
     * 5. Apply the change, record an audit event (ROLE_CHANGED / MEMBER_REMOVED / MEMBER_LEFT).
     * 6. For REMOVE/LEAVE, notify the leads (queued in the same transaction).
     *
     * @param newRole          only used for CHANGE_ROLE
     * @param submittedVersion only used for CHANGE_ROLE
     */
    public void updateTeamMembership(AuthContext ctx, long teamId, long targetUserId, MembershipAction action,
                                     TeamRole newRole, Integer submittedVersion) {
        Validator validator = new Validator()
                .required("action", action);
    
        if (action == MembershipAction.CHANGE_ROLE) {
            validator
                    .required("role", newRole)
                    .required("version", submittedVersion)
                    .check(
                            submittedVersion == null || submittedVersion >= 1,
                            "version",
                            "Version must be at least 1."
                    );
        }
    
        validator.throwIfInvalid();
    
        if (action == MembershipAction.LEAVE
                && targetUserId != ctx.userId()) {
            throw ApiException.forbidden();
        }
    
        db.inTransaction(c -> {
            // Always lock leads before the target membership.
            List<Membership> leads =
                    membershipDao.findLeadsForUpdate(c, teamId);
    
            Membership target = membershipDao
                    .findForUpdate(c, teamId, targetUserId)
                    .orElseThrow(ApiException::notFound);
    
            if (action != MembershipAction.LEAVE) {
                permissions.require(
                        ctx,
                        Permission.MANAGE_MEMBERS,
                        teamId
                );
    
                // Also verify the actor is still a lead in the database.
                boolean actorIsCurrentLead = leads.stream()
                        .anyMatch(lead -> lead.userId() == ctx.userId());
    
                if (!ctx.isSystemAdmin() && !actorIsCurrentLead) {
                    throw ApiException.forbidden();
                }
            }
    
            if (action == MembershipAction.CHANGE_ROLE
                    && submittedVersion.intValue() != target.version()) {
                throw ApiException.versionConflict(target);
            }
    
            boolean losesLeadRole =
                    target.role() == TeamRole.LEAD
                            && (action != MembershipAction.CHANGE_ROLE
                                || newRole != TeamRole.LEAD);
    
            if (losesLeadRole && leads.size() <= 1) {
                throw ApiException.invalidState(
                        "A team must have at least one lead."
                );
            }
    
            if (action == MembershipAction.CHANGE_ROLE) {
                // An unchanged role is a successful no-op.
                if (target.role() == newRole) {
                    return null;
                }
    
                membershipDao.updateRole(
                        c,
                        target.membershipId(),
                        newRole,
                        target.version() + 1
                );
    
                auditLog.record(
                        c,
                        teamId,
                        ctx.userId(),
                        target.userId(),
                        AuditEventType.ROLE_CHANGED,
                        "Changed role from " + target.role()
                                + " to " + newRole + "."
                );
            } else {
                membershipDao.delete(c, target.membershipId());
    
                boolean leaving = action == MembershipAction.LEAVE;
    
                AuditEventType eventType = leaving
                        ? AuditEventType.MEMBER_LEFT
                        : AuditEventType.MEMBER_REMOVED;
    
                NotificationType notificationType = leaving
                        ? NotificationType.MEMBER_LEFT
                        : NotificationType.MEMBER_REMOVED;
    
                String message = leaving
                        ? target.displayName() + " left team " + teamId + "."
                        : target.displayName()
                                + " was removed from team " + teamId + ".";
    
                auditLog.record(
                        c,
                        teamId,
                        ctx.userId(),
                        target.userId(),
                        eventType,
                        message
                );
    
                List<Long> recipientIds = leads.stream()
                        .filter(lead -> lead.userId() != target.userId())
                        .map(Membership::userId)
                        .toList();
    
                if (!recipientIds.isEmpty()) {
                    notifications.notify(
                            c,
                            new NotificationRequest(
                                    recipientIds,
                                    notificationType,
                                    null,
                                    null,
                                    message,
                                    true
                            )
                    );
                }
            }
    
            return null;
        });
    }
}
