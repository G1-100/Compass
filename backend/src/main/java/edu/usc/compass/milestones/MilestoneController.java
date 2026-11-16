package edu.usc.compass.milestones;

import edu.usc.compass.common.web.Router;
import edu.usc.compass.milestones.MilestoneDtos.CreateMilestoneRequest;
import edu.usc.compass.milestones.MilestoneDtos.ReplaceMilestoneTasksRequest;
import edu.usc.compass.milestones.MilestoneDtos.UpdateMilestoneRequest;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Milestones (Detailed Design 3.5.7). Owner: Siya. */
public final class MilestoneController {

    private final MilestoneService milestones;

    public MilestoneController(MilestoneService milestones) {
        this.milestones = milestones;
    }

    public void register(Router r) {
        r.get("/api/projects/{projectId}/milestones", SIGNED_IN, ctx -> milestones.list(ctx.auth(), ctx.pathLong("projectId")));
        r.post("/api/projects/{projectId}/milestones", SIGNED_IN, ctx -> {
            var m = milestones.create(ctx.auth(), ctx.pathLong("projectId"), ctx.body(CreateMilestoneRequest.class));
            return ctx.created("/api/milestones/" + m.milestone().milestoneId(), m);
        });
        r.get("/api/milestones/{milestoneId}", SIGNED_IN, ctx -> milestones.get(ctx.auth(), ctx.pathLong("milestoneId")));
        r.patch("/api/milestones/{milestoneId}", SIGNED_IN,
                ctx -> milestones.update(ctx.auth(), ctx.pathLong("milestoneId"), ctx.body(UpdateMilestoneRequest.class)));
        r.put("/api/milestones/{milestoneId}/tasks", SIGNED_IN,
                ctx -> milestones.replaceTasks(ctx.auth(), ctx.pathLong("milestoneId"), ctx.body(ReplaceMilestoneTasksRequest.class)));
        r.delete("/api/milestones/{milestoneId}", SIGNED_IN, ctx -> {
            milestones.delete(ctx.auth(), ctx.pathLong("milestoneId"));
            return null;
        });
    }
}
