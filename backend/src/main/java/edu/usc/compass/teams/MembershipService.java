package edu.usc.compass.teams;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamModels.MembershipAction;
import edu.usc.compass.teams.model.TeamRole;

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
        throw Todo.of("Solomon", "MembershipService.updateTeamMembership");
    }
}
