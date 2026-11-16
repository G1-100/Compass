package edu.usc.compass.milestones;

import java.time.LocalDate;
import java.util.List;

/** Request bodies for milestone routes (Detailed Design 3.5.7). Owner: Siya. */
public final class MilestoneDtos {

    private MilestoneDtos() {
    }

    public record CreateMilestoneRequest(String name, String description, LocalDate targetDate, List<Long> taskIds) {
    }

    public record UpdateMilestoneRequest(String name, String description, LocalDate targetDate, int version) {
    }

    public record ReplaceMilestoneTasksRequest(List<Long> taskIds, int version) {
    }
}
