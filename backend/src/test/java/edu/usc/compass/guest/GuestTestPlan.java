package edu.usc.compass.guest;

import com.fasterxml.jackson.databind.JsonNode;
import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Hashing;
import edu.usc.compass.common.Json;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.db.SqlWork;
import edu.usc.compass.guest.dao.GuestLinkDao;
import edu.usc.compass.guest.dao.GuestLinkDao.GuestProjectRow;
import edu.usc.compass.guest.dao.GuestLinkDao.GuestTaskRow;
import edu.usc.compass.guest.model.GuestModels.CreatedGuestLink;
import edu.usc.compass.guest.model.GuestModels.GuestLink;
import edu.usc.compass.guest.model.GuestModels.GuestLinkStatus;
import edu.usc.compass.guest.model.GuestModels.GuestProjectView;
import edu.usc.compass.milestones.MilestoneService;
import edu.usc.compass.milestones.model.MilestoneModels.DeadlineStatus;
import edu.usc.compass.milestones.model.MilestoneModels.Milestone;
import edu.usc.compass.milestones.model.MilestoneModels.MilestoneProgress;
import edu.usc.compass.milestones.model.MilestoneModels.MilestoneView;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.EffortLevel;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;
import edu.usc.compass.teams.AuditLogService;
import edu.usc.compass.teams.model.TeamModels.AuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testing Plan cases for guest links (Black box 6, 7; Stress 10). Owner: Ramanjot. */
class GuestTestPlan {

    private static final Instant NOW = Instant.parse("2026-11-22T18:00:00Z");
    private static final long PROJECT_ID = 7;
    private static final long TEAM_ID = 3;
    private static final AuthContext LEAD =
            new AuthContext(11, "lead@example.com", "Lead", SystemRole.USER, Map.of());

    private Database db;
    private Connection conn;
    private FakeGuestLinkDao dao;
    private ProjectService projects;
    private MilestoneService milestones;
    private AuditLogService audit;
    private GuestLinkService service;

    @BeforeEach
    void setUp() throws Exception {
        db = mock(Database.class);
        conn = mock(Connection.class);
        when(db.inTransaction(any())).thenAnswer(inv -> ((SqlWork<?>) inv.getArgument(0)).run(conn));
        when(db.withConnection(any())).thenAnswer(inv -> ((SqlWork<?>) inv.getArgument(0)).run(conn));
        dao = new FakeGuestLinkDao();
        projects = mock(ProjectService.class);
        when(projects.requirePermission(any(), any(), eq(PROJECT_ID), eq(Permission.MANAGE_GUEST_LINKS))).thenReturn(TEAM_ID);
        milestones = mock(MilestoneService.class);
        when(milestones.listForProject(any(), eq(PROJECT_ID), any())).thenReturn(List.of(new MilestoneView(
                new Milestone(1, PROJECT_ID, "Beta", "secret milestone description", null, NOW, 1),
                new MilestoneProgress(50.0, DeadlineStatus.ON_SCHEDULE), List.of(100L))));
        audit = mock(AuditLogService.class);
        AppConfig config = AppConfig.from(Map.of("PUBLIC_SITE_URL", "https://compass.example.com/"));
        service = new GuestLinkService(db, dao, projects, mock(TaskDao.class), milestones, audit, config,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- Black box 6 ----------------------------------------------------

    @Test
    void bb6_revokedLinkStopsWorking() {
        String token = tokenOf(service.create(LEAD, PROJECT_ID, null));
        assertEquals("Compass", service.viewByToken(token).projectName());

        long linkId = dao.links.keySet().iterator().next();
        service.revoke(LEAD, PROJECT_ID, linkId);

        assertUnavailable(() -> service.viewByToken(token));
        assertEquals(GuestLinkStatus.REVOKED, service.list(LEAD, PROJECT_ID).get(0).status());
        verify(audit).record(eq(conn), eq(TEAM_ID), eq(LEAD.userId()), isNull(), eq(AuditEventType.GUEST_LINK_REVOKED), anyString());
    }

    @Test
    void revokingTwiceIsIdempotentAndAuditsOnce() {
        service.create(LEAD, PROJECT_ID, null);
        long linkId = dao.links.keySet().iterator().next();
        service.revoke(LEAD, PROJECT_ID, linkId);
        Instant first = dao.links.get(linkId).revokedAt();
        service.revoke(LEAD, PROJECT_ID, linkId);
        assertEquals(first, dao.links.get(linkId).revokedAt());
        verify(audit).record(any(), anyLong(), any(), any(), eq(AuditEventType.GUEST_LINK_REVOKED), anyString());
    }

    @Test
    void revokingALinkFromAnotherProjectIs404() throws Exception {
        long otherLink = dao.insert(conn, 99, "h", 1, null);
        when(projects.requirePermission(any(), any(), eq(99L), any())).thenReturn(5L);
        ApiException e = assertThrows(ApiException.class, () -> service.revoke(LEAD, PROJECT_ID, otherLink));
        assertEquals(ErrorCode.NOT_FOUND, e.code());
        assertNull(dao.links.get(otherLink).revokedAt());
    }

    // ---- Black box 7 ----------------------------------------------------

    @Test
    void bb7_guestSeesOnlyApprovedFields() {
        String token = tokenOf(service.create(LEAD, PROJECT_ID, null));
        GuestProjectView view = service.viewByToken(token);

        assertEquals("Compass", view.projectName());
        assertEquals("Team project tracker", view.summary());
        assertEquals(List.of("Ada", "Grace"), view.memberDisplayNames());
        assertEquals(2, view.tasks().size());
        assertEquals("Write spec", view.tasks().get(0).title());
        assertEquals(TaskStatus.DONE, view.tasks().get(0).status());
        assertEquals("Beta", view.milestones().get(0).name());
        assertEquals(50.0, view.milestones().get(0).progressPercent());
        // Effort-weighted: Done MODERATE (3) out of 3 + HIGH (5) = 37.5%
        assertEquals(37.5, view.overallProgressPercent());

        // The serialized JSON has exactly the approved keys, nothing more.
        JsonNode json = Json.MAPPER.valueToTree(view);
        assertEquals(Set.of("projectName", "summary", "overallProgressPercent", "milestones", "tasks", "memberDisplayNames"),
                fieldNames(json));
        assertEquals(Set.of("title", "status"), fieldNames(json.get("tasks").get(0)));
        assertEquals(Set.of("name", "progressPercent", "deadlineStatus"), fieldNames(json.get("milestones").get(0)));
        String body = json.toString();
        assertFalse(body.contains("@"), "no email addresses");
        assertFalse(body.contains("secret"), "no milestone descriptions");
        assertFalse(body.contains("effort"), "no effort or other internal task fields");
    }

    @Test
    void createdLinkStoresOnlyTheHashAndReturnsShareUrlOnce() {
        CreatedGuestLink created = service.create(LEAD, PROJECT_ID, NOW.plus(Duration.ofDays(3)));
        String token = tokenOf(created);

        assertTrue(created.shareUrl().startsWith("https://compass.example.com/guest/"), created.shareUrl());
        assertTrue(token.length() >= 43, "32 bytes of entropy");
        GuestLink stored = dao.links.get(created.link().linkId());
        assertEquals(Hashing.sha256Hex(token), dao.hashes.get(stored.linkId()));
        assertNotEquals(token, dao.hashes.get(stored.linkId()));
        assertEquals(GuestLinkStatus.ACTIVE, created.link().status());
        assertFalse(Json.toString(service.list(LEAD, PROJECT_ID)).contains(token), "list never exposes the token");
        verify(audit).record(eq(conn), eq(TEAM_ID), eq(LEAD.userId()), isNull(), eq(AuditEventType.GUEST_LINK_CREATED), anyString());
    }

    @Test
    void tokensAreUnique() {
        String a = tokenOf(service.create(LEAD, PROJECT_ID, null));
        String b = tokenOf(service.create(LEAD, PROJECT_ID, null));
        assertNotEquals(a, b);
    }

    @Test
    void expiryMustBeInTheFuture() {
        ApiException e = assertThrows(ApiException.class, () -> service.create(LEAD, PROJECT_ID, NOW));
        assertEquals(ErrorCode.VALIDATION_FAILED, e.code());
        assertTrue(e.fields().containsKey("expiresAt"));
        assertTrue(dao.links.isEmpty());
        verify(audit, never()).record(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void permissionIsCheckedBeforeEveryLeadAction() throws Exception {
        when(projects.requirePermission(any(), any(), eq(PROJECT_ID), eq(Permission.MANAGE_GUEST_LINKS)))
                .thenThrow(ApiException.forbidden());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(ApiException.class, () -> service.list(LEAD, PROJECT_ID)).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(ApiException.class, () -> service.create(LEAD, PROJECT_ID, null)).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(ApiException.class, () -> service.revoke(LEAD, PROJECT_ID, 1)).code());
        assertTrue(dao.links.isEmpty());
    }

    @Test
    void listComputesStatusFromRevokedAndExpiry() {
        long active = dao.insert(conn, PROJECT_ID, "a", 1, NOW.plusSeconds(60));
        long expired = dao.insert(conn, PROJECT_ID, "b", 1, NOW);
        long revoked = dao.insert(conn, PROJECT_ID, "c", 1, NOW.minusSeconds(60));
        dao.revoke(conn, revoked, NOW.minusSeconds(120));

        Map<Long, GuestLinkStatus> byId = new HashMap<>();
        service.list(LEAD, PROJECT_ID).forEach(l -> byId.put(l.linkId(), l.status()));
        assertEquals(GuestLinkStatus.ACTIVE, byId.get(active));
        assertEquals(GuestLinkStatus.EXPIRED, byId.get(expired));
        assertEquals(GuestLinkStatus.REVOKED, byId.get(revoked), "revoked wins over expired");
    }

    // ---- 404 with no data ------------------------------------------------

    @Test
    void unknownExpiredRevokedTokensReturn404WithNoData() {
        // unknown and malformed
        assertUnavailable(() -> service.viewByToken("not-a-real-token"));
        assertUnavailable(() -> service.viewByToken(""));
        assertUnavailable(() -> service.viewByToken("x".repeat(500)));

        // expired: the expiry instant itself already counts as expired
        String expiring = "expiring-token";
        dao.insert(conn, PROJECT_ID, Hashing.sha256Hex(expiring), 1, NOW);
        assertUnavailable(() -> service.viewByToken(expiring));

        // revoked
        String revoked = "revoked-token";
        long id = dao.insert(conn, PROJECT_ID, Hashing.sha256Hex(revoked), 1, null);
        dao.revoke(conn, id, NOW);
        assertUnavailable(() -> service.viewByToken(revoked));

        assertEquals(0, dao.projectReads, "no project data is read for a bad link");
    }

    @Test
    void stress10_concurrentGuestViewsAllSucceedOrAllFail() throws Exception {
        String good = tokenOf(service.create(LEAD, PROJECT_ID, null));
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String token = i % 2 == 0 ? good : "bad-" + i;
                calls.add(() -> {
                    try {
                        return service.viewByToken(token).projectName().equals("Compass");
                    } catch (ApiException e) {
                        return e.code() == ErrorCode.GUEST_LINK_UNAVAILABLE ? null : false;
                    }
                });
            }
            int ok = 0;
            int unavailable = 0;
            for (Future<Boolean> f : pool.invokeAll(calls)) {
                Boolean r = f.get();
                if (r == null) {
                    unavailable++;
                } else if (r) {
                    ok++;
                }
            }
            assertEquals(100, ok);
            assertEquals(100, unavailable);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void progressIsNullWithoutTasks() {
        assertNull(GuestLinkService.overallProgressPercent(List.of()));
    }

    // ---- helpers ----------------------------------------------------------

    private static String tokenOf(CreatedGuestLink created) {
        return created.shareUrl().substring(created.shareUrl().lastIndexOf('/') + 1);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(ErrorCode.GUEST_LINK_UNAVAILABLE, e.code());
        assertEquals(404, e.httpStatus());
        assertNull(e.current(), "error carries no project data");
        assertNull(e.fields());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** In-memory GuestLinkDao; thread-safe enough for the stress test (reads only after setup). */
    private static final class FakeGuestLinkDao extends GuestLinkDao {
        final Map<Long, GuestLink> links = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<Long, String> hashes = new java.util.concurrent.ConcurrentHashMap<>();
        volatile int projectReads;
        private long nextId = 1;

        @Override
        public synchronized long insert(Connection c, long projectId, String tokenHash, long createdBy, Instant expiresAt) {
            long id = nextId++;
            links.put(id, new GuestLink(id, projectId, createdBy, NOW, expiresAt, null, null));
            hashes.put(id, tokenHash);
            return id;
        }

        @Override
        public List<GuestLink> findByProject(Connection c, long projectId) {
            return links.values().stream().filter(l -> l.projectId() == projectId).toList();
        }

        @Override
        public Optional<GuestLink> findById(Connection c, long linkId) {
            return Optional.ofNullable(links.get(linkId));
        }

        @Override
        public Optional<GuestLink> findForUpdate(Connection c, long linkId) {
            return findById(c, linkId);
        }

        @Override
        public Optional<GuestLink> findByTokenHash(Connection c, String tokenHash) {
            return hashes.entrySet().stream().filter(e -> e.getValue().equals(tokenHash))
                    .map(e -> links.get(e.getKey())).findFirst();
        }

        @Override
        public void revoke(Connection c, long linkId, Instant now) {
            GuestLink l = links.get(linkId);
            if (l.revokedAt() == null) {
                links.put(linkId, new GuestLink(l.linkId(), l.projectId(), l.createdBy(), l.createdAt(), l.expiresAt(), now, null));
            }
        }

        @Override
        public synchronized Optional<GuestProjectRow> findProjectForGuest(Connection c, long projectId) {
            projectReads++;
            return projectId == PROJECT_ID ? Optional.of(new GuestProjectRow("Compass", "Team project tracker")) : Optional.empty();
        }

        @Override
        public List<GuestTaskRow> findTasksForGuest(Connection c, long projectId) {
            return List.of(new GuestTaskRow("Write spec", TaskStatus.DONE, EffortLevel.MODERATE),
                    new GuestTaskRow("Build API", TaskStatus.IN_PROGRESS, EffortLevel.HIGH));
        }

        @Override
        public List<String> findMemberDisplayNamesForGuest(Connection c, long projectId) {
            return List.of("Ada", "Grace");
        }
    }
}
