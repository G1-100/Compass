package edu.usc.compass.tasks;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Testing Plan cases for tasks, suggestions and comments. Owner: Adam. */
@Disabled("TODO(Adam): implement with the feature")
class TasksTestPlan {

    @Test void wb7_staleTaskWriteReturns409() {}
    @Test void reg8_cancelingEditLeavesTaskUnchanged() {}
    @Test void reg9_rejectingSuggestionCreatesNoTask() {}
    @Test void reg10_approvingSuggestionCreatesExactlyOneTask() {}
    @Test void reg12_invalidInputCreatesNoPartialTask() {}
    @Test void mentionsNotifyEachMentionedMemberOnce() {}
}
