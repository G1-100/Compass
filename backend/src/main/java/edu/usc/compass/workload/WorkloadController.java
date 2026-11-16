package edu.usc.compass.workload;

import edu.usc.compass.common.web.Router;
import edu.usc.compass.workload.WorkloadDtos.ReportAvailabilityRequest;
import edu.usc.compass.workload.WorkloadDtos.UpdateAlertRequest;
import edu.usc.compass.workload.model.WorkloadModels.AlertStatus;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Workload (Detailed Design 3.5.4). Owner: Liv. */
public final class WorkloadController {

    private final WorkloadService workload;
    private final AlertService alerts;

    public WorkloadController(WorkloadService workload, AlertService alerts) {
        this.workload = workload;
        this.alerts = alerts;
    }

    public void register(Router r) {
        r.put("/api/projects/{projectId}/availability/me", SIGNED_IN,
                ctx -> workload.reportAvailability(ctx.auth(), ctx.pathLong("projectId"), ctx.body(ReportAvailabilityRequest.class)));
        r.get("/api/projects/{projectId}/workload", SIGNED_IN, ctx -> workload.getWorkload(ctx.auth(), ctx.pathLong("projectId")));
        r.get("/api/projects/{projectId}/alerts", SIGNED_IN, ctx -> alerts.list(ctx.auth(), ctx.pathLong("projectId"),
                ctx.queryEnum("status", AlertStatus.class).orElse(null)));
        r.patch("/api/projects/{projectId}/alerts/{alertId}", SIGNED_IN, ctx -> alerts.update(ctx.auth(),
                ctx.pathLong("projectId"), ctx.pathLong("alertId"), ctx.body(UpdateAlertRequest.class)));
    }
}
