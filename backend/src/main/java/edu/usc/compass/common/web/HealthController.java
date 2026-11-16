package edu.usc.compass.common.web;

import edu.usc.compass.common.db.Database;

import java.time.Instant;
import java.util.Map;

/**
 * GET /api/health — used by the host health check and the Deployment doc's connectivity checks.
 * Always 200 while Tomcat is up; "database" reports up / down / unconfigured.
 * PLATFORM — complete.
 */
public final class HealthController {

    private final Database db;

    public HealthController(Database db) {
        this.db = db;
    }

    public void register(Router r) {
        r.get("/api/health", Router.Access.PUBLIC, ctx -> Map.of(
                "status", "ok",
                "database", !db.isConfigured() ? "unconfigured" : (db.ping() ? "up" : "down"),
                "time", Instant.now().toString()));
    }
}
