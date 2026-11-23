package edu.usc.compass.checkins.model;

import edu.usc.compass.common.Severity;

import java.time.Instant;
import java.util.List;

/** Check-in records (tables checkins, checkin_updates, checkin_blockers, checkin_reminders). Owner: Graham. */
public final class CheckinModels {

    private CheckinModels() {
    }

    public enum CheckinStatus {
        SCHEDULED, COMPLETED, CANCELED
    }

    public record Checkin(long checkinId, long teamId, String description, Instant scheduledAt, String location,
                          CheckinStatus status, long createdBy, Instant createdAt, Instant completedAt, int version) {

        /** True only while SCHEDULED; completed or canceled check-ins lock their updates. Tests: Unit 15. */
        public boolean acceptsUpdates() {
            return status == CheckinStatus.SCHEDULED;
        }
    }

    public record Blocker(long blockerId, long updateId, String description, Severity severity, Long taskId,
                          Instant resolvedAt, Long resolvedBy) {

        /** True once resolvedAt is set. Tests: Unit 20. */
        public boolean isResolved() {
            return resolvedAt != null;
        }
    }

    public record CheckinUpdate(long updateId, long checkinId, long userId, String displayName, String completedWork,
                                String upcomingTasks, Instant submittedAt, int version, List<Blocker> blockers) {
    }
}
