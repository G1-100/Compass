package edu.usc.compass.projects;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.milestones.MilestoneService;
import edu.usc.compass.projects.model.ProjectModels.DashboardView;
import edu.usc.compass.tasks.dao.TaskDao;

import java.time.Clock;

/**
 * GET /api/projects/{projectId}/dashboard: project progress, team assignments, upcoming deadlines,
 * milestones with status, and the user's own tasks — all for ONE project only.
 * Owner: Solomon. Tests: Regression 6, 7, 13, 14; Stress 2.
 */
public class DashboardService {

    private final Database db;
    private final ProjectService projects;
    private final TaskDao taskDao;
    private final MilestoneService milestones;
    private final Clock clock;

    public DashboardService(Database db, ProjectService projects, TaskDao taskDao, MilestoneService milestones, Clock clock) {
        this.db = db;
        this.projects = projects;
        this.taskDao = taskDao;
        this.milestones = milestones;
        this.clock = clock;
    }

    /**
     * Team member. Build the DashboardView from TaskDao queries and MilestoneService.listForProject.
     * Progress = Done effort points / all effort points (EffortLevel.points()). Reading must never write.
     */
    public DashboardView getDashboard(AuthContext ctx, long projectId) {
        throw Todo.of("Solomon", "DashboardService.getDashboard");
    }
}
