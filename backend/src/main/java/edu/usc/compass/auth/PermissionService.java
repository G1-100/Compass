package edu.usc.compass.auth;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;

import java.util.Collection;

/**
 * Server-side authorization (Detailed Design 3.1.1.2 AuthorizeTeamRequest, 3.1.2).
 * Owner: Gavin. SHARED CONTRACT — every feature calls these methods.
 *
 * STUB BEHAVIOR: until implemented, every check passes (logs a TODO), so other features can be built
 * and tested now. Write your services as if these checks are real.
 */
public class PermissionService {

    /**
     * Throws ApiException.forbidden() unless the user may perform {@code permission} in {@code teamId}.
     * Rules: system admins always pass; otherwise the user must have a membership in the team whose role
     * is at least permission.minimumRole() (LEAD > MEMBER); MANAGE_USERS requires SYSTEM_ADMIN.
     */
    public void require(AuthContext ctx, Permission permission, long teamId) {
        // TODO(Gavin): implement using ctx.isSystemAdmin() and ctx.roleIn(teamId)
        Todo.noop("Gavin", "PermissionService.require");
    }

    /** Same rules as {@link #require} but returns true/false instead of throwing (e.g. to hide UI data). */
    public boolean has(AuthContext ctx, Permission permission, long teamId) {
        Todo.noop("Gavin", "PermissionService.has");
        return true;
    }

    /**
     * Task status changes: the user must be one of {@code assigneeIds}, OR a lead of the team, OR a
     * system admin. Otherwise 403.
     */
    public void requireAssigneeOrLead(AuthContext ctx, long teamId, Collection<Long> assigneeIds) {
        Todo.noop("Gavin", "PermissionService.requireAssigneeOrLead");
    }

    /** 403 unless ctx.isSystemAdmin(). */
    public void requireSystemAdmin(AuthContext ctx) {
        Todo.noop("Gavin", "PermissionService.requireSystemAdmin");
    }
}
