package edu.usc.compass.auth;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.teams.model.TeamRole;

import java.util.Collection;
import java.util.Optional;

/**
 * Server-side authorization (Detailed Design 3.1.1.2 AuthorizeTeamRequest, 3.1.2).
 * Owner: Gavin. SHARED CONTRACT — every feature calls these methods.
 *
 * Rules: system admins always pass. Otherwise the user needs a membership in the team whose role is at least
 * permission.minimumRole() (LEAD > MEMBER). MANAGE_USERS (minimumRole null) is system admin only.
 * Team roles come from AuthContext.teamRoles, loaded once per request by AuthFilter.
 */
public class PermissionService {

    /** Throws ApiException.forbidden() unless the user may perform {@code permission} in {@code teamId}. */
    public void require(AuthContext ctx, Permission permission, long teamId) {
        if (!has(ctx, permission, teamId)) {
            throw ApiException.forbidden();
        }
    }

    /** Same rules as {@link #require} but returns true/false instead of throwing (e.g. to hide UI data). */
    public boolean has(AuthContext ctx, Permission permission, long teamId) {
        if (ctx == null || permission == null) {
            return false;
        }
        if (ctx.isSystemAdmin()) {
            return true;
        }
        TeamRole minimum = permission.minimumRole();
        if (minimum == null) {
            return false;
        }
        Optional<TeamRole> role = ctx.roleIn(teamId);
        return role.isPresent() && role.get().atLeast(minimum);
    }

    /**
     * Task status changes: the user must be a member of the team AND one of {@code assigneeIds},
     * OR a lead of the team, OR a system admin. Otherwise 403.
     */
    public void requireAssigneeOrLead(AuthContext ctx, long teamId, Collection<Long> assigneeIds) {
        if (ctx == null) {
            throw ApiException.forbidden();
        }
        if (ctx.isSystemAdmin()) {
            return;
        }
        Optional<TeamRole> role = ctx.roleIn(teamId);
        if (role.isEmpty()) {
            throw ApiException.forbidden();
        }
        if (role.get().atLeast(TeamRole.LEAD)) {
            return;
        }
        if (assigneeIds != null && assigneeIds.contains(ctx.userId())) {
            return;
        }
        throw ApiException.forbidden();
    }

    /** 403 unless ctx.isSystemAdmin(). */
    public void requireSystemAdmin(AuthContext ctx) {
        if (ctx == null || !ctx.isSystemAdmin()) {
            throw ApiException.forbidden();
        }
    }
}
