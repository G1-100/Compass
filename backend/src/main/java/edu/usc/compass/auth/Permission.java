package edu.usc.compass.auth;

import edu.usc.compass.teams.model.TeamRole;

/**
 * Every permission-checked action, from the Role Permissions table (Detailed Design 3.1.2).
 * minimumRole = the lowest team role allowed. System admins pass everything.
 * SHARED CONTRACT — add values here when you need a new check.
 */
public enum Permission {
    VIEW_WORKSPACE(TeamRole.MEMBER),
    COMMENT_ON_TASKS(TeamRole.MEMBER),
    SUGGEST_TASKS(TeamRole.MEMBER),
    UPDATE_OWN_TASK_STATUS(TeamRole.MEMBER),   // also requires being an assignee
    SUBMIT_CHECKIN_UPDATE(TeamRole.MEMBER),
    REPORT_AVAILABILITY(TeamRole.MEMBER),
    MANAGE_TASKS(TeamRole.LEAD),
    MANAGE_PROJECTS(TeamRole.LEAD),
    MANAGE_SPRINTS(TeamRole.LEAD),
    MANAGE_MILESTONES(TeamRole.LEAD),
    MANAGE_CHECKINS(TeamRole.LEAD),
    MANAGE_MEMBERS(TeamRole.LEAD),
    MANAGE_GUEST_LINKS(TeamRole.LEAD),
    VIEW_WORKLOAD(TeamRole.LEAD),
    SEND_CUSTOM_NOTIFICATION(TeamRole.LEAD),
    VIEW_AUDIT_LOG(TeamRole.LEAD),
    DELETE_TEAM(TeamRole.LEAD),
    /** System admin only. */
    MANAGE_USERS(null);

    private final TeamRole minimumRole;

    Permission(TeamRole minimumRole) {
        this.minimumRole = minimumRole;
    }

    /** null means system admin only. */
    public TeamRole minimumRole() {
        return minimumRole;
    }
}
