package edu.usc.compass.auth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Placeholders for the Testing Plan cases that cover this feature. Owner: Gavin.
 * Remove @Disabled and write each test as you implement the feature (mock DAOs with Mockito, or use a test DB).
 */
@Disabled("TODO(Gavin): implement with the feature")
class AuthTestPlan {

    @Test void wb1_lockoutOnFifthFailure_sixthReturns429WithoutPasswordCheck() {}
    @Test void wb2_lockoutWindowResetsAfter30Minutes() {}
    @Test void wb3_sessionExpiresAfter30MinutesInactivity() {}
    @Test void wb4_verificationCodeInvalidatedAfterFiveWrongAttempts() {}
    @Test void wb5_authorizeTeamRequestRoleBranches() {}
    @Test void reg1_profileChangeDoesNotAffectAuthOrMemberships() {}
}
