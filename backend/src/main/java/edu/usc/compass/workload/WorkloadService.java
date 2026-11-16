package edu.usc.compass.workload;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.workload.WorkloadDtos.ReportAvailabilityRequest;
import edu.usc.compass.workload.dao.WorkloadDao;
import edu.usc.compass.workload.model.WorkloadModels.Availability;
import edu.usc.compass.workload.model.WorkloadModels.WorkloadResult;

import java.sql.Connection;

/** Availability reporting and the lead's workload view (Detailed Design 3.2.3). Owner: Liv. */
public class WorkloadService {

    private final Database db;
    private final WorkloadDao workloadDao;
    private final TaskDao taskDao;
    private final WorkloadCalculator calculator;
    private final AlertService alerts;
    private final ProjectService projects;

    public WorkloadService(Database db, WorkloadDao workloadDao, TaskDao taskDao, WorkloadCalculator calculator,
                           AlertService alerts, ProjectService projects) {
        this.db = db;
        this.workloadDao = workloadDao;
        this.taskDao = taskDao;
        this.calculator = calculator;
        this.alerts = alerts;
        this.projects = projects;
    }

    /** PUT /api/projects/{projectId}/availability/me. Team member. hoursPerWeek 0-80. */
    public Availability reportAvailability(AuthContext ctx, long projectId, ReportAvailabilityRequest req) {
        throw Todo.of("Liv", "WorkloadService.reportAvailability");
    }

    /** GET /api/projects/{projectId}/workload. Team lead. Runs the calculator and raises/resolves alerts. */
    public WorkloadResult getWorkload(AuthContext ctx, long projectId) {
        throw Todo.of("Liv", "WorkloadService.getWorkload");
    }

    /**
     * SHARED CONTRACT (Adam calls this after task assignments or effort labels change). Recalculates and
     * raises/resolves OVERLOAD and AVAILABLE_CAPACITY alerts inside the caller's transaction.
     * Stub: no-op until implemented.
     */
    public void onAssignmentsChanged(Connection c, long projectId) {
        Todo.noop("Liv", "WorkloadService.onAssignmentsChanged");
    }
}
