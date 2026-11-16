package edu.usc.compass.teams.model;

/** team_memberships.role. LEAD outranks MEMBER. SHARED CONTRACT. */
public enum TeamRole {
    MEMBER, LEAD;

    public boolean atLeast(TeamRole other) {
        return this.ordinal() >= other.ordinal();
    }
}
