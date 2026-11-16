package edu.usc.compass.teams;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.teams.TeamDtos.CreateTeamRequest;
import edu.usc.compass.teams.TeamDtos.RenameTeamRequest;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.dao.TeamDao;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.teams.model.TeamModels.Team;
import edu.usc.compass.teams.model.TeamModels.TeamWithRole;

import java.util.List;

/** Team CRUD and member listing. Owner: Solomon. Tests: Unit 7-8. */
public class TeamService {

    private final Database db;
    private final TeamDao teamDao;
    private final MembershipDao membershipDao;
    private final PermissionService permissions;

    public TeamService(Database db, TeamDao teamDao, MembershipDao membershipDao, PermissionService permissions) {
        this.db = db;
        this.teamDao = teamDao;
        this.membershipDao = membershipDao;
        this.permissions = permissions;
    }

    /** POST /api/teams. Name 1-60 chars (trimmed, not blank). Creator becomes LEAD in the same transaction. */
    public TeamWithRole createTeam(AuthContext ctx, CreateTeamRequest req) {
        throw Todo.of("Solomon", "TeamService.createTeam");
    }

    /** GET /api/teams. The user's teams and role in each; system admins see all teams. */
    public List<TeamWithRole> listMyTeams(AuthContext ctx) {
        throw Todo.of("Solomon", "TeamService.listMyTeams");
    }

    /** GET /api/teams/{teamId}. Team member. */
    public TeamWithRole getTeam(AuthContext ctx, long teamId) {
        throw Todo.of("Solomon", "TeamService.getTeam");
    }

    /** PATCH /api/teams/{teamId}. Team lead; version-checked. */
    public Team renameTeam(AuthContext ctx, long teamId, RenameTeamRequest req) {
        throw Todo.of("Solomon", "TeamService.renameTeam");
    }

    /** DELETE /api/teams/{teamId}. Team lead. Cascades to memberships, invitations, projects, check-ins only. */
    public void deleteTeam(AuthContext ctx, long teamId) {
        throw Todo.of("Solomon", "TeamService.deleteTeam");
    }

    /** GET /api/teams/{teamId}/members. Team member. */
    public List<Membership> listMembers(AuthContext ctx, long teamId) {
        throw Todo.of("Solomon", "TeamService.listMembers");
    }
}
