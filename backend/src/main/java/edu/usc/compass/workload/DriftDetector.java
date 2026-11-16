package edu.usc.compass.workload;

import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.dao.CommentDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.teams.dao.MembershipDao;

import java.sql.Connection;
import java.time.LocalDate;

/**
 * DetectWorkloadDrift (Detailed Design 3.2.4). Owner: Liv. Tests: White box 15 & 16, Stress 14.
 */
public class DriftDetector {

    private final TaskDao taskDao;
    private final CommentDao commentDao;
    private final CheckinDao checkinDao;
    private final MembershipDao membershipDao;
    private final AlertService alerts;

    public DriftDetector(TaskDao taskDao, CommentDao commentDao, CheckinDao checkinDao, MembershipDao membershipDao,
                         AlertService alerts) {
        this.taskDao = taskDao;
        this.commentDao = commentDao;
        this.checkinDao = checkinDao;
        this.membershipDao = membershipDao;
        this.alerts = alerts;
    }

    /**
     * For each member of the project's team: if they own an incomplete task due within DRIFT_DUE_WINDOW_DAYS
     * and their last activity (latest of task status change, task comment, check-in update — or their join
     * date if none) is DRIFT_INACTIVITY_DAYS or more ago, create a WORKLOAD_DRIFT alert (only if none is open)
     * and notify the member and leads. Otherwise resolve any open drift alert for them.
     */
    public void detectWorkloadDrift(Connection c, long projectId, LocalDate today) {
        throw Todo.of("Liv", "DriftDetector.detectWorkloadDrift");
    }
}
