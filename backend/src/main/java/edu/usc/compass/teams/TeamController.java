package edu.usc.compass.teams;

import edu.usc.compass.common.web.Router;
import edu.usc.compass.teams.TeamDtos.ChangeRoleRequest;
import edu.usc.compass.teams.TeamDtos.CreateTeamRequest;
import edu.usc.compass.teams.TeamDtos.InviteRequest;
import edu.usc.compass.teams.TeamDtos.RenameTeamRequest;
import edu.usc.compass.teams.model.TeamModels.MembershipAction;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Teams and Membership (Detailed Design 3.5.2). Owner: Solomon. */
public final class TeamController {

    private final TeamService teams;
    private final MembershipService memberships;
    private final InvitationService invitations;
    private final AuditLogService auditLog;

    public TeamController(TeamService teams, MembershipService memberships, InvitationService invitations, AuditLogService auditLog) {
        this.teams = teams;
        this.memberships = memberships;
        this.invitations = invitations;
        this.auditLog = auditLog;
    }

    public void register(Router r) {
        r.post("/api/teams", SIGNED_IN, ctx -> {
            var created = teams.createTeam(ctx.auth(), ctx.body(CreateTeamRequest.class));
            return ctx.created("/api/teams/" + created.team().teamId(), created);
        });
        r.get("/api/teams", SIGNED_IN, ctx -> teams.listMyTeams(ctx.auth()));
        r.get("/api/teams/{teamId}", SIGNED_IN, ctx -> teams.getTeam(ctx.auth(), ctx.pathLong("teamId")));
        r.patch("/api/teams/{teamId}", SIGNED_IN, ctx -> teams.renameTeam(ctx.auth(), ctx.pathLong("teamId"), ctx.body(RenameTeamRequest.class)));
        r.delete("/api/teams/{teamId}", SIGNED_IN, ctx -> {
            teams.deleteTeam(ctx.auth(), ctx.pathLong("teamId"));
            return null;
        });

        r.get("/api/teams/{teamId}/members", SIGNED_IN, ctx -> teams.listMembers(ctx.auth(), ctx.pathLong("teamId")));
        r.patch("/api/teams/{teamId}/members/{userId}", SIGNED_IN, ctx -> {
            var body = ctx.body(ChangeRoleRequest.class);
            memberships.updateTeamMembership(ctx.auth(), ctx.pathLong("teamId"), ctx.pathLong("userId"),
                    MembershipAction.CHANGE_ROLE, body.role(), body.version());
            return null;
        });
        r.delete("/api/teams/{teamId}/members/me", SIGNED_IN, ctx -> {
            memberships.updateTeamMembership(ctx.auth(), ctx.pathLong("teamId"), ctx.auth().userId(),
                    MembershipAction.LEAVE, null, null);
            return null;
        });
        r.delete("/api/teams/{teamId}/members/{userId}", SIGNED_IN, ctx -> {
            memberships.updateTeamMembership(ctx.auth(), ctx.pathLong("teamId"), ctx.pathLong("userId"),
                    MembershipAction.REMOVE, null, null);
            return null;
        });

        r.post("/api/teams/{teamId}/invitations", SIGNED_IN, ctx -> {
            var inv = invitations.invite(ctx.auth(), ctx.pathLong("teamId"), ctx.body(InviteRequest.class));
            return ctx.created("/api/teams/" + inv.teamId() + "/invitations/" + inv.invitationId(), inv);
        });
        r.get("/api/teams/{teamId}/invitations", SIGNED_IN, ctx -> invitations.listForTeam(ctx.auth(), ctx.pathLong("teamId")));
        r.delete("/api/teams/{teamId}/invitations/{invitationId}", SIGNED_IN, ctx -> {
            invitations.revoke(ctx.auth(), ctx.pathLong("teamId"), ctx.pathLong("invitationId"));
            return null;
        });
        r.get("/api/users/me/invitations", SIGNED_IN, ctx -> invitations.listMine(ctx.auth()));
        r.post("/api/teams/{teamId}/invitations/{invitationId}/accept", SIGNED_IN,
                ctx -> invitations.accept(ctx.auth(), ctx.pathLong("teamId"), ctx.pathLong("invitationId")));
        r.post("/api/teams/{teamId}/invitations/{invitationId}/decline", SIGNED_IN, ctx -> {
            invitations.decline(ctx.auth(), ctx.pathLong("teamId"), ctx.pathLong("invitationId"));
            return null;
        });

        r.get("/api/teams/{teamId}/audit-log", SIGNED_IN, ctx -> auditLog.list(ctx.auth(), ctx.pathLong("teamId"), ctx.page()));
    }
}
