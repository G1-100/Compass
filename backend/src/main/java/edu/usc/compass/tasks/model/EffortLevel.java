package edu.usc.compass.tasks.model;

/**
 * Task effort labels and their point values (ConvertEffortToPoints, Detailed Design 3.2.3).
 * SHARED CONTRACT — milestones, workload, late risk and the dashboard all use points().
 * The values are fixed by the design: Minor = 1, Moderate = 3, High = 5, Extreme = 8.
 */
public enum EffortLevel {
    MINOR(1), MODERATE(3), HIGH(5), EXTREME(8);

    private final int points;

    EffortLevel(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }
}
