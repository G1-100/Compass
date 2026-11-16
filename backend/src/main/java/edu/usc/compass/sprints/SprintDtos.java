package edu.usc.compass.sprints;

import java.time.LocalDate;

/** Request bodies for sprint routes (Detailed Design 3.5.6). Owner: Siya. */
public final class SprintDtos {

    private SprintDtos() {
    }

    public record CreateSprintRequest(String name, LocalDate startDate, LocalDate endDate) {
    }

    public record UpdateSprintRequest(String name, LocalDate startDate, LocalDate endDate, int version) {
    }

    /** targetSprintId null = move unfinished tasks to the backlog. */
    public record CompleteSprintRequest(Long targetSprintId, int version) {
    }
}
