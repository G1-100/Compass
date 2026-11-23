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

    public record BlockerInput(Long blockerId, String description, Severity severity, Long taskId) {
    }

    /** Version 0 creates the member's update. An edit uses the saved version and IDs of retained blockers. */
    public record SubmitUpdateRequest(Integer version, String completedWork, String upcomingTasks, List<BlockerInput> blockers) {
    }

    public enum When {
        upcoming, past
    }
}
