package edu.usc.compass.projects.model;

import edu.usc.compass.milestones.model.MilestoneModels.MilestoneView;
import edu.usc.compass.tasks.model.TaskModels.Task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Project and dashboard records (table projects). Owner: Solomon. */
public final class ProjectModels {

    private ProjectModels() {
    }

    public enum ProjectStatus {
        PLANNING, ACTIVE, ON_HOLD, COMPLETED
    }

    public record Project(long projectId, long teamId, String name, String description, LocalDate targetDate,
                          ProjectStatus status, Instant archivedAt, long createdBy, Instant createdAt, int version) {
    }

    /** One row of the dashboard's "team assignments" panel. */
    public record MemberAssignment(long userId, String displayName, int activeTaskCount, int activePoints) {
    }

    /**
     * GET /api/projects/{projectId}/dashboard.
     *
     * @param progressPercent effort-weighted share of Done tasks, or null if the project has no tasks
     */
    public record DashboardView(Project project, Double progressPercent, List<MemberAssignment> teamAssignments,
                                List<Task> upcomingDeadlines, List<MilestoneView> milestones, List<Task> myTasks) {
    }
}
