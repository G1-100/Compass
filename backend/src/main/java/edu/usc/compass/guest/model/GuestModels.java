package edu.usc.compass.guest.model;

import edu.usc.compass.milestones.model.MilestoneModels.DeadlineStatus;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;

import java.time.Instant;
import java.util.List;

/** Guest link records (table guest_links) and the read-only guest view. Owner: Ramanjot. */
public final class GuestModels {

    private GuestModels() {
    }

    public enum GuestLinkStatus {
        ACTIVE, EXPIRED, REVOKED
    }

    /** Never contains the raw token. */
    public record GuestLink(long linkId, long projectId, long createdBy, Instant createdAt, Instant expiresAt,
                            Instant revokedAt, GuestLinkStatus status) {
    }

    /** Returned once, at creation. shareUrl = PUBLIC_SITE_URL + "/guest/" + rawToken. */
    public record CreatedGuestLink(GuestLink link, String shareUrl) {
    }

    public record GuestMilestone(String name, Double progressPercent, DeadlineStatus deadlineStatus) {
    }

    public record GuestTask(String title, TaskStatus status) {
    }

    /**
     * GET /api/guest/{token}. ONLY approved fields: never emails, check-in responses, admin data or other projects.
     */
    public record GuestProjectView(String projectName, String summary, Double overallProgressPercent,
                                   List<GuestMilestone> milestones, List<GuestTask> tasks, List<String> memberDisplayNames) {
    }
}
