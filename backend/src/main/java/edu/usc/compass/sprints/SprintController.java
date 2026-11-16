package edu.usc.compass.sprints;

import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.web.Router;
import edu.usc.compass.sprints.SprintDtos.CompleteSprintRequest;
import edu.usc.compass.sprints.SprintDtos.CreateSprintRequest;
import edu.usc.compass.sprints.SprintDtos.UpdateSprintRequest;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Sprints (Detailed Design 3.5.6). Owner: Siya. */
public final class SprintController {

    private final SprintService sprints;

    public SprintController(SprintService sprints) {
        this.sprints = sprints;
    }

    public void register(Router r) {
        r.get("/api/projects/{projectId}/sprints", SIGNED_IN, ctx -> sprints.list(ctx.auth(), ctx.pathLong("projectId")));
        r.post("/api/projects/{projectId}/sprints", SIGNED_IN, ctx -> {
            var s = sprints.create(ctx.auth(), ctx.pathLong("projectId"), ctx.body(CreateSprintRequest.class));
            return ctx.created("/api/sprints/" + s.sprintId(), s);
        });
        r.patch("/api/sprints/{sprintId}", SIGNED_IN,
                ctx -> sprints.update(ctx.auth(), ctx.pathLong("sprintId"), ctx.body(UpdateSprintRequest.class)));
        r.post("/api/sprints/{sprintId}/start", SIGNED_IN,
                ctx -> sprints.start(ctx.auth(), ctx.pathLong("sprintId"), ctx.body(VersionRequest.class)));
        r.post("/api/sprints/{sprintId}/complete", SIGNED_IN,
                ctx -> sprints.completeSprint(ctx.auth(), ctx.pathLong("sprintId"), ctx.body(CompleteSprintRequest.class)));
        r.delete("/api/sprints/{sprintId}", SIGNED_IN, ctx -> {
            sprints.delete(ctx.auth(), ctx.pathLong("sprintId"));
            return null;
        });
    }
}
