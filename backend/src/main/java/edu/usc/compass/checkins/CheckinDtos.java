package edu.usc.compass.checkins;

import edu.usc.compass.common.Severity;

import java.time.Instant;
import java.util.List;

/** Request bodies for check-in routes (Detailed Design 3.5.8). Owner: Graham. */
public final class CheckinDtos {

    private CheckinDtos() {
    }

    public record ScheduleCheckinRequest(long teamId, Instant scheduledAt, String description, String location) {
    }

    public record UpdateCheckinRequest(Instant scheduledAt, String description, String location, int version) {
    }

    public record BlockerInput(String description, Severity severity, Long taskId) {
    }

    /** PUT .../updates/me creates or replaces the member's update (and replaces all its blockers). */
    public record SubmitUpdateRequest(String completedWork, String upcomingTasks, List<BlockerInput> blockers) {
    }

    public enum When {
        upcoming, past
    }
}
