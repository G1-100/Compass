package edu.usc.compass.guest;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.guest.dao.GuestLinkDao;
import edu.usc.compass.guest.model.GuestModels.CreatedGuestLink;
import edu.usc.compass.guest.model.GuestModels.GuestLink;
import edu.usc.compass.guest.model.GuestModels.GuestProjectView;
import edu.usc.compass.milestones.MilestoneService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.teams.AuditLogService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Read-only sharing links. The raw token (Hashing.randomToken(32)) appears only in the share URL; the DB stores
 * its SHA-256 hash. Owner: Ramanjot. Tests: Black box 6 & 7, Stress 10.
 */
public class GuestLinkService {

    private final Database db;
    private final GuestLinkDao guestLinkDao;
    private final ProjectService projects;
    private final TaskDao taskDao;
    private final MilestoneService milestones;
    private final AuditLogService auditLog;
    private final AppConfig config;
    private final Clock clock;

    public GuestLinkService(Database db, GuestLinkDao guestLinkDao, ProjectService projects, TaskDao taskDao,
                            MilestoneService milestones, AuditLogService auditLog, AppConfig config, Clock clock) {
        this.db = db;
        this.guestLinkDao = guestLinkDao;
        this.projects = projects;
        this.taskDao = taskDao;
        this.milestones = milestones;
        this.auditLog = auditLog;
        this.config = config;
        this.clock = clock;
    }

    /** GET /api/projects/{projectId}/guest-links. Team lead. Status computed from revokedAt/expiresAt. */
    public List<GuestLink> list(AuthContext ctx, long projectId) {
        throw Todo.of("Ramanjot", "GuestLinkService.list");
    }

    /** POST /api/projects/{projectId}/guest-links. Team lead. Optional expiry (must be future). Audit GUEST_LINK_CREATED. */
    public CreatedGuestLink create(AuthContext ctx, long projectId, Instant expiresAt) {
        throw Todo.of("Ramanjot", "GuestLinkService.create");
    }

    /** DELETE /api/projects/{projectId}/guest-links/{linkId}. Team lead. Audit GUEST_LINK_REVOKED. */
    public void revoke(AuthContext ctx, long projectId, long linkId) {
        throw Todo.of("Ramanjot", "GuestLinkService.revoke");
    }

    /**
     * GET /api/guest/{token} — no session. Hash the token and look it up; unknown, expired or revoked ->
     * ApiException.guestLinkUnavailable() with no project data. Otherwise build GuestProjectView.
     */
    public GuestProjectView viewByToken(String rawToken) {
        throw Todo.of("Ramanjot", "GuestLinkService.viewByToken");
    }
}
