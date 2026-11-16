package edu.usc.compass.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    @Test
    void extractsPathParams() {
        Router r = new Router();
        r.get("/api/teams/{teamId}/members", Router.Access.SIGNED_IN, ctx -> null);
        var match = r.match("GET", "/api/teams/42/members").orElseThrow();
        assertEquals("42", match.pathParams().get("teamId"));
    }

    @Test
    void literalSegmentBeatsParam() {
        Router r = new Router();
        r.delete("/api/teams/{teamId}/members/{userId}", Router.Access.SIGNED_IN, ctx -> "param");
        r.delete("/api/teams/{teamId}/members/me", Router.Access.SIGNED_IN, ctx -> "me");
        var match = r.match("DELETE", "/api/teams/1/members/me").orElseThrow();
        assertEquals("/api/teams/{teamId}/members/me", match.route().pattern());
    }

    @Test
    void methodMustMatch() {
        Router r = new Router();
        r.get("/api/health", Router.Access.PUBLIC, ctx -> null);
        assertTrue(r.match("POST", "/api/health").isEmpty());
        assertTrue(r.pathExists("/api/health"));
    }

    @Test
    void rejectsDuplicateRoutes() {
        Router r = new Router();
        r.get("/api/x", Router.Access.PUBLIC, ctx -> null);
        assertThrows(IllegalStateException.class, () -> r.get("/api/x", Router.Access.PUBLIC, ctx -> null));
    }
}
