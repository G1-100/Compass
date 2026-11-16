package edu.usc.compass.auth.model;

import edu.usc.compass.teams.model.TeamRole;

import java.util.Map;
import java.util.Optional;

/**
 * Who is making the request. Built once per request by AuthFilter and read by every service.
 * SHARED CONTRACT — other features depend on these fields; discuss before changing.
 *
 * @param teamRoles team_id -> role for every team the user belongs to
 */
public record AuthContext(
        long userId,
        String email,
        String displayName,
        SystemRole systemRole,
        Map<Long, TeamRole> teamRoles) {

    public boolean isSystemAdmin() {
        return systemRole == SystemRole.SYSTEM_ADMIN;
    }

    public Optional<TeamRole> roleIn(long teamId) {
        return Optional.ofNullable(teamRoles.get(teamId));
    }
}
