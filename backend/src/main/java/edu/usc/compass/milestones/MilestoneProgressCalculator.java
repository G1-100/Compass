package edu.usc.compass.milestones;

import edu.usc.compass.common.Todo;
import edu.usc.compass.milestones.model.MilestoneModels.LinkedTask;
import edu.usc.compass.milestones.model.MilestoneModels.MilestoneProgress;

import java.time.LocalDate;
import java.util.List;

/**
 * Pure calculation, no database — easy to unit test (Detailed Design 3.3.1 CalculateMilestoneProgress).
 * Owner: Siya. Tests: White box 18, Black box 11 & 12, Stress 15.
 */
public class MilestoneProgressCalculator {

    /**
     * progress = sum(points of Done tasks) / sum(points of all linked tasks) * 100, using EffortLevel.points().
     * No linked tasks -> progress null (UNKNOWN) but still compute deadline status.
     * Deadline status: all Done -> COMPLETE; targetDate before today -> OVERDUE;
     * targetDate within Rules.LATE_RISK_WINDOW_DAYS -> APPROACHING; else ON_SCHEDULE.
     * Example: Done MINOR (1) + unfinished EXTREME (8) -> 11.11%.
     */
    public MilestoneProgress calculate(LocalDate targetDate, List<LinkedTask> linkedTasks, LocalDate today) {
        throw Todo.of("Siya", "MilestoneProgressCalculator.calculate");
    }
}
