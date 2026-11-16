package edu.usc.compass.milestones;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.milestones.MilestoneDtos.CreateMilestoneRequest;
import edu.usc.compass.milestones.MilestoneDtos.ReplaceMilestoneTasksRequest;
import edu.usc.compass.milestones.MilestoneDtos.UpdateMilestoneRequest;
import edu.usc.compass.milestones.dao.MilestoneDao;
import edu.usc.compass.milestones.model.MilestoneModels.MilestoneView;
import edu.usc.compass.projects.ProjectService;

import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Milestones with progress computed from linked tasks on every load (Detailed Design 3.3). Owner: Siya. */
public class MilestoneService {

    private final Database db;
    private final MilestoneDao milestoneDao;
    private final MilestoneProgressCalculator calculator;
    private final ProjectService projects;
    private final Clock clock;

    public MilestoneService(Database db, MilestoneDao milestoneDao, MilestoneProgressCalculator calculator,
                            ProjectService projects, Clock clock) {
        this.db = db;
        this.milestoneDao = milestoneDao;
        this.calculator = calculator;
        this.projects = projects;
        this.clock = clock;
    }

    /** GET /api/projects/{projectId}/milestones. Team member. */
    public List<MilestoneView> list(AuthContext ctx, long projectId) {
        throw Todo.of("Siya", "MilestoneService.list");
    }

    /**
     * SHARED CONTRACT (dashboard — Solomon, guest view — Ramanjot). All milestones of a project with progress,
     * no permission check (the caller already did one). Stub returns an empty list so callers keep working.
     */
    public List<MilestoneView> listForProject(Connection c, long projectId, LocalDate today) {
        Todo.noop("Siya", "MilestoneService.listForProject");
        return List.of();
    }

    /** POST /api/projects/{projectId}/milestones. Team lead. Linked tasks must belong to the same project. */
    public MilestoneView create(AuthContext ctx, long projectId, CreateMilestoneRequest req) {
        throw Todo.of("Siya", "MilestoneService.create");
    }

    /** GET /api/milestones/{milestoneId}. Team member. */
    public MilestoneView get(AuthContext ctx, long milestoneId) {
        throw Todo.of("Siya", "MilestoneService.get");
    }

    /** PATCH /api/milestones/{milestoneId}. Team lead; version-checked. */
    public MilestoneView update(AuthContext ctx, long milestoneId, UpdateMilestoneRequest req) {
        throw Todo.of("Siya", "MilestoneService.update");
    }

    /** PUT /api/milestones/{milestoneId}/tasks. Team lead. Tasks must belong to the same project. */
    public MilestoneView replaceTasks(AuthContext ctx, long milestoneId, ReplaceMilestoneTasksRequest req) {
        throw Todo.of("Siya", "MilestoneService.replaceTasks");
    }

    /** DELETE /api/milestones/{milestoneId}. Team lead. */
    public void delete(AuthContext ctx, long milestoneId) {
        throw Todo.of("Siya", "MilestoneService.delete");
    }
}
