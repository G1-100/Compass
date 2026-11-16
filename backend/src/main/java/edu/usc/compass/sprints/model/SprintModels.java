package edu.usc.compass.sprints.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Sprint records (table sprints). Owner: Siya. */
public final class SprintModels {

    private SprintModels() {
    }

    public enum SprintStatus {
        PLANNED, ACTIVE, COMPLETED
    }

    public record Sprint(long sprintId, long projectId, String name, LocalDate startDate, LocalDate endDate,
                         SprintStatus status, Instant completedAt, int version) {
    }

    /** What CompleteSprint did. targetSprintId null = moved to backlog. */
    public record SprintCompletionResult(Sprint sprint, Long targetSprintId, List<Long> completedTaskIds, List<Long> movedTaskIds) {
    }
}
