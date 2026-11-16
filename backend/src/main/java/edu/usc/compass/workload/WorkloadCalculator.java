package edu.usc.compass.workload;

import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.model.TaskModels.AssignmentRow;
import edu.usc.compass.workload.model.WorkloadModels.Availability;
import edu.usc.compass.workload.model.WorkloadModels.WorkloadResult;

import java.util.List;
import java.util.Map;

/**
 * Pure workload math, no database (Detailed Design 3.2.3). Owner: Liv.
 * Tests: White box 9, 13, 14; Stress 13.
 */
public class WorkloadCalculator {

    /**
     * 1. Ignore Done tasks. Points from EffortLevel.points().
     * 2. Split each task's points among assignees: equally, or by allocationPercent when set (totals 100).
     * 3. assignedShare = member points / total points; expectedShare = member hours / total hours.
     * 4. OVERLOAD if assignedShare - expectedShare > Rules.OVERLOAD_THRESHOLD (strictly greater) or the member
     *    reports overload; AVAILABLE_CAPACITY if expectedShare - assignedShare > threshold; else BALANCED.
     * 5. Total points or total hours of zero -> WorkloadResult(insufficientData = true, empty list).
     *
     * @param displayNames userId -> display name for the result rows
     */
    public WorkloadResult calculate(List<AssignmentRow> activeAssignments, List<Availability> availability,
                                    Map<Long, String> displayNames) {
        throw Todo.of("Liv", "WorkloadCalculator.calculate");
    }
}
