package edu.usc.compass.auth;

import edu.usc.compass.auth.AuthDtos.LoginRequest;
import edu.usc.compass.auth.AuthDtos.UpdateProfileRequest;
import edu.usc.compass.auth.VerificationCodeService.CodeCheck;
import edu.usc.compass.auth.dao.LoginLockoutDao;
import edu.usc.compass.auth.dao.LoginLockoutDao.LockoutRow;
import edu.usc.compass.auth.dao.SessionDao;
import edu.usc.compass.auth.dao.SessionDao.SessionRow;
import edu.usc.compass.auth.dao.UserDao;
import edu.usc.compass.auth.dao.VerificationCodeDao;
import edu.usc.compass.auth.dao.VerificationCodeDao.CodeRow;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.AuthEnums.CodePurpose;
import edu.usc.compass.auth.model.AuthEnums.LockoutAction;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Hashing;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.db.SqlWork;
import edu.usc.compass.notifications.EmailQueue;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamRole;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Testing Plan cases for Accounts and Authentication. Owner: Gavin. */
class AuthTestPlan {

    private static final Instant T0 = Instant.parse("2026-12-01T18:00:00Z");
    private final Connection conn = mock(Connection.class);

    // ---- White box 1: 5th failure locks; 6th returns 429 without checking the password ----------------

    @Test
    void wb1_lockoutOnFifthFailure_sixthReturns429WithoutPasswordCheck() {
        LockoutRow row = new LockoutRow("a@usc.edu", LockoutAction.LOGIN, 0, T0, null);
        for (int i = 1; i <= 4; i++) {
            row = LoginLockoutService.next(row, T0.plusSeconds(i));
            assertNull(row.lockedUntil(), "not locked after failure " + i);
        }
        row = LoginLockoutService.next(row, T0.plusSeconds(5));
        assertEquals(5, row.failedCount());
        assertEquals(T0.plusSeconds(5).plus(Duration.ofMinutes(30)), row.lockedUntil());

        // 6th attempt: the login flow stops at checkNotLocked, so the password is never verified.
        LoginLockoutDao lockoutDao = mock(LoginLockoutDao.class);
        when(lockoutDao.findForUpdate(any(), eq("a@usc.edu"), eq(LockoutAction.LOGIN))).thenReturn(Optional.of(row));
        PasswordHasher hasher = mock(PasswordHasher.class);
        UserDao userDao = mock(UserDao.class);
        AuthService auth = authService(userDao, hasher, new LoginLockoutService(lockoutDao, fixed(T0.plusSeconds(6))));

        ApiException e = assertThrows(ApiException.class, () -> auth.login(new LoginRequest("A@usc.edu ", "Password1")));
        assertEquals(ErrorCode.LOCKED_OUT, e.code());
        assertEquals(429, e.httpStatus());
        assertTrue(e.retryAfterSeconds() > 29 * 60);
        verify(hasher, never()).verify(anyString(), anyString());
        verify(userDao, never()).findByEmail(any(), anyString());
    }

    @Test
    void wb1_wrongPasswordRecordsFailureAndReturns401() {
        LoginLockoutDao lockoutDao = mock(LoginLockoutDao.class);
        when(lockoutDao.findForUpdate(any(), anyString(), any())).thenReturn(Optional.empty());
        UserDao userDao = mock(UserDao.class);
        User user = user(7, SystemRole.USER, true);
        when(userDao.findByEmail(any(), eq("a@usc.edu"))).thenReturn(Optional.of(user));
        when(userDao.findPasswordHash(any(), eq(7L))).thenReturn(Optional.of(new PasswordHasher().hash("Right1234")));
        AuthService auth = authService(userDao, new PasswordHasher(), new LoginLockoutService(lockoutDao, fixed(T0)));

        ApiException e = assertThrows(ApiException.class, () -> auth.login(new LoginRequest("a@usc.edu", "Wrong1234")));
        assertEquals(ErrorCode.UNAUTHENTICATED, e.code());
        assertEquals(AuthService.BAD_CREDENTIALS, e.getMessage());
        verify(lockoutDao).upsert(any(), any());   // the failure was written inside the committed transaction
    }

    // ---- White box 2: the window resets after 30 minutes ----------------------------------------------

    @Test
    void wb2_lockoutWindowResetsAfter30Minutes() {
        LockoutRow fourFailures = new LockoutRow("a@usc.edu", LockoutAction.LOGIN, 4, T0, null);

        LockoutRow inside = LoginLockoutService.next(fourFailures, T0.plus(Duration.ofMinutes(29)));
        assertEquals(5, inside.failedCount());
        assertNotNull(inside.lockedUntil());

        LockoutRow after = LoginLockoutService.next(fourFailures, T0.plus(Duration.ofMinutes(30)));
        assertEquals(1, after.failedCount());
        assertEquals(T0.plus(Duration.ofMinutes(30)), after.windowStart());
        assertNull(after.lockedUntil());
    }

    @Test
    void wb2_expiredLockNoLongerBlocks() {
        LoginLockoutDao dao = mock(LoginLockoutDao.class);
        when(dao.findForUpdate(any(), anyString(), any()))
                .thenReturn(Optional.of(new LockoutRow("a@usc.edu", LockoutAction.LOGIN, 5, T0, T0.plusSeconds(1800))));
        assertThrows(ApiException.class, () ->
                new LoginLockoutService(dao, fixed(T0.plusSeconds(1799))).checkNotLocked(conn, "a@usc.edu", LockoutAction.LOGIN));
        assertDoesNotThrow(() ->
                new LoginLockoutService(dao, fixed(T0.plusSeconds(1800))).checkNotLocked(conn, "a@usc.edu", LockoutAction.LOGIN));
    }

    // ---- White box 3: session expires after 30 minutes of inactivity ----------------------------------

    @Test
    void wb3_sessionExpiresAfter30MinutesInactivity() {
        String raw = "raw-session-id";
        String hash = Hashing.sha256Hex(raw);
        SessionDao dao = mock(SessionDao.class);
        when(dao.findByHash(any(), eq(hash))).thenReturn(Optional.of(new SessionRow(hash, 3, T0, false)));

        Instant active = T0.plus(Duration.ofMinutes(29)).plusSeconds(59);
        assertEquals(Optional.of(3L), new SessionManager(dao, fixed(active)).validateAndTouch(conn, raw));
        verify(dao).touch(conn, hash, active);

        Instant expired = T0.plus(Duration.ofMinutes(30));
        assertEquals(Optional.empty(), new SessionManager(dao, fixed(expired)).validateAndTouch(conn, raw));
        verify(dao, never()).touch(conn, hash, expired);
    }

    @Test
    void wb3_invalidatedOrUnknownSessionIsRejected() {
        SessionDao dao = mock(SessionDao.class);
        String hash = Hashing.sha256Hex("x");
        when(dao.findByHash(any(), eq(hash))).thenReturn(Optional.of(new SessionRow(hash, 3, T0, true)));
        SessionManager sm = new SessionManager(dao, fixed(T0));
        assertTrue(sm.validateAndTouch(conn, "x").isEmpty());
        assertTrue(sm.validateAndTouch(conn, "unknown").isEmpty());
        assertTrue(sm.validateAndTouch(conn, null).isEmpty());
    }

    // ---- White box 4: code consumed after five wrong attempts -----------------------------------------

    @Test
    void wb4_verificationCodeInvalidatedAfterFiveWrongAttempts() {
        FakeCodeDao dao = new FakeCodeDao();
        VerificationCodeService codes = new VerificationCodeService(dao, fixed(T0));
        String real = codes.issue(conn, 9, CodePurpose.ACTIVATION);
        String wrong = real.equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 4; i++) {
            assertEquals(CodeCheck.WRONG, codes.check(conn, 9, CodePurpose.ACTIVATION, wrong));
            assertNull(dao.row.consumedAt(), "still usable after " + i + " wrong attempts");
        }
        assertEquals(CodeCheck.WRONG, codes.check(conn, 9, CodePurpose.ACTIVATION, wrong));
        assertNotNull(dao.row.consumedAt(), "consumed after the 5th wrong attempt");

        // Even the right code no longer works: the user must resend.
        assertEquals(CodeCheck.MISSING, codes.check(conn, 9, CodePurpose.ACTIVATION, real));
    }

    @Test
    void wb4_correctCodeWorksOnceAndExpiresAfterOneHour() {
        FakeCodeDao dao = new FakeCodeDao();
        String code = new VerificationCodeService(dao, fixed(T0)).issue(conn, 9, CodePurpose.PASSWORD_RESET);
        assertEquals(CodeCheck.OK, new VerificationCodeService(dao, fixed(T0.plusSeconds(60)))
                .check(conn, 9, CodePurpose.PASSWORD_RESET, code));
        assertEquals(CodeCheck.MISSING, new VerificationCodeService(dao, fixed(T0.plusSeconds(61)))
                .check(conn, 9, CodePurpose.PASSWORD_RESET, code));

        String code2 = new VerificationCodeService(dao, fixed(T0)).issue(conn, 9, CodePurpose.PASSWORD_RESET);
        assertEquals(CodeCheck.EXPIRED, new VerificationCodeService(dao, fixed(T0.plus(Duration.ofHours(1))))
                .check(conn, 9, CodePurpose.PASSWORD_RESET, code2));
    }

    // ---- White box 5: AuthorizeTeamRequest role branches ----------------------------------------------

    @Test
    void wb5_authorizeTeamRequestRoleBranches() {
        PermissionService p = new PermissionService();
        AuthContext admin = ctx(1, SystemRole.SYSTEM_ADMIN, Map.of());
        AuthContext lead = ctx(2, SystemRole.USER, Map.of(10L, TeamRole.LEAD));
        AuthContext member = ctx(3, SystemRole.USER, Map.of(10L, TeamRole.MEMBER));
        AuthContext outsider = ctx(4, SystemRole.USER, Map.of(99L, TeamRole.LEAD));

        // System admin passes everything, including admin-only permissions.
        assertTrue(p.has(admin, Permission.MANAGE_TASKS, 10));
        assertTrue(p.has(admin, Permission.MANAGE_USERS, 10));
        // Lead passes lead and member permissions.
        assertTrue(p.has(lead, Permission.MANAGE_TASKS, 10));
        assertTrue(p.has(lead, Permission.VIEW_WORKSPACE, 10));
        // Member passes member permissions only.
        assertTrue(p.has(member, Permission.COMMENT_ON_TASKS, 10));
        assertFalse(p.has(member, Permission.MANAGE_TASKS, 10));
        assertThrows(ApiException.class, () -> p.require(member, Permission.DELETE_TEAM, 10));
        // A lead of another team gets nothing here.
        assertFalse(p.has(outsider, Permission.VIEW_WORKSPACE, 10));
        // MANAGE_USERS is system admin only, even for a lead.
        assertFalse(p.has(lead, Permission.MANAGE_USERS, 10));

        // Task status: assignee member, lead, or admin.
        assertDoesNotThrow(() -> p.requireAssigneeOrLead(member, 10, List.of(3L)));
        assertThrows(ApiException.class, () -> p.requireAssigneeOrLead(member, 10, List.of(5L)));
        assertDoesNotThrow(() -> p.requireAssigneeOrLead(lead, 10, List.of()));
        assertDoesNotThrow(() -> p.requireAssigneeOrLead(admin, 10, List.of()));
        assertThrows(ApiException.class, () -> p.requireAssigneeOrLead(outsider, 10, List.of(4L)));

        assertDoesNotThrow(() -> p.requireSystemAdmin(admin));
        ApiException e = assertThrows(ApiException.class, () -> p.requireSystemAdmin(lead));
        assertEquals(ErrorCode.FORBIDDEN, e.code());
    }

    // ---- Regression 1: profile change does not touch auth or memberships ------------------------------

    @Test
    void reg1_profileChangeDoesNotAffectAuthOrMemberships() {
        UserDao userDao = mock(UserDao.class);
        User before = user(3, SystemRole.USER, true);
        User after = new User(3, before.email(), "New Name", SystemRole.USER, true, before.createdAt(), 2);
        when(userDao.updateDisplayName(any(), eq(3L), eq("New Name"), eq(1))).thenReturn(true);
        when(userDao.findById(any(), eq(3L))).thenReturn(Optional.of(after));
        UserService users = new UserService(db(), userDao, new PermissionService());

        User result = users.updateProfile(ctx(3, SystemRole.USER, Map.of(10L, TeamRole.MEMBER)),
                new UpdateProfileRequest("  New Name  ", 1));

        assertEquals("New Name", result.displayName());
        assertEquals(SystemRole.USER, result.systemRole());
        assertTrue(result.activated());
        verify(userDao).updateDisplayName(any(), eq(3L), eq("New Name"), eq(1));
        verify(userDao).findById(any(), eq(3L));
        verifyNoMoreInteractions(userDao);   // no password, Google, role or activation writes
    }

    @Test
    void reg1_staleVersionReturnsConflictWithLatest() {
        UserDao userDao = mock(UserDao.class);
        User latest = user(3, SystemRole.USER, true);
        when(userDao.updateDisplayName(any(), anyLong(), anyString(), anyInt())).thenReturn(false);
        when(userDao.findById(any(), eq(3L))).thenReturn(Optional.of(latest));
        UserService users = new UserService(db(), userDao, new PermissionService());

        ApiException e = assertThrows(ApiException.class, () ->
                users.updateProfile(ctx(3, SystemRole.USER, Map.of()), new UpdateProfileRequest("X", 1)));
        assertEquals(ErrorCode.VERSION_CONFLICT, e.code());
        assertEquals(latest, e.current());
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private AuthService authService(UserDao userDao, PasswordHasher hasher, LoginLockoutService lockout) {
        SessionManager sessions = mock(SessionManager.class);
        when(sessions.createSession(any(), anyLong())).thenReturn("new-session");
        return new AuthService(db(), userDao, mock(MembershipDao.class), hasher, sessions, lockout,
                mock(VerificationCodeService.class), mock(GoogleTokenVerifier.class), mock(EmailQueue.class));
    }

    /** A Database whose transactions run the work directly on a mock connection. */
    @SuppressWarnings("unchecked")
    private Database db() {
        Database db = mock(Database.class);
        when(db.isConfigured()).thenReturn(true);
        when(db.inTransaction(any())).thenAnswer(inv -> ((SqlWork<Object>) inv.getArgument(0)).run(conn));
        when(db.withConnection(any())).thenAnswer(inv -> ((SqlWork<Object>) inv.getArgument(0)).run(conn));
        return db;
    }

    private static Clock fixed(Instant now) {
        return Clock.fixed(now, ZoneId.of("UTC"));
    }

    private static AuthContext ctx(long id, SystemRole role, Map<Long, TeamRole> teams) {
        return new AuthContext(id, "u" + id + "@usc.edu", "User " + id, role, teams);
    }

    private static User user(long id, SystemRole role, boolean activated) {
        return new User(id, "a@usc.edu", "User " + id, role, activated, T0.atOffset(ZoneOffset.UTC).toInstant(), 1);
    }

    /** In-memory stand-in for one verification_codes row per user+purpose. */
    private static final class FakeCodeDao extends VerificationCodeDao {
        CodeRow row;
        long nextId = 1;

        @Override
        public Optional<CodeRow> findActiveForUpdate(Connection c, long userId, CodePurpose purpose) {
            return row != null && row.consumedAt() == null && row.userId() == userId && row.purpose() == purpose
                    ? Optional.of(row) : Optional.empty();
        }

        @Override
        public void consumeActive(Connection c, long userId, CodePurpose purpose, Instant now) {
            if (row != null && row.consumedAt() == null) {
                markConsumed(c, row.codeId(), now);
            }
        }

        @Override
        public void insert(Connection c, long userId, String codeHash, CodePurpose purpose, Instant expiresAt) {
            row = new CodeRow(nextId++, userId, codeHash, purpose, expiresAt, 0, null);
        }

        @Override
        public void incrementAttempts(Connection c, long codeId) {
            row = new CodeRow(row.codeId(), row.userId(), row.codeHash(), row.purpose(), row.expiresAt(),
                    row.attemptsUsed() + 1, row.consumedAt());
        }

        @Override
        public void markConsumed(Connection c, long codeId, Instant now) {
            row = new CodeRow(row.codeId(), row.userId(), row.codeHash(), row.purpose(), row.expiresAt(),
                    row.attemptsUsed(), now);
        }
    }
}
