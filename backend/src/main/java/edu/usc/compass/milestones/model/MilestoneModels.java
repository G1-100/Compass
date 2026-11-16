package edu.usc.compass.milestones.model;

import edu.usc.compass.tasks.model.EffortLevel;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Milestone records (tables milestones, milestone_tasks). Owner: Siya. */
public final class MilestoneModels {

    private MilestoneModels() {
    }

    public enum DeadlineStatus {
        COMPLETE, OVERDUE, APPROACHING, ON_SCHEDULE
    }

    public record Milestone(long milestoneId, long projectId, String name, String description, LocalDate targetDate,
                            Instant createdAt, int version) {
    }

    /** @param progressPercent null when the milestone has no linked tasks ("Progress unavailable") */
    public record MilestoneProgress(Double progressPercent, DeadlineStatus deadlineStatus) {
    }

    /** Input to the calculator: just what it needs from each linked task. */
    public record LinkedTask(EffortLevel effort, TaskStatus status) {
    }

    /** API shape: milestone + computed progress + linked task ids. SHARED with dashboard and guest view. */
    public record MilestoneView(Milestone milestone, MilestoneProgress progress, List<Long> linkedTaskIds) {
    }
}
