package edu.usc.compass.workload;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Rules;
import edu.usc.compass.common.Severity;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.workload.WorkloadDtos.ReportAvailabilityRequest;
import edu.usc.compass.workload.dao.WorkloadDao;
import edu.usc.compass.workload.model.WorkloadModels.AlertType;
import edu.usc.compass.workload.model.WorkloadModels.Availability;
import edu.usc.compass.workload.model.WorkloadModels.MemberWorkload;
import edu.usc.compass.workload.model.WorkloadModels.WorkloadResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Availability reporting and the lead's workload view (Detailed Design 3.2.3). Owner: Liv. */
public class WorkloadService {

    private static final BigDecimal MAX_HOURS_PER_WEEK = new BigDecimal("80");

    /** An overload gap at least this big (twice the threshold) is HIGH severity, otherwise MEDIUM. */
    private static final double HIGH_SEVERITY_GAP = 2 * Rules.OVERLOAD_THRESHOLD;

    private final Database db;
    private final WorkloadDao workloadDao;
    private final TaskDao taskDao;
    private final WorkloadCalculator calculator;
    private final AlertService alerts;
    private final ProjectService projects;
    private final ProjectDao projectDao;
    private final MembershipDao membershipDao;

    public WorkloadService(Database db, WorkloadDao workloadDao, TaskDao taskDao, WorkloadCalculator calculator,
                           AlertService alerts, ProjectService projects, ProjectDao projectDao,
                           MembershipDao membershipDao) {
        this.db = db;
        this.workloadDao = workloadDao;
        this.taskDao = taskDao;
        this.calculator = calculator;
        this.alerts = alerts;
        this.projects = projects;
        this.projectDao = projectDao;
        this.membershipDao = membershipDao;
    }

    /**
     * PUT /api/projects/{projectId}/availability/me. Team member. hoursPerWeek 0-80.
     * Also re-checks workload alerts, because the hours and the "I'm overloaded" flag feed the calculation.
     */
    public Availability reportAvailability(AuthContext ctx, long projectId, ReportAvailabilityRequest req) {
        return db.inTransaction(c -> {
            long teamId = projects.requirePermission(c, ctx, projectId, Permission.REPORT_AVAILABILITY);
            BigDecimal hours = validatedHours(req);

            workloadDao.upsertAvailability(c, projectId, ctx.userId(), hours, req.reportsOverload());
            recalculate(c, projectId, teamId);

            return workloadDao.findAvailability(c, projectId, ctx.userId())
                    .orElseThrow(() -> new IllegalStateException("Availability row missing after upsert"));
        });
    }

    /** GET /api/projects/{projectId}/workload. Team lead. Runs the calculator and raises/resolves alerts. */
    public WorkloadResult getWorkload(AuthContext ctx, long projectId) {
        // Not read-only: raising and resolving alerts writes, so this runs in a transaction.
        return db.inTransaction(c -> {
            long teamId = projects.requirePermission(c, ctx, projectId, Permission.VIEW_WORKLOAD);
            return recalculate(c, projectId, teamId);
        });
    }

    /**
     * SHARED CONTRACT (Adam calls this after task assignments or effort labels change). Recalculates and
     * raises/resolves OVERLOAD and AVAILABLE_CAPACITY alerts inside the caller's transaction.
     */
    public void onAssignmentsChanged(Connection c, long projectId) {
        try {
            long teamId = projectDao.findTeamId(c, projectId);
            recalculate(c, projectId, teamId);
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    // ---- internals -------------------------------------------------------

    private WorkloadResult recalculate(Connection c, long projectId, long teamId) throws SQLException {
        List<Availability> availability = workloadDao.findAvailability(c, projectId);

        Map<Long, String> displayNames = new HashMap<>();
        for (Membership member : membershipDao.findByTeam(c, teamId)) {
            displayNames.put(member.userId(), member.displayName());
        }

        WorkloadResult result = calculator.calculate(taskDao.findActiveAssignments(c, projectId), availability,
                displayNames);

        // Not enough data to judge anyone: leave existing alerts exactly as they are.
        if (result.insufficientData()) {
            return result;
        }

        Map<Long, Availability> availabilityByUser = new HashMap<>();
        for (Availability a : availability) {
            availabilityByUser.put(a.userId(), a);
        }
        for (MemberWorkload member : result.members()) {
            syncAlerts(c, projectId, member, availabilityByUser.get(member.userId()));
        }
        return result;
    }

    /** Raise the alert that matches the member's flag and resolve the other one. */
    private void syncAlerts(Connection c, long projectId, MemberWorkload member, Availability availability)
            throws SQLException {
        long userId = member.userId();
        switch (member.flag()) {
            case OVERLOAD -> {
                boolean selfReported = availability != null && availability.reportsOverload();
                double gap = member.assignedShare() - member.expectedShare();
                Severity severity = (selfReported || gap >= HIGH_SEVERITY_GAP) ? Severity.HIGH : Severity.MEDIUM;
                alerts.raise(c, projectId, userId, null, AlertType.OVERLOAD, severity,
                        overloadReason(member, selfReported));
                alerts.resolve(c, projectId, userId, null, AlertType.AVAILABLE_CAPACITY);
            }
            case AVAILABLE_CAPACITY -> {
                alerts.raise(c, projectId, userId, null, AlertType.AVAILABLE_CAPACITY, Severity.LOW,
                        name(member) + " has " + percent(member.expectedShare()) + " of the team's available hours but only "
                                + percent(member.assignedShare()) + " of the open effort, so they have room for more work.");
                alerts.resolve(c, projectId, userId, null, AlertType.OVERLOAD);
            }
            case BALANCED -> {
                alerts.resolve(c, projectId, userId, null, AlertType.OVERLOAD);
                alerts.resolve(c, projectId, userId, null, AlertType.AVAILABLE_CAPACITY);
            }
        }
    }

    private static String overloadReason(MemberWorkload member, boolean selfReported) {
        String shares = name(member) + " has " + percent(member.assignedShare()) + " of the open effort but only "
                + percent(member.expectedShare()) + " of the team's available hours.";
        if (selfReported) {
            return name(member) + " reported being overloaded. " + shares;
        }
        return shares;
    }

    private static BigDecimal validatedHours(ReportAvailabilityRequest req) {
        if (req == null) {
            throw ApiException.badRequest("A request body is required.");
        }
        BigDecimal hours = req.hoursPerWeek();
        if (hours == null) {
            throw ApiException.validation("hoursPerWeek", "Hours per week is required.");
        }
        if (hours.signum() < 0 || hours.compareTo(MAX_HOURS_PER_WEEK) > 0) {
            throw ApiException.validation("hoursPerWeek", "Hours per week must be between 0 and 80.");
        }
        // member_availability stores one decimal place (NUMERIC(4,1)).
        return hours.setScale(1, RoundingMode.HALF_UP);
    }

    private static String name(MemberWorkload member) {
        return member.displayName() == null ? "A team member" : member.displayName();
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.0f%%", share * 100);
    }
}
