package edu.usc.compass.guest;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.Hashing;
import edu.usc.compass.common.Validator;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.guest.dao.GuestLinkDao;
import edu.usc.compass.guest.dao.GuestLinkDao.GuestProjectRow;
import edu.usc.compass.guest.dao.GuestLinkDao.GuestTaskRow;
import edu.usc.compass.guest.model.GuestModels.CreatedGuestLink;
import edu.usc.compass.guest.model.GuestModels.GuestLink;
import edu.usc.compass.guest.model.GuestModels.GuestLinkStatus;
import edu.usc.compass.guest.model.GuestModels.GuestMilestone;
import edu.usc.compass.guest.model.GuestModels.GuestProjectView;
import edu.usc.compass.guest.model.GuestModels.GuestTask;
import edu.usc.compass.milestones.MilestoneService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;
import edu.usc.compass.teams.AuditLogService;
import edu.usc.compass.teams.model.TeamModels.AuditEventType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only sharing links. The raw token (Hashing.randomToken(32)) appears only in the share URL; the DB stores
 * its SHA-256 hash. Owner: Ramanjot. Tests: Black box 6 & 7, Stress 10.
 */
public class GuestLinkService {

    /** 32 random bytes encode to 43 URL-safe characters; anything much longer is not one of ours. */
    static final int MAX_TOKEN_LENGTH = 128;
    static final int TOKEN_BYTES = 32;

    private final Database db;
    private final GuestLinkDao guestLinkDao;
    private final ProjectService projects;
    @SuppressWarnings("unused") // guest-view queries live in GuestLinkDao so only approved columns are ever read
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
        return db.withConnection(c -> {
            projects.requirePermission(c, ctx, projectId, Permission.MANAGE_GUEST_LINKS);
            Instant now = Instant.now(clock);
            return guestLinkDao.findByProject(c, projectId).stream().map(l -> withStatus(l, now)).toList();
        });
    }

    /** POST /api/projects/{projectId}/guest-links. Team lead. Optional expiry (must be future). Audit GUEST_LINK_CREATED. */
    public CreatedGuestLink create(AuthContext ctx, long projectId, Instant expiresAt) {
        return db.inTransaction(c -> {
            long teamId = projects.requirePermission(c, ctx, projectId, Permission.MANAGE_GUEST_LINKS);
            Instant now = Instant.now(clock);
            new Validator().future("expiresAt", expiresAt, now).throwIfInvalid();

            String rawToken = Hashing.randomToken(TOKEN_BYTES);
            long linkId = guestLinkDao.insert(c, projectId, Hashing.sha256Hex(rawToken), ctx.userId(), expiresAt);
            auditLog.record(c, teamId, ctx.userId(), null, AuditEventType.GUEST_LINK_CREATED,
                    "Guest link " + linkId + " created for project " + projectId
                            + (expiresAt == null ? " (no expiry)" : " (expires " + expiresAt + ")"));

            GuestLink link = guestLinkDao.findById(c, linkId).orElseThrow();
            return new CreatedGuestLink(withStatus(link, now), shareUrl(rawToken));
        });
    }

    /** DELETE /api/projects/{projectId}/guest-links/{linkId}. Team lead. Audit GUEST_LINK_REVOKED. */
    public void revoke(AuthContext ctx, long projectId, long linkId) {
        db.inTransaction(c -> {
            long teamId = projects.requirePermission(c, ctx, projectId, Permission.MANAGE_GUEST_LINKS);
            GuestLink link = guestLinkDao.findForUpdate(c, linkId)
                    .filter(l -> l.projectId() == projectId)
                    .orElseThrow(ApiException::notFound);
            if (link.revokedAt() != null) {
                return null; // already revoked: idempotent, no second audit event
            }
            guestLinkDao.revoke(c, linkId, Instant.now(clock));
            auditLog.record(c, teamId, ctx.userId(), null, AuditEventType.GUEST_LINK_REVOKED,
                    "Guest link " + linkId + " revoked for project " + projectId);
            return null;
        });
    }

    /**
     * GET /api/guest/{token} — no session. Hash the token and look it up; unknown, expired or revoked ->
     * ApiException.guestLinkUnavailable() with no project data. Otherwise build GuestProjectView.
     */
    public GuestProjectView viewByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            throw ApiException.guestLinkUnavailable();
        }
        String tokenHash = Hashing.sha256Hex(rawToken);
        return db.withConnection(c -> {
            Instant now = Instant.now(clock);
            GuestLink link = guestLinkDao.findByTokenHash(c, tokenHash)
                    .filter(l -> statusAt(l, now) == GuestLinkStatus.ACTIVE)
                    .orElseThrow(ApiException::guestLinkUnavailable);
            long projectId = link.projectId();

            GuestProjectRow project = guestLinkDao.findProjectForGuest(c, projectId)
                    .orElseThrow(ApiException::guestLinkUnavailable);
            List<GuestTaskRow> taskRows = guestLinkDao.findTasksForGuest(c, projectId);
            List<GuestMilestone> guestMilestones = milestones.listForProject(c, projectId, LocalDate.now(clock)).stream()
                    .map(m -> new GuestMilestone(m.milestone().name(),
                            m.progress() == null ? null : m.progress().progressPercent(),
                            m.progress() == null ? null : m.progress().deadlineStatus()))
                    .toList();

            return new GuestProjectView(
                    project.name(),
                    project.description(),
                    overallProgressPercent(taskRows),
                    guestMilestones,
                    taskRows.stream().map(t -> new GuestTask(t.title(), t.status())).toList(),
                    guestLinkDao.findMemberDisplayNamesForGuest(c, projectId));
        });
    }

    // ---- helpers ----------------------------------------------------------

    /** REVOKED wins over EXPIRED; a link is expired from the instant expiresAt is reached. */
    static GuestLinkStatus statusAt(GuestLink link, Instant now) {
        if (link.revokedAt() != null) {
            return GuestLinkStatus.REVOKED;
        }
        if (link.expiresAt() != null && !now.isBefore(link.expiresAt())) {
            return GuestLinkStatus.EXPIRED;
        }
        return GuestLinkStatus.ACTIVE;
    }

    static GuestLink withStatus(GuestLink l, Instant now) {
        return new GuestLink(l.linkId(), l.projectId(), l.createdBy(), l.createdAt(), l.expiresAt(), l.revokedAt(),
                statusAt(l, now));
    }

    /** Effort-weighted share of Done tasks (same rule as the dashboard), or null when there are no tasks. */
    static Double overallProgressPercent(List<GuestTaskRow> tasks) {
        int total = tasks.stream().mapToInt(t -> t.effort().points()).sum();
        if (total == 0) {
            return null;
        }
        int done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).mapToInt(t -> t.effort().points()).sum();
        return Math.round(done * 1000.0 / total) / 10.0;
    }

    private String shareUrl(String rawToken) {
        String base = config.publicSiteUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/guest/" + rawToken;
    }
}
