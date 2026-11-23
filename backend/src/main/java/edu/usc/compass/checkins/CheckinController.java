package edu.usc.compass.checkins;

import edu.usc.compass.checkins.CheckinDtos.ScheduleCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.SubmitUpdateRequest;
import edu.usc.compass.checkins.CheckinDtos.UpdateCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.When;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.web.RequestContext;
import edu.usc.compass.common.web.Router;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Check-ins (Detailed Design 3.5.8). Owner: Graham. */
public final class CheckinController {

    private final CheckinService checkins;

    public CheckinController(CheckinService checkins) {
        this.checkins = checkins;
    }

    public void register(Router r) {
        r.get("/api/checkins", SIGNED_IN, ctx -> checkins.list(ctx.auth(),
                ctx.queryLong("teamId").orElseThrow(() -> ApiException.validation("teamId", "Team id is required.")),
                ctx.queryEnum("when", When.class).orElse(When.upcoming)));
        r.post("/api/checkins", SIGNED_IN, ctx -> {
            var c = checkins.schedule(ctx.auth(), body(ctx, ScheduleCheckinRequest.class));
            return ctx.created("/api/checkins/" + c.checkinId(), c);
        });
        r.get("/api/checkins/{checkinId}", SIGNED_IN, ctx -> checkins.get(ctx.auth(), ctx.pathLong("checkinId")));
        r.patch("/api/checkins/{checkinId}", SIGNED_IN,
                ctx -> checkins.update(ctx.auth(), ctx.pathLong("checkinId"), body(ctx, UpdateCheckinRequest.class)));
        r.post("/api/checkins/{checkinId}/cancel", SIGNED_IN,
                ctx -> checkins.cancel(ctx.auth(), ctx.pathLong("checkinId"), body(ctx, VersionRequest.class)));
        r.post("/api/checkins/{checkinId}/complete", SIGNED_IN,
                ctx -> checkins.complete(ctx.auth(), ctx.pathLong("checkinId"), body(ctx, VersionRequest.class)));
        r.get("/api/checkins/{checkinId}/updates", SIGNED_IN, ctx -> checkins.listUpdates(ctx.auth(), ctx.pathLong("checkinId")));
        r.put("/api/checkins/{checkinId}/updates/me", SIGNED_IN,
                ctx -> checkins.submitMyUpdate(ctx.auth(), ctx.pathLong("checkinId"), body(ctx, SubmitUpdateRequest.class)));
        r.patch("/api/checkins/{checkinId}/blockers/{blockerId}", SIGNED_IN,
                ctx -> checkins.resolveBlocker(ctx.auth(), ctx.pathLong("checkinId"), ctx.pathLong("blockerId")));
    }

    private static <T> T body(RequestContext ctx, Class<T> type) {
        T request = ctx.body(type);
        if (request == null) throw ApiException.badRequest("Request body must be a JSON object.");
        return request;
    }
}
