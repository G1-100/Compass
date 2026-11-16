package edu.usc.compass.workload;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Testing Plan cases for workload and risk detection. Owner: Liv. */
@Disabled("TODO(Liv): implement with the feature")
class WorkloadTestPlan {

    @Test void wb9_edgeCases_zeroTotals_exactly20Points_equalSplit() {}
    @Test void wb13_missingDataReturnsInsufficientData() {}
    @Test void wb14_splittingWorkBetweenMembers() {}
    @Test void wb15_driftUsesJoinDateWhenNoActivity() {}
    @Test void wb16_noDuplicateDriftAlerts_resolvesAfterActivity() {}
}
