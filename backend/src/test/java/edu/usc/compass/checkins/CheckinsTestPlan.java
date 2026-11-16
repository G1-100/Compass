package edu.usc.compass.checkins;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Testing Plan cases for check-ins. Owner: Graham. */
@Disabled("TODO(Graham): implement with the feature")
class CheckinsTestPlan {

    @Test void unit13_checkinTimeMustBeInFuture() {}
    @Test void unit14_descriptionAndLocationRules() {}
    @Test void unit15_completedAndCanceledCheckinsAreLocked() {}
    @Test void unit16_rescheduleClearsRemindersForThatCheckinOnly() {}
    @Test void unit17_replacingProgressUpdate() {}
    @Test void unit18_blockerInsertFailureRollsBackUpdate() {}
    @Test void unit19_updateAndBlockerLimits() {}
    @Test void unit20_resolvingBlockerPermissions() {}
    @Test void reg5_submittingCheckinDoesNotModifyTasks() {}
    @Test void reg11_reschedulingKeepsPreviousResponses() {}
}
