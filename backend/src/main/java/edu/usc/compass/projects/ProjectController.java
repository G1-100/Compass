package edu.usc.compass.projects;

import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.web.Router;
import edu.usc.compass.projects.ProjectDtos.CreateProjectRequest;
import edu.usc.compass.projects.ProjectDtos.UpdateProjectRequest;

import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/** Routes for Projects (Detailed Design 3.5.3, guest-link routes are in GuestController). Owner: Solomon. */
public final class ProjectController {

    private final ProjectService projects;
    private final DashboardService dashboard;

    public ProjectController(ProjectService projects, DashboardService dashboard) {
        this.projects = projects;
        this.dashboard = dashboard;
    }

    public void register(Router r) {
        r.get("/api/projects", SIGNED_IN, ctx -> projects.search(ctx.auth(), ctx.query("q").orElse("")));
        r.get("/api/teams/{teamId}/projects", SIGNED_IN,
                ctx -> projects.listForTeam(ctx.auth(), ctx.pathLong("teamId"), ctx.queryBool("includeArchived")));
        r.post("/api/teams/{teamId}/projects", SIGNED_IN, ctx -> {
            var p = projects.create(ctx.auth(), ctx.pathLong("teamId"), ctx.body(CreateProjectRequest.class));
            return ctx.created("/api/projects/" + p.projectId(), p);
        });
        r.get("/api/projects/{projectId}", SIGNED_IN, ctx -> projects.get(ctx.auth(), ctx.pathLong("projectId")));
        r.patch("/api/projects/{projectId}", SIGNED_IN,
                ctx -> projects.update(ctx.auth(), ctx.pathLong("projectId"), ctx.body(UpdateProjectRequest.class)));
        r.post("/api/projects/{projectId}/archive", SIGNED_IN,
                ctx -> projects.archive(ctx.auth(), ctx.pathLong("projectId"), ctx.body(VersionRequest.class)));
        r.get("/api/projects/{projectId}/dashboard", SIGNED_IN, ctx -> dashboard.getDashboard(ctx.auth(), ctx.pathLong("projectId")));
    }
}
