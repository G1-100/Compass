package edu.usc.compass.teams;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Validator;
import edu.usc.compass.teams.model.TeamRole;
import edu.usc.compass.teams.TeamDtos.CreateTeamRequest;
import edu.usc.compass.teams.TeamDtos.RenameTeamRequest;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.dao.TeamDao;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.teams.model.TeamModels.Team;
import edu.usc.compass.teams.model.TeamModels.TeamWithRole;

import java.util.ArrayList;
import java.util.Comparator;
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
        if (req == null) {
            throw ApiException.badRequest(
                    "A team request body is required."
            );
        }
    
        String name = req.name() == null
                ? null
                : req.name().trim();
    
        new Validator()
                .required("name", name)
                .maxLength("name", name, 60)
                .throwIfInvalid();
    
        return db.inTransaction(c -> {
            long teamId = teamDao.insert(
                    c,
                    name,
                    ctx.userId()
            );
    
            membershipDao.insert(
                    c,
                    teamId,
                    ctx.userId(),
                    TeamRole.LEAD
            );
    
            Team team = teamDao.findById(c, teamId)
                    .orElseThrow(() -> new IllegalStateException(
                            "The newly created team could not be retrieved."
                    ));
    
            return new TeamWithRole(team, TeamRole.LEAD);
        });
    }

    /** GET /api/teams. The user's teams and role in each; system admins see all teams. */
    public List<TeamWithRole> listMyTeams(AuthContext ctx) {
        return db.withConnection(c -> {
            var roles = membershipDao.findRolesForUser(
                    c,
                    ctx.userId()
            );
    
            List<TeamWithRole> results = new ArrayList<>();
    
            if (ctx.isSystemAdmin()) {
                for (Team team : teamDao.findAll(c)) {
                    results.add(new TeamWithRole(
                            team,
                            roles.get(team.teamId())
                    ));
                }
            } else {
                for (var entry : roles.entrySet()) {
                    var team = teamDao.findById(c, entry.getKey());
    
                    if (team.isPresent()) {
                        results.add(new TeamWithRole(
                                team.get(),
                                entry.getValue()
                        ));
                    }
                }
            }
    
            results.sort(
                    Comparator.comparing(
                            (TeamWithRole result) -> result.team().name(),
                            String.CASE_INSENSITIVE_ORDER
                    ).thenComparingLong(result -> result.team().teamId())
            );
    
            return results;
        });
    }

    /** GET /api/teams/{teamId}. Team member. */
    public TeamWithRole getTeam(AuthContext ctx, long teamId) {
        return db.withConnection(c -> {
            Team team = teamDao.findById(c, teamId)
                    .orElseThrow(ApiException::notFound);
    
            permissions.require(
                    ctx,
                    Permission.VIEW_WORKSPACE,
                    teamId
            );
    
            TeamRole myRole = membershipDao.find(c, teamId, ctx.userId())
                    .map(Membership::role)
                    .orElse(null);
    
            return new TeamWithRole(team, myRole);
        });
    }

    /** PATCH /api/teams/{teamId}. Team lead; version-checked. */
    public Team renameTeam(AuthContext ctx, long teamId, RenameTeamRequest req) {
        if (req == null) {
            throw ApiException.badRequest(
                    "A rename request body is required."
            );
        }
    
        return db.inTransaction(c -> {
            Team existing = teamDao.findById(c, teamId)
                    .orElseThrow(ApiException::notFound);
    
            boolean isTeamLead = ctx.roleIn(teamId)
                    .map(role -> role.atLeast(TeamRole.LEAD))
                    .orElse(false);
    
            if (!ctx.isSystemAdmin() && !isTeamLead) {
                throw ApiException.forbidden();
            }
    
            String name = req.name() == null
                    ? null
                    : req.name().trim();
    
            new Validator()
                    .required("name", name)
                    .maxLength("name", name, 60)
                    .check(
                            req.version() >= 1,
                            "version",
                            "Version must be at least 1."
                    )
                    .throwIfInvalid();
    
            boolean renamed = teamDao.rename(
                    c,
                    existing.teamId(),
                    name,
                    req.version()
            );
    
            if (!renamed) {
                Team current = teamDao.findById(c, teamId)
                        .orElseThrow(ApiException::notFound);
    
                throw ApiException.versionConflict(current);
            }
    
            return teamDao.findById(c, teamId)
                    .orElseThrow(() -> new IllegalStateException(
                            "The renamed team could not be retrieved."
                    ));
        });
    }

    /** DELETE /api/teams/{teamId}. Team lead. Permanently deletes the team and dependent records through schema cascades. */
    public void deleteTeam(AuthContext ctx, long teamId) {
        db.inTransaction(c -> {
            Team team = teamDao.findById(c, teamId)
                    .orElseThrow(ApiException::notFound);
    
            permissions.require(
                    ctx,
                    Permission.DELETE_TEAM,
                    team.teamId()
            );
    
            teamDao.delete(c, team.teamId());
    
            return null;
        });
    }

    /** GET /api/teams/{teamId}/members. Team member. */
    public List<Membership> listMembers(AuthContext ctx, long teamId) {
        return db.withConnection(c -> {
            Team team = teamDao.findById(c, teamId)
                    .orElseThrow(ApiException::notFound);
    
            permissions.require(
                    ctx,
                    Permission.VIEW_WORKSPACE,
                    team.teamId()
            );
    
            return membershipDao.findByTeam(c, team.teamId());
        });
    }
}
