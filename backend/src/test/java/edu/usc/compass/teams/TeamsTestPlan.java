package edu.usc.compass.teams;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Testing Plan cases for teams, memberships, invitations and projects. Owner: Solomon. */
@Disabled("TODO(Solomon): implement with the feature")
class TeamsTestPlan {

    @Test void wb6_lastLeadGuardBlocksDemoteAndLeave() {}
    @Test void unit1_promoteMemberToLead() {}
    @Test void unit2_roleChangeWithStaleVersionReturns409() {}
    @Test void unit3_removingOnlyLeadReturns409() {}
    @Test void unit4_memberLeavesTeam() {}
    @Test void unit5_invalidRemoveRequests() {}
    @Test void unit6_auditFailureRollsBackRemoval() {}
    @Test void unit7_teamNameLimits() {}
    @Test void unit8_deletingTeamRemovesOnlyItsRecords() {}
    @Test void unit9_invitationExpiryBoundary() {}
    @Test void unit10_acceptInvitationAsLead() {}
    @Test void unit11_closedInvitationsCannotBeAccepted() {}
    @Test void unit12_invalidInvitationsRejected() {}
    @Test void reg2_renamingProjectKeepsRelatedRecords() {}
    @Test void reg14_dashboardIsProjectSpecific() {}
}
