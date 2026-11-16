package edu.usc.compass.guest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Testing Plan cases for guest links. Owner: Ramanjot. */
@Disabled("TODO(Ramanjot): implement with the feature")
class GuestTestPlan {

    @Test void bb6_revokedLinkStopsWorking() {}
    @Test void bb7_guestSeesOnlyApprovedFields() {}
    @Test void unknownExpiredRevokedTokensReturn404WithNoData() {}
}
