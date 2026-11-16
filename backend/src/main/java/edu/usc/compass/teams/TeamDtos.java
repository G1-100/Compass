package edu.usc.compass.teams;

import edu.usc.compass.teams.model.TeamRole;

/** Request bodies for /api/teams routes (Detailed Design 3.5.2). Owner: Solomon. */
public final class TeamDtos {

    private TeamDtos() {
    }

    public record CreateTeamRequest(String name) {
    }

    public record RenameTeamRequest(String name, int version) {
    }

    public record ChangeRoleRequest(TeamRole role, int version) {
    }

    public record InviteRequest(String email, TeamRole role) {
    }
}
