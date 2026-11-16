package edu.usc.compass.guest;

import edu.usc.compass.common.web.Router;

import java.time.Instant;

import static edu.usc.compass.common.web.Router.Access.PUBLIC;
import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for guest links (Detailed Design 3.5.3 guest-links + 3.5.9). Owner: Ramanjot. */
public final class GuestController {

    /** Body for POST .../guest-links. expiresAt null = never expires. */
    public record CreateGuestLinkRequest(Instant expiresAt) {
    }

    private final GuestLinkService guestLinks;

    public GuestController(GuestLinkService guestLinks) {
        this.guestLinks = guestLinks;
    }

    public void register(Router r) {
        r.get("/api/projects/{projectId}/guest-links", SIGNED_IN, ctx -> guestLinks.list(ctx.auth(), ctx.pathLong("projectId")));
        r.post("/api/projects/{projectId}/guest-links", SIGNED_IN, ctx -> {
            var created = guestLinks.create(ctx.auth(), ctx.pathLong("projectId"), ctx.body(CreateGuestLinkRequest.class).expiresAt());
            return ctx.created("/api/projects/" + created.link().projectId() + "/guest-links/" + created.link().linkId(), created);
        });
        r.delete("/api/projects/{projectId}/guest-links/{linkId}", SIGNED_IN, ctx -> {
            guestLinks.revoke(ctx.auth(), ctx.pathLong("projectId"), ctx.pathLong("linkId"));
            return null;
        });
        r.get("/api/guest/{token}", PUBLIC, ctx -> guestLinks.viewByToken(ctx.path("token")));
    }
}
