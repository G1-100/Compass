package edu.usc.compass.workload;

import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.Rules;
import edu.usc.compass.common.Severity;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.tasks.dao.CommentDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.SuggestionStatus;
import edu.usc.compass.tasks.model.TaskModels.Task;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DetectWorkloadDrift (Detailed Design 3.2.4). Owner: Liv. Tests: White box 15 & 16, Stress 14.
 */
public class DriftDetector {

    private final TaskDao taskDao;
    private final CommentDao commentDao;
    private final CheckinDao checkinDao;
    private final MembershipDao membershipDao;
    private final AlertService alerts;
    private final ProjectDao projectDao;

    public DriftDetector(TaskDao taskDao, CommentDao commentDao, CheckinDao checkinDao, MembershipDao membershipDao,
                         AlertService alerts, ProjectDao projectDao) {
        this.taskDao = taskDao;
        this.commentDao = commentDao;
        this.checkinDao = checkinDao;
        this.membershipDao = membershipDao;
        this.alerts = alerts;
        this.projectDao = projectDao;
    }

    /**
     * For each member of the project's team: if they own an incomplete task due within DRIFT_DUE_WINDOW_DAYS
     * and their last activity (latest of task status change, task comment, check-in update — or their join
     * date if none) is DRIFT_INACTIVITY_DAYS or more ago, create a WORKLOAD_DRIFT alert (only if none is open)
     * and notify the member and leads. Otherwise resolve any open drift alert for them.
     *
     * Overdue incomplete tasks count as "due within the window". Day counts use UTC calendar dates.
     */
    public void detectWorkloadDrift(Connection c, long projectId, LocalDate today) throws SQLException {
        long teamId = projectDao.findTeamId(c, projectId);
        LocalDate dueLimit = today.plusDays(Rules.DRIFT_DUE_WINDOW_DAYS);

        for (Membership member : membershipDao.findByTeam(c, teamId)) {
            long userId = member.userId();

            List<Task> dueTasks = incompleteDueTasks(c, projectId, userId, dueLimit);
            if (dueTasks.isEmpty()) {
                alerts.resolve(c, projectId, userId, null, AlertType.WORKLOAD_DRIFT);
                continue;
            }

            Instant lastActivity = lastActivity(c, projectId, teamId, member);
            long daysInactive = ChronoUnit.DAYS.between(lastActivity.atZone(ZoneOffset.UTC).toLocalDate(), today);

            if (daysInactive >= Rules.DRIFT_INACTIVITY_DAYS) {
                boolean anyOverdue = dueTasks.stream().anyMatch(t -> t.dueDate().isBefore(today));
                Severity severity = anyOverdue ? Severity.HIGH : Severity.MEDIUM;
                String name = member.displayName() == null ? "A team member" : member.displayName();
                String reason = name + " has " + dueTasks.size() + " incomplete task"
                        + (dueTasks.size() == 1 ? "" : "s") + " due within " + Rules.DRIFT_DUE_WINDOW_DAYS
                        + " days and no activity for " + daysInactive + " days.";
                alerts.raise(c, projectId, userId, null, AlertType.WORKLOAD_DRIFT, severity, reason);
            } else {
                alerts.resolve(c, projectId, userId, null, AlertType.WORKLOAD_DRIFT);
            }
        }
    }

    private List<Task> incompleteDueTasks(Connection c, long projectId, long userId, LocalDate dueLimit)
            throws SQLException {
        List<Task> result = new ArrayList<>();
        for (Task task : taskDao.findIncompleteAssigned(c, projectId, userId, dueLimit)) {
            SuggestionStatus suggestion = task.suggestionStatus();
            boolean unapproved = suggestion == SuggestionStatus.PENDING || suggestion == SuggestionStatus.REJECTED;
            if (!unapproved && task.dueDate() != null) {
                result.add(task);
            }
        }
        return result;
    }

    /** Latest of status change, comment and check-in update; the join date only if there is no activity at all. */
    private Instant lastActivity(Connection c, long projectId, long teamId, Membership member) throws SQLException {
        Instant latest = null;
        latest = later(latest, taskDao.findLastStatusChangeBy(c, projectId, member.userId()));
        latest = later(latest, commentDao.findLastCommentBy(c, projectId, member.userId()));
        latest = later(latest, checkinDao.findLastUpdateTime(c, teamId, member.userId()));
        return latest != null ? latest : member.joinedAt();
    }

    private static Instant later(Instant current, Optional<Instant> candidate) {
        if (candidate.isPresent() && (current == null || candidate.get().isAfter(current))) {
            return candidate.get();
        }
        return current;
    }
}
