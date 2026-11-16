package edu.usc.compass.teams;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.EmailQueue;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.teams.TeamDtos.InviteRequest;
import edu.usc.compass.teams.dao.InvitationDao;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamModels.Invitation;
import edu.usc.compass.teams.model.TeamModels.Membership;

import java.time.Clock;
import java.util.List;

/**
 * Email invitations (expire after 7 days, matched by email, no token stored).
 * Owner: Solomon. Tests: Unit 9-12, Stress 9.
 */
public class InvitationService {

    private final Database db;
    private final InvitationDao invitationDao;
    private final MembershipDao membershipDao;
    private final EmailQueue emailQueue;
    private final NotificationService notifications;
    private final PermissionService permissions;
    private final Clock clock;

    public InvitationService(Database db, InvitationDao invitationDao, MembershipDao membershipDao, EmailQueue emailQueue,
                             NotificationService notifications, PermissionService permissions, Clock clock) {
        this.db = db;
        this.invitationDao = invitationDao;
        this.membershipDao = membershipDao;
        this.emailQueue = emailQueue;
        this.notifications = notifications;
        this.permissions = permissions;
        this.clock = clock;
    }

    /**
     * POST /api/teams/{teamId}/invitations. Team lead. Rejects invalid email, unknown role, an email that
     * already has a PENDING invite to this team, or a current member. Sets expiresAt = now + 7 days and
     * queues the invite email (EmailTemplates.invitation) — the link contains no token.
     */
    public Invitation invite(AuthContext ctx, long teamId, InviteRequest req) {
        throw Todo.of("Solomon", "InvitationService.invite");
    }

    /** GET /api/teams/{teamId}/invitations. Team lead. Report PENDING rows past expiresAt as EXPIRED. */
    public List<Invitation> listForTeam(AuthContext ctx, long teamId) {
        throw Todo.of("Solomon", "InvitationService.listForTeam");
    }

    /** DELETE /api/teams/{teamId}/invitations/{invitationId}. Team lead. Only PENDING can be revoked. */
    public void revoke(AuthContext ctx, long teamId, long invitationId) {
        throw Todo.of("Solomon", "InvitationService.revoke");
    }

    /** GET /api/users/me/invitations. Pending, unexpired invitations sent to ctx.email(). */
    public List<Invitation> listMine(AuthContext ctx) {
        throw Todo.of("Solomon", "InvitationService.listMine");
    }

    /**
     * POST .../accept. Invitee only (email must match). Lock the invitation row; anything other than an
     * unexpired PENDING invite -> 409 INVALID_STATE. Creates exactly one membership with the invited role,
     * sets ACCEPTED + respondedAt, notifies the leads.
     */
    public Membership accept(AuthContext ctx, long teamId, long invitationId) {
        throw Todo.of("Solomon", "InvitationService.accept");
    }

    /** POST .../decline. Invitee only. Sets DECLINED + respondedAt. */
    public void decline(AuthContext ctx, long teamId, long invitationId) {
        throw Todo.of("Solomon", "InvitationService.decline");
    }
}
