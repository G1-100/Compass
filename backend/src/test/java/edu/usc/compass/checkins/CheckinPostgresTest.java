package edu.usc.compass.checkins;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.checkins.CheckinDtos.BlockerInput;
import edu.usc.compass.checkins.CheckinDtos.ScheduleCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.SubmitUpdateRequest;
import edu.usc.compass.checkins.CheckinDtos.UpdateCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.When;
import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.checkins.model.CheckinModels.Blocker;
import edu.usc.compass.checkins.model.CheckinModels.Checkin;
import edu.usc.compass.checkins.model.CheckinModels.CheckinStatus;
import edu.usc.compass.checkins.model.CheckinModels.CheckinUpdate;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Severity;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.notifications.model.NotificationModels.NotificationRequest;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.teams.model.TeamRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static edu.usc.compass.common.ErrorCode.FORBIDDEN;
import static edu.usc.compass.common.ErrorCode.INVALID_STATE;
import static edu.usc.compass.common.ErrorCode.NOT_FOUND;
import static edu.usc.compass.common.ErrorCode.VALIDATION_FAILED;
import static edu.usc.compass.common.ErrorCode.VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Set COMPASS_CHECKIN_TEST_DB_URL to a development PostgreSQL JDBC URL with permission to create a schema,
 * then run mvn test from backend/. The suite creates and drops its own randomly named schema.
 */
@EnabledIfEnvironmentVariable(named = "COMPASS_CHECKIN_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckinPostgresTest {

    private static final Instant NOW = Instant.parse("2026-11-21T20:00:00Z");
    private static final AuthContext LEAD = user(2, 1, TeamRole.LEAD);
    private static final AuthContext ALEX = user(3, 1, TeamRole.MEMBER);
    private static final AuthContext JORDAN = user(4, 1, TeamRole.MEMBER);
    private static final AuthContext OUTSIDER = user(1, 2, TeamRole.LEAD);

    private final String schema = "checkin_test_" + UUID.randomUUID().toString().replace("-", "");
    private final CheckinDao dao = new CheckinDao();
    private final TestClock clock = new TestClock();
    private Database db;
    private String url;
    private String seed;
    private PermissionService permissions;
    private NotificationService notifications;
    private CheckinService service;

    @BeforeAll
    void createSchema() throws Exception {
        url = System.getenv("COMPASS_CHECKIN_TEST_DB_URL");
        try (var c = DriverManager.getConnection(url); var statement = c.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        String isolatedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        db = Database.fromConfig(AppConfig.from(Map.of("SUPABASE_DB_URL", isolatedUrl)));
        Path database = Path.of("..", "database");
        execute(Files.readString(database.resolve("schema.sql")));
        seed = Files.readString(database.resolve("seed.sql"));
    }

    @AfterAll
    void dropSchema() throws SQLException {
        if (db != null) db.close();
        if (url != null) {
            try (var c = DriverManager.getConnection(url); var statement = c.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        execute("TRUNCATE users, email_queue RESTART IDENTITY CASCADE");
        execute(seed);
        execute("""
                INSERT INTO teams (name, created_by) VALUES ('Other team', 1);
                INSERT INTO team_memberships (team_id, user_id, role) VALUES (2, 1, 'LEAD');
                INSERT INTO projects (team_id, name, created_by) VALUES (2, 'Other project', 1);
                INSERT INTO tasks (project_id, title, effort, created_by) VALUES (2, 'Other task', 'MINOR', 1);
                """);

        permissions = mock(PermissionService.class);
        doAnswer(call -> {
            AuthContext caller = call.getArgument(0);
            Permission permission = call.getArgument(1);
            long teamId = call.getArgument(2);
            if (!caller.isSystemAdmin() && caller.roleIn(teamId).filter(role -> role.atLeast(permission.minimumRole())).isEmpty()) {
                throw ApiException.forbidden();
            }
            return null;
        }).when(permissions).require(any(), any(), anyLong());

        notifications = mock(NotificationService.class);
        doAnswer(call -> {
            queueNotifications(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(notifications).notify(any(), any());
        service = serviceWith(dao, notifications);
    }

    @Test
    void schedulesAndListsInOrderWithTeamPermissions() {
        var earlier = schedule(2);
        var later = schedule(6);
        var latest = schedule(10);
        service.schedule(OUTSIDER, new ScheduleCheckinRequest(2, NOW.plus(Duration.ofHours(5)), null, null));
        assertEquals(List.of(earlier, later, latest), service.list(ALEX, 1, When.upcoming));
        assertEquals(earlier, service.get(JORDAN, earlier.checkinId()));
        assertEquals(9, count("SELECT count(*) FROM email_queue WHERE subject = 'CHECKIN_SCHEDULED' AND to_email <> 'admin@usc.edu'"));
        assertCode(FORBIDDEN, () -> service.list(OUTSIDER, 1, When.upcoming));
        assertCode(FORBIDDEN, () -> service.get(OUTSIDER, earlier.checkinId()));
        assertCode(FORBIDDEN, () -> service.listUpdates(OUTSIDER, earlier.checkinId()));
        assertCode(FORBIDDEN, () -> service.submitMyUpdate(OUTSIDER, earlier.checkinId(), progress(0, "Wrong team")));
        assertCode(FORBIDDEN, () -> service.schedule(ALEX, new ScheduleCheckinRequest(1, NOW.plusSeconds(3_600), null, null)));

        clock.set(later.scheduledAt());
        assertEquals(List.of(later, latest), service.list(ALEX, 1, When.upcoming));
        assertEquals(List.of(earlier), service.list(ALEX, 1, When.past));
        clock.set(later.scheduledAt().plusSeconds(1));
        assertEquals(List.of(later, earlier), service.list(ALEX, 1, When.past));
        assertEquals(1, service.submitMyUpdate(ALEX, earlier.checkinId(), progress(0, "After the meeting")).version());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unit15ClosureLocksChangesButAllowsExactRetries(boolean complete) {
        var checkin = schedule(1);
        var request = progress(0, "Finished design", blocker(null, "Awaiting review", Severity.LOW, null));
        var first = service.submitMyUpdate(ALEX, checkin.checkinId(), request);
        assertCode(FORBIDDEN, () -> close(service, ALEX, checkin, complete));
        var closed = close(service, LEAD, checkin, complete);
        assertEquals(checkin.version() + 1, closed.version());
        assertFalse(closed.acceptsUpdates());
        assertEquals(first, service.submitMyUpdate(ALEX, checkin.checkinId(), request));
        assertCode(INVALID_STATE, () -> service.submitMyUpdate(ALEX, checkin.checkinId(), progress(first.version(), "Changed")));
        assertCode(INVALID_STATE, () -> service.submitMyUpdate(JORDAN, checkin.checkinId(), progress(0, "New")));
        assertCode(INVALID_STATE, () -> service.update(LEAD, checkin.checkinId(),
                new UpdateCheckinRequest(NOW.plusSeconds(9_000), null, null, closed.version())));

        clock.set(NOW.plusSeconds(60));
        if (complete) {
            assertEquals(CheckinStatus.COMPLETED, closed.status());
            assertEquals(NOW, closed.completedAt());
            assertEquals(closed, close(service, LEAD, checkin, true));
            assertEquals(List.of("lead@usc.edu"), queuedRecipients("CHECKIN_COMPLETED"));
        } else {
            assertEquals(CheckinStatus.CANCELED, closed.status());
            assertNull(closed.completedAt());
            assertCode(INVALID_STATE, () -> close(service, LEAD, checkin, true));
            assertEquals(List.of("alex@usc.edu", "jordan@usc.edu", "lead@usc.edu"), queuedRecipients("CHECKIN_CANCELED"));
        }
    }

    @Test
    void unit16AndRegression11RescheduleClearsOnlyTheMeetingClaimsAndKeepsResponses() {
        var checkin = schedule(2);
        var other = schedule(3);
        var response = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "Initial"));
        new CheckinReminderJob(db, dao, notifications, clock).run();
        assertEquals(6, count("SELECT count(*) FROM checkin_reminders"));

        var edited = service.update(LEAD, checkin.checkinId(),
                new UpdateCheckinRequest(checkin.scheduledAt(), "Changed description", "Room 5", checkin.version()));
        assertEquals(2, edited.version());
        assertEquals(6, count("SELECT count(*) FROM checkin_reminders"));
        var stale = assertCode(VERSION_CONFLICT, () -> service.update(LEAD, checkin.checkinId(),
                new UpdateCheckinRequest(null, "Stale description", null, checkin.version())));
        assertEquals(edited, stale.current());

        var rescheduled = service.update(LEAD, checkin.checkinId(),
                new UpdateCheckinRequest(NOW.plus(Duration.ofHours(4)), null, "", edited.version()));
        assertEquals(3, rescheduled.version());
        assertEquals("Changed description", rescheduled.description());
        assertNull(rescheduled.location());
        assertEquals(0, reminderCount(checkin.checkinId()));
        assertEquals(3, reminderCount(other.checkinId()));
        assertEquals(List.of(response), service.listUpdates(JORDAN, checkin.checkinId()));
        assertEquals(6, queuedRecipients("CHECKIN_UPDATED").size());
        assertEquals(3, count("SELECT count(*) FROM email_queue WHERE subject = 'CHECKIN_UPDATED' AND body_text LIKE 'Check-in rescheduled%'"));
    }

    @Test
    void unit17ReplacesOmittedBlockersAndKeepsOneProgressRow() {
        var checkin = schedule(1);
        var first = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "First",
                blocker(null, "Review", Severity.LOW, null), blocker(null, "API", Severity.HIGH, 2L)));
        clock.set(NOW.plusSeconds(60));
        var edited = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(first.version(), "Edited",
                blocker(null, "New blocker", Severity.MEDIUM, null)));
        assertEquals(first.updateId(), edited.updateId());
        assertEquals(2, edited.version());
        assertEquals("Edited", edited.completedWork());
        assertEquals(clock.instant(), edited.submittedAt());
        assertEquals(1, edited.blockers().size());
        assertEquals("New blocker", edited.blockers().getFirst().description());
        assertFalse(first.blockers().stream().anyMatch(b -> b.blockerId() == edited.blockers().getFirst().blockerId()));
        assertEquals(List.of(edited), service.listUpdates(LEAD, checkin.checkinId()));
    }

    @Test
    void unit18SecondBlockerWriteRollsBackANewOrExistingResponse() {
        var checkin = schedule(1);
        var original = service.submitMyUpdate(ALEX, checkin.checkinId(),
                progress(0, "Old text", blocker(null, "Old blocker", Severity.HIGH, null)));
        var failingService = serviceWith(new CheckinDao() {
            @Override
            public void insertBlocker(Connection c, long updateId, BlockerInput blocker) throws SQLException {
                // The second insert violates the foreign key after the first insert has succeeded.
                super.insertBlocker(c, blocker.description().equals("Fails in SQL") ? -1 : updateId, blocker);
            }
        }, notifications);
        var first = blocker(null, "Valid first blocker", Severity.LOW, null);
        var second = blocker(null, "Fails in SQL", Severity.HIGH, null);
        clock.set(NOW.plusSeconds(60));

        for (var caller : List.of(ALEX, JORDAN)) {
            int version = caller == ALEX ? original.version() : 0;
            var error = assertThrows(IllegalStateException.class, () -> failingService.submitMyUpdate(caller,
                    checkin.checkinId(), progress(version, "Replacement", first, second)));
            assertEquals("23503", assertInstanceOf(SQLException.class, error.getCause()).getSQLState());
            assertEquals(List.of(original), service.listUpdates(LEAD, checkin.checkinId()));
            assertEquals(1, count("SELECT count(*) FROM checkin_blockers"));
        }
    }

    @Test
    void unit19AcceptsBoundaryLengthsAndRejectsForeignTaskLinks() {
        var checkin = schedule(1);
        var text = "🧭".repeat(2_000);
        var description = "x".repeat(500);
        var accepted = service.submitMyUpdate(ALEX, checkin.checkinId(),
                new SubmitUpdateRequest(0, text, text, List.of(blocker(null, description, Severity.LOW, null))));
        assertEquals(text, accepted.completedWork());
        assertEquals(text, accepted.upcomingTasks());
        assertEquals(description, accepted.blockers().getFirst().description());
        assertNull(accepted.blockers().getFirst().taskId());

        for (long taskId : List.of(5L, 999L)) {
            var error = assertCode(VALIDATION_FAILED, () -> service.submitMyUpdate(ALEX, checkin.checkinId(),
                    progress(accepted.version(), "Changed", blocker(null, "Not in team", Severity.MEDIUM, taskId))));
            assertEquals(Map.of("blockers[0].taskId", "Choose a task from this check-in's team."), error.fields());
        }
        assertEquals(List.of(accepted), service.listUpdates(LEAD, checkin.checkinId()));
    }

    @Test
    void unit20ResolvesOnlyTheRightBlockerByAuthorOrLeadEvenAfterClosure() {
        var checkin = schedule(1);
        var other = schedule(2);
        var saved = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "Design",
                blocker(null, "Review", Severity.LOW, null), blocker(null, "API", Severity.HIGH, null)));
        long firstId = saved.blockers().getFirst().blockerId();
        long secondId = saved.blockers().getLast().blockerId();
        assertCode(FORBIDDEN, () -> service.resolveBlocker(JORDAN, checkin.checkinId(), firstId));
        assertCode(FORBIDDEN, () -> service.resolveBlocker(OUTSIDER, checkin.checkinId(), firstId));
        assertCode(NOT_FOUND, () -> service.resolveBlocker(LEAD, other.checkinId(), firstId));
        close(service, LEAD, checkin, true);
        var first = service.resolveBlocker(ALEX, checkin.checkinId(), firstId);
        assertTrue(first.isResolved());
        assertEquals(ALEX.userId(), first.resolvedBy());
        assertEquals(NOW, first.resolvedAt());

        clock.set(NOW.plusSeconds(30));
        assertEquals(first, service.resolveBlocker(LEAD, checkin.checkinId(), firstId));
        var second = service.resolveBlocker(LEAD, checkin.checkinId(), secondId);
        assertEquals(LEAD.userId(), second.resolvedBy());
        assertEquals(clock.instant(), second.resolvedAt());
        assertEquals(saved.version(), service.listUpdates(LEAD, checkin.checkinId()).getFirst().version());
    }

    @Test
    void regression5SavesTheSubmittedProgressAndBlockerWithoutChangingTheTask() {
        var checkin = schedule(1);
        var taskBefore = taskSnapshot(2);
        var saved = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "Dashboard layout is complete",
                blocker(null, "Waiting for API endpoint", Severity.MEDIUM, 2L)));
        assertEquals(ALEX.userId(), saved.userId());
        assertEquals("Dashboard layout is complete", saved.completedWork());
        assertEquals("Waiting for API endpoint", saved.blockers().getFirst().description());
        assertEquals(2L, saved.blockers().getFirst().taskId());
        assertEquals(List.of(saved), service.listUpdates(JORDAN, checkin.checkinId()));
        assertEquals(taskBefore, taskSnapshot(2));
        assertEquals(5, count("SELECT count(*) FROM tasks"));
    }

    @Test
    void preservesResolutionOnTextEditsAndReopensChangedBlockers() {
        var checkin = schedule(1);
        var first = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "First",
                blocker(null, "API", Severity.HIGH, 2L)));
        var resolved = service.resolveBlocker(LEAD, checkin.checkinId(), first.blockers().getFirst().blockerId());
        var unchanged = blocker(resolved.blockerId(), "API", Severity.HIGH, 2L);
        var edited = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(first.version(), "Updated", unchanged));
        assertEquals(List.of(resolved), edited.blockers());

        var duplicate = progress(edited.version(), "Updated", unchanged, unchanged);
        assertTrue(assertCode(VALIDATION_FAILED, () -> service.submitMyUpdate(ALEX, checkin.checkinId(), duplicate))
                .fields().containsKey("blockers[1].blockerId"));
        var foreign = service.submitMyUpdate(JORDAN, checkin.checkinId(),
                progress(0, "Other member", blocker(null, "Other blocker", Severity.LOW, null))).blockers().getFirst();
        var otherId = progress(edited.version(), "Updated", blocker(foreign.blockerId(), "API", Severity.HIGH, 2L));
        assertTrue(assertCode(VALIDATION_FAILED, () -> service.submitMyUpdate(ALEX, checkin.checkinId(), otherId))
                .fields().containsKey("blockers[0].blockerId"));

        var changed = blocker(resolved.blockerId(), "API is still down", Severity.HIGH, 2L);
        var reopened = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(edited.version(), "Updated", changed))
                .blockers().getFirst();
        assertEquals(resolved.blockerId(), reopened.blockerId());
        assertFalse(reopened.isResolved());
        assertNull(reopened.resolvedBy());
    }

    @Test
    void replacingAResolvedBlockerWithIdenticalDetailsCreatesAnUnresolvedBlocker() {
        var checkin = schedule(1);
        var input = blocker(null, "Waiting for review", Severity.LOW, null);
        var submitted = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "First draft", input));
        var resolved = service.resolveBlocker(ALEX, checkin.checkinId(), submitted.blockers().getFirst().blockerId());

        var request = progress(submitted.version(), submitted.completedWork(), input);
        var replacement = service.submitMyUpdate(ALEX, checkin.checkinId(), request);
        var newBlocker = replacement.blockers().getFirst();
        assertEquals(submitted.version() + 1, replacement.version());
        assertNotEquals(resolved.blockerId(), newBlocker.blockerId());
        assertFalse(newBlocker.isResolved());
        assertEquals(replacement, service.submitMyUpdate(ALEX, checkin.checkinId(), request));
    }

    @Test
    void staleLaptopRetryCannotOverwriteNewerPhoneSave() {
        var checkin = schedule(1);
        var laptop = progress(0, "Laptop", blocker(null, "Review", Severity.LOW, null));
        var saved = service.submitMyUpdate(ALEX, checkin.checkinId(), laptop);
        var blocker = saved.blockers().getFirst();
        var resolved = service.resolveBlocker(ALEX, checkin.checkinId(), blocker.blockerId());
        clock.set(NOW.plusSeconds(60));
        var exactRetry = service.submitMyUpdate(ALEX, checkin.checkinId(), laptop);
        assertEquals(saved.version(), exactRetry.version());
        assertEquals(saved.submittedAt(), exactRetry.submittedAt());
        assertEquals(List.of(resolved), exactRetry.blockers());

        var phone = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(saved.version(), "Newer phone edit",
                blocker(blocker.blockerId(), "Review", Severity.LOW, null)));
        assertEquals(phone, assertCode(VERSION_CONFLICT,
                () -> service.submitMyUpdate(ALEX, checkin.checkinId(), laptop)).current());
        assertEquals(List.of(phone), service.listUpdates(LEAD, checkin.checkinId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void differentMembersCanWriteConcurrentlyAndIdenticalFirstSavesDeduplicate(boolean identical) throws Exception {
        var checkin = schedule(1);
        var barrier = new CyclicBarrier(2);
        var racing = serviceWith(new CheckinDao() {
            @Override
            public Optional<Long> insertUpdate(Connection c, long checkinId, long userId, String completedWork,
                                               String upcomingTasks, Instant now) throws SQLException {
                meet(barrier);
                return super.insertUpdate(c, checkinId, userId, completedWork, upcomingTasks, now);
            }
        }, notifications);
        var request = progress(0, "Finished", blocker(null, "API", Severity.HIGH, null),
                blocker(null, "API", Severity.HIGH, null));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var alex = executor.submit(() -> racing.submitMyUpdate(ALEX, checkin.checkinId(), request));
            var second = executor.submit(() -> racing.submitMyUpdate(identical ? ALEX : JORDAN, checkin.checkinId(), request));
            var firstResult = alex.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstResult.blockers().getFirst().blockerId(), firstResult.blockers().getLast().blockerId());
            assertEquals(1, firstResult.version());
            if (identical) assertEquals(firstResult, secondResult);
            else assertNotEquals(firstResult.updateId(), secondResult.updateId());
            assertEquals(identical ? 1 : 2, service.listUpdates(LEAD, checkin.checkinId()).size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void changedSubmissionThatWinsTheLockPersistsBeforeClosure(boolean complete) throws Exception {
        var checkin = schedule(1);
        var first = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "Earlier"));
        var hasWriteLock = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var racing = serviceWith(new CheckinDao() {
            @Override
            public boolean updateProgress(Connection c, long updateId, int expectedVersion, String completedWork,
                                          String upcomingTasks, Instant now) throws SQLException {
                boolean updated = super.updateProgress(c, updateId, expectedVersion, completedWork, upcomingTasks, now);
                hasWriteLock.countDown();
                await(release);
                return updated;
            }
        }, notifications);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var save = executor.submit(() -> racing.submitMyUpdate(ALEX, checkin.checkinId(),
                        progress(first.version(), "Accepted change")));
                await(hasWriteLock);
                var closing = executor.submit(() -> close(service, LEAD, checkin, complete));
                assertThrows(TimeoutException.class, () -> closing.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                var saved = save.get(10, TimeUnit.SECONDS);
                assertFalse(closing.get(10, TimeUnit.SECONDS).acceptsUpdates());
                assertEquals("Accepted change", saved.completedWork());
                assertEquals(List.of(saved), service.listUpdates(LEAD, checkin.checkinId()));
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void closureThatWinsTheLockRejectsNewAndChangedSubmissions(boolean complete) throws Exception {
        var checkin = schedule(1);
        var first = service.submitMyUpdate(ALEX, checkin.checkinId(), progress(0, "Earlier"));
        var hasWriteLock = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        NotificationService waiting = mock(NotificationService.class);
        doAnswer(call -> {
            hasWriteLock.countDown();
            await(release);
            queueNotifications(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(waiting).notify(any(), any());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var closing = executor.submit(() -> close(serviceWith(dao, waiting), LEAD, checkin, complete));
                await(hasWriteLock);
                var change = executor.submit(() -> assertCode(INVALID_STATE, () ->
                        service.submitMyUpdate(ALEX, checkin.checkinId(), progress(first.version(), "Changed"))));
                var create = executor.submit(() -> assertCode(INVALID_STATE, () ->
                        service.submitMyUpdate(JORDAN, checkin.checkinId(), progress(0, "New"))));
                assertThrows(TimeoutException.class, () -> change.get(150, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> create.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                assertFalse(closing.get(10, TimeUnit.SECONDS).acceptsUpdates());
                change.get(10, TimeUnit.SECONDS);
                create.get(10, TimeUnit.SECONDS);
                assertEquals(List.of(first), service.listUpdates(LEAD, checkin.checkinId()));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void sharedReadsFilterResolvedBlockersAndScopeActivityToMemberAndTeam() {
        var first = schedule(1);
        var second = schedule(2);
        var otherTeam = service.schedule(OUTSIDER, new ScheduleCheckinRequest(2, NOW.plusSeconds(3_600), null, null));
        var updates = service.submitMyUpdate(ALEX, first.checkinId(), progress(0, "Design",
                blocker(null, "Review", Severity.LOW, 2L), blocker(null, "API", Severity.HIGH, 2L),
                blocker(null, "Different task", Severity.MEDIUM, 3L)));
        service.resolveBlocker(LEAD, first.checkinId(), updates.blockers().getFirst().blockerId());

        clock.set(NOW.plusSeconds(60));
        service.submitMyUpdate(ALEX, second.checkinId(), progress(0, "More work"));
        clock.set(NOW.plusSeconds(90));
        service.submitMyUpdate(JORDAN, second.checkinId(), progress(0, "Jordan work"));
        clock.set(NOW.plusSeconds(120));
        var alexInBothTeams = user(3, 2, TeamRole.MEMBER);
        execute("INSERT INTO team_memberships (team_id, user_id, role) VALUES (2, 3, 'MEMBER')");
        service.submitMyUpdate(alexInBothTeams, otherTeam.checkinId(), progress(0, "Other team work"));

        db.withConnection(c -> {
            assertEquals(List.of(updates.blockers().get(1)), dao.findUnresolvedBlockersForTask(c, 2));
            assertEquals(List.of(updates.blockers().get(2)), dao.findUnresolvedBlockersForTask(c, 3));
            assertEquals(List.of(), dao.findUnresolvedBlockersForTask(c, 5));
            assertEquals(Optional.of(NOW.plusSeconds(60)), dao.findLastUpdateTime(c, 1, ALEX.userId()));
            assertEquals(Optional.of(NOW.plusSeconds(90)), dao.findLastUpdateTime(c, 1, JORDAN.userId()));
            assertEquals(Optional.of(NOW.plusSeconds(120)), dao.findLastUpdateTime(c, 2, ALEX.userId()));
            assertTrue(dao.findLastUpdateTime(c, 1, LEAD.userId()).isEmpty());
            return null;
        });
    }

    @Test
    void remindersUseMemberLeadTimesAndPreferences() {
        execute("""
                INSERT INTO notification_preferences (user_id, email_enabled, in_app_enabled, early_notif_time) VALUES
                (2, false, false, interval '24 hours'),
                (3, true, false, interval '2 hours'),
                (4, false, true, NULL)
                """);
        var checkin = schedule(6);
        var job = new CheckinReminderJob(db, dao, notifications, clock);
        job.run();
        assertEquals(1, reminderCount(checkin.checkinId()));
        assertEquals(1, count("SELECT count(*) FROM notifications WHERE notif_type = 'CHECKIN_REMINDER' AND user_id = 4"));
        assertEquals(List.of(), queuedRecipients("CHECKIN_REMINDER"));

        clock.set(checkin.scheduledAt().minus(Duration.ofHours(2)));
        job.run();
        assertEquals(2, reminderCount(checkin.checkinId()));
        assertEquals(List.of("alex@usc.edu"), queuedRecipients("CHECKIN_REMINDER"));
    }

    @Test
    void rechecksScheduleStartAndRecipientEligibilityBeforeClaiming() {
        var moving = schedule(7);
        var starting = schedule(1);
        var stillDue = schedule(8);
        CheckinDao changing = new CheckinDao() {
            @Override
            public List<Long> findCheckinsWithDueReminders(Connection c, Instant now, Duration defaultLead) throws SQLException {
                var candidates = super.findCheckinsWithDueReminders(c, now, defaultLead);
                assertEquals(List.of(moving.checkinId(), starting.checkinId(), stillDue.checkinId()), candidates);
                clock.set(starting.scheduledAt());
                service.update(LEAD, moving.checkinId(), new UpdateCheckinRequest(NOW.plus(Duration.ofDays(3)),
                        null, null, moving.version()));
                execute("""
                        DELETE FROM team_memberships WHERE team_id = 1 AND user_id = 4;
                        INSERT INTO notification_preferences (user_id, email_enabled, in_app_enabled) VALUES (3, false, false)
                        """);
                return candidates;
            }
        };
        new CheckinReminderJob(db, changing, notifications, clock).run();
        assertEquals(0, reminderCount(moving.checkinId()));
        assertEquals(0, reminderCount(starting.checkinId()));
        assertEquals(1, reminderCount(stillDue.checkinId()));
        assertEquals(List.of("lead@usc.edu"), queuedRecipients("CHECKIN_REMINDER"));
    }

    @Test
    void notificationFailureRollsBackClaimsAndReschedule() {
        var checkin = schedule(1);
        NotificationService failing = mock(NotificationService.class);
        doAnswer(call -> {
            queueNotifications(call.getArgument(0), call.getArgument(1));
            throw new IllegalStateException("Test queue failure");
        }).when(failing).notify(any(), any());
        assertThrows(IllegalStateException.class, () -> new CheckinReminderJob(db, dao, failing, clock).run());
        assertEquals(0, reminderCount(checkin.checkinId()));
        assertEquals(List.of(), queuedRecipients("CHECKIN_REMINDER"));
        new CheckinReminderJob(db, dao, notifications, clock).run();
        assertEquals(3, reminderCount(checkin.checkinId()));
        assertEquals(3, queuedRecipients("CHECKIN_REMINDER").size());

        assertThrows(IllegalStateException.class, () -> serviceWith(dao, failing).update(LEAD, checkin.checkinId(),
                new UpdateCheckinRequest(NOW.plus(Duration.ofHours(3)), null, null, checkin.version())));
        assertEquals(checkin, service.get(LEAD, checkin.checkinId()));
        assertEquals(3, reminderCount(checkin.checkinId()));
        assertEquals(List.of(), queuedRecipients("CHECKIN_UPDATED"));
    }

    @Test
    void stress7OverlappingRunsQueueEachOfTenThousandRemindersOncePerSchedule() throws Exception {
        addMembers(9_997);
        var checkin = schedule(6);
        var barrier = new CyclicBarrier(2);
        CheckinDao overlapping = new CheckinDao() {
            @Override
            public List<Long> findCheckinsWithDueReminders(Connection c, Instant now, Duration defaultLead) throws SQLException {
                var candidates = super.findCheckinsWithDueReminders(c, now, defaultLead);
                meet(barrier);
                return candidates;
            }
        };
        var job = new CheckinReminderJob(db, overlapping, notifications, clock);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(job::run);
            var second = executor.submit(job::run);
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        }
        var firstRun = queuedRecipients("CHECKIN_REMINDER");
        assertEquals(10_000, firstRun.size());
        assertEquals(10_000, new HashSet<>(firstRun).size());
        assertEquals(10_000, reminderCount(checkin.checkinId()));

        service.update(LEAD, checkin.checkinId(),
                new UpdateCheckinRequest(checkin.scheduledAt().plusSeconds(3_600), null, null, checkin.version()));
        assertEquals(0, reminderCount(checkin.checkinId()));
        new CheckinReminderJob(db, dao, notifications, clock).run();
        assertEquals(10_000, reminderCount(checkin.checkinId()));
        assertEquals(20_000, queuedRecipients("CHECKIN_REMINDER").size());
        assertEquals(10_000, count("""
                SELECT count(*) FROM (SELECT to_email FROM email_queue
                    WHERE subject = 'CHECKIN_REMINDER' GROUP BY to_email HAVING count(*) = 2) grouped
                """));
    }

    @Test
    void stress16KeepsTwoThousandResponsesWithTwoHundredRetries() throws Exception {
        var checkin = schedule(1);
        var users = addMembers(2_000);
        List<Callable<Submission>> calls = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            long userId = users.get(i);
            var caller = user(userId, 1, TeamRole.MEMBER);
            var request = progress(0, "Work by " + userId, blocker(null, "Waiting for review", Severity.LOW, null));
            Callable<Submission> call = () -> {
                long start = System.nanoTime();
                var saved = service.submitMyUpdate(caller, checkin.checkinId(), request);
                return new Submission(userId, saved, Duration.ofNanos(System.nanoTime() - start));
            };
            calls.add(call);
            if (i % 10 == 0) calls.add(call);
        }
        long start = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(64)) {
            var results = executor.invokeAll(calls, 60, TimeUnit.SECONDS);
            long errors = 0;
            long timeouts = 0;
            for (var future : results) {
                if (future.isCancelled()) {
                    errors++;
                    timeouts++;
                    continue;
                }
                Submission result;
                try {
                    result = future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    errors++;
                    continue;
                }
                if (result.duration().compareTo(Duration.ofSeconds(15)) >= 0) timeouts++;
                assertEquals(result.userId(), result.update().userId());
                assertEquals(1, result.update().version());
                assertEquals("Work by " + result.userId(), result.update().completedWork());
                assertEquals(1, result.update().blockers().size());
            }
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            System.out.printf("Stress 16 | %,d submissions and retries in %.2fs (%.0f/s), %d errors, %d past 15s%n",
                    results.size(), seconds, results.size() / seconds, errors, timeouts);
            assertTrue(errors < results.size() * 0.01);
            assertTrue(seconds < 60);
        }

        var saved = service.listUpdates(LEAD, checkin.checkinId());
        assertEquals(2_000, saved.size());
        assertEquals(new HashSet<>(users), new HashSet<>(saved.stream().map(CheckinUpdate::userId).toList()));
        assertTrue(saved.stream().allMatch(update -> update.version() == 1
                && update.completedWork().equals("Work by " + update.userId()) && update.blockers().size() == 1));
    }

    private CheckinService serviceWith(CheckinDao queries, NotificationService queue) {
        return new CheckinService(db, queries, permissions, queue, new TaskDao(), new ProjectDao(), clock);
    }

    private Checkin schedule(int hours) {
        return service.schedule(LEAD, new ScheduleCheckinRequest(1, NOW.plus(Duration.ofHours(hours)),
                "Team check-in", "Room 204"));
    }

    private static Checkin close(CheckinService service, AuthContext caller, Checkin checkin, boolean complete) {
        var req = new VersionRequest(checkin.version());
        return complete ? service.complete(caller, checkin.checkinId(), req)
                : service.cancel(caller, checkin.checkinId(), req);
    }

    private static AuthContext user(long id, long teamId, TeamRole role) {
        return new AuthContext(id, "user-" + id + "@example.test", "User " + id, SystemRole.USER, Map.of(teamId, role));
    }

    private static SubmitUpdateRequest progress(int version, String content, BlockerInput... blockers) {
        return new SubmitUpdateRequest(version, content, "Next steps", List.of(blockers));
    }

    private static BlockerInput blocker(Long id, String description, Severity severity, Long taskId) {
        return new BlockerInput(id, description, severity, taskId);
    }

    private static ApiException assertCode(ErrorCode expected, Executable action) {
        ApiException result = assertThrows(ApiException.class, action);
        assertEquals(expected, result.code());
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for the other transaction");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void meet(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private long reminderCount(long checkinId) {
        return count("SELECT count(*) FROM checkin_reminders WHERE checkin_id = " + checkinId);
    }

    private long count(String sql) {
        return db.withConnection(c -> {
            try (var statement = c.createStatement(); var rows = statement.executeQuery(sql)) {
                rows.next();
                return rows.getLong(1);
            }
        });
    }

    private List<String> queuedRecipients(String type) {
        return db.withConnection(c -> {
            try (var ps = c.prepareStatement("SELECT to_email FROM email_queue WHERE subject = ? ORDER BY to_email")) {
                ps.setString(1, type);
                try (var rows = ps.executeQuery()) {
                    List<String> emails = new ArrayList<>();
                    while (rows.next()) emails.add(rows.getString(1));
                    return emails;
                }
            }
        });
    }

    private List<String> taskSnapshot(long taskId) {
        return db.withConnection(c -> {
            try (var ps = c.prepareStatement("""
                    SELECT status, effort, due_date, ARRAY(SELECT user_id FROM task_assignees
                        WHERE task_id = t.task_id ORDER BY user_id) AS assignees
                    FROM tasks t WHERE task_id = ?
                    """)) {
                ps.setLong(1, taskId);
                try (var rows = ps.executeQuery()) {
                    assertTrue(rows.next());
                    return List.of(rows.getString("status"), rows.getString("effort"),
                            rows.getString("due_date"), rows.getString("assignees"));
                }
            }
        });
    }

    private List<Long> addMembers(int count) {
        return db.withConnection(c -> {
            try (var ps = c.prepareStatement("""
                    WITH new_users AS (
                        INSERT INTO users (email, display_name, activated)
                        SELECT 'member-' || n || '@example.test', 'Member ' || n, true FROM generate_series(1, ?) n
                        RETURNING user_id
                    )
                    INSERT INTO team_memberships (team_id, user_id, role)
                    SELECT 1, user_id, 'MEMBER' FROM new_users RETURNING user_id
                    """)) {
                ps.setInt(1, count);
                try (var rows = ps.executeQuery()) {
                    List<Long> ids = new ArrayList<>();
                    while (rows.next()) ids.add(rows.getLong(1));
                    return ids;
                }
            }
        });
    }

    private void execute(String sql) {
        db.withConnection(c -> {
            try (var statement = c.createStatement()) {
                statement.execute(sql);
                return null;
            }
        });
    }

    /** Test double for the shared hook. These inserts use the check-in transaction's connection. */
    private static void queueNotifications(Connection c, NotificationRequest request) throws SQLException {
        var recipients = c.createArrayOf("int8", request.recipientUserIds().toArray(Long[]::new));
        try (var app = c.prepareStatement("""
                INSERT INTO notifications (user_id, notif_type, message)
                SELECT u.user_id, ?, ? FROM users u LEFT JOIN notification_preferences p USING (user_id)
                WHERE u.user_id = ANY (?) AND COALESCE(p.in_app_enabled, true)
                """); var email = c.prepareStatement("""
                INSERT INTO email_queue (to_email, subject, body_text)
                SELECT u.email, ?, ? FROM users u LEFT JOIN notification_preferences p USING (user_id)
                WHERE u.user_id = ANY (?) AND COALESCE(p.email_enabled, true)
                """)) {
            app.setString(1, request.type().name());
            app.setString(2, request.message());
            app.setArray(3, recipients);
            app.executeUpdate();
            if (request.alsoEmail()) {
                email.setString(1, request.type().name());
                email.setString(2, request.message());
                email.setArray(3, recipients);
                email.executeUpdate();
            }
        } finally {
            recipients.free();
        }
    }

    private record Submission(long userId, CheckinUpdate update, Duration duration) {}

    private static final class TestClock extends Clock {
        private volatile Instant now = NOW;

        void set(Instant now) { this.now = now; }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
