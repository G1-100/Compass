package edu.usc.compass.auth;

import edu.usc.compass.auth.AuthDtos.ActivateRequest;
import edu.usc.compass.auth.AuthDtos.GoogleLoginRequest;
import edu.usc.compass.auth.AuthDtos.LoginRequest;
import edu.usc.compass.auth.AuthDtos.LoginResult;
import edu.usc.compass.auth.AuthDtos.MeResponse;
import edu.usc.compass.auth.AuthDtos.PasswordResetConfirmRequest;
import edu.usc.compass.auth.AuthDtos.PasswordResetRequest;
import edu.usc.compass.auth.AuthDtos.RegisterRequest;
import edu.usc.compass.auth.AuthDtos.ResendCodeRequest;
import edu.usc.compass.auth.AuthDtos.TeamMembershipSummary;
import edu.usc.compass.auth.VerificationCodeService.CodeCheck;
import edu.usc.compass.auth.dao.UserDao;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.AuthEnums.CodePurpose;
import edu.usc.compass.auth.model.AuthEnums.GoogleIdentity;
import edu.usc.compass.auth.model.AuthEnums.LockoutAction;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Validator;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.EmailQueue;
import edu.usc.compass.notifications.EmailTemplates;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.model.TeamRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Account lifecycle and sign-in (Detailed Design 3.1.1, routes 3.5.10).
 * Owner: Gavin.
 *
 * Transaction rule used throughout: Database.inTransaction rolls back on ANY exception. Anything that must be
 * remembered after a failure (a failed login, a wrong code attempt) is committed first, and the error is thrown
 * after the transaction returns.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final String BAD_CREDENTIALS = "Incorrect email or password.";
    static final String NOT_ACTIVATED = "Activate your account first. We emailed you a 6-digit code.";

    private final Database db;
    private final UserDao userDao;
    private final MembershipDao membershipDao;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;
    private final LoginLockoutService lockoutService;
    private final VerificationCodeService codeService;
    private final GoogleTokenVerifier googleVerifier;
    private final EmailQueue emailQueue;

    public AuthService(Database db, UserDao userDao, MembershipDao membershipDao, PasswordHasher passwordHasher,
                       SessionManager sessionManager, LoginLockoutService lockoutService,
                       VerificationCodeService codeService, GoogleTokenVerifier googleVerifier, EmailQueue emailQueue) {
        this.db = db;
        this.userDao = userDao;
        this.membershipDao = membershipDao;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
        this.lockoutService = lockoutService;
        this.codeService = codeService;
        this.googleVerifier = googleVerifier;
        this.emailQueue = emailQueue;
    }

    /**
     * POST /api/auth/register. Validates email, password policy and display name (max 60), creates an
     * unactivated user, issues an ACTIVATION code and queues the code email. Duplicate email: 400 on "email".
     */
    public User register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        String displayName = req.displayName() == null ? null : req.displayName().trim();
        new Validator()
                .required("email", email)
                .email("email", email)
                .password("password", req.password())
                .lengthBetween("displayName", displayName, 1, 60)
                .throwIfInvalid();

        String hash = passwordHasher.hash(req.password());   // slow; done before opening the transaction
        return db.inTransaction(c -> {
            if (userDao.findByEmail(c, email).isPresent()) {
                throw ApiException.validation("email", "An account with this email already exists.");
            }
            long userId = userDao.insert(c, email, hash, null, displayName, false);
            String code = codeService.issue(c, userId, CodePurpose.ACTIVATION);
            emailQueue.enqueue(c, email, EmailTemplates.activationCode(code));
            return userDao.findById(c, userId).orElseThrow();
        });
    }

    /** POST /api/auth/activate. Verifies the code and sets activated = true. Already-active accounts succeed. */
    public void activate(ActivateRequest req) {
        String email = normalizeEmail(req.email());
        new Validator().required("email", email).required("code", req.code()).throwIfInvalid();

        CodeCheck result = db.inTransaction(c -> {
            Optional<User> user = userDao.findByEmail(c, email);
            if (user.isEmpty()) {
                return CodeCheck.MISSING;   // same answer as "no code", so the endpoint does not reveal accounts
            }
            if (user.get().activated()) {
                return CodeCheck.OK;
            }
            CodeCheck check = codeService.check(c, user.get().userId(), CodePurpose.ACTIVATION, req.code());
            if (check == CodeCheck.OK) {
                userDao.setActivated(c, user.get().userId());
            }
            return check;
        });
        throwIfFailed(result);
    }

    /**
     * POST /api/auth/resend-code. Every request counts toward the RESEND_CODE lockout (5 per 30 minutes).
     * Issues a new code (invalidating the old one) only for an existing, unactivated account, but always
     * returns normally so it does not reveal which emails have accounts.
     */
    public void resendCode(ResendCodeRequest req) {
        String email = normalizeEmail(req.email());
        new Validator().required("email", email).throwIfInvalid();
        db.inTransaction(c -> {
            lockoutService.checkNotLocked(c, email, LockoutAction.RESEND_CODE);
            lockoutService.recordFailure(c, email, LockoutAction.RESEND_CODE);
            Optional<User> user = userDao.findByEmail(c, email);
            if (user.isPresent() && !user.get().activated()) {
                String code = codeService.issue(c, user.get().userId(), CodePurpose.ACTIVATION);
                emailQueue.enqueue(c, user.get().email(), EmailTemplates.activationCode(code));
            }
            return null;
        });
    }

    /**
     * POST /api/auth/login. Order matters: checkNotLocked -> load user -> verify password.
     * Any failure: recordFailure (committed) and 401 "Incorrect email or password." (same for unknown emails).
     * Correct password on an unactivated account: 409 INVALID_STATE so the page can send the user to /activate.
     * Success: reset lockout, create a session, return LoginResult (controller sets the cookie).
     */
    public LoginResult login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        String password = req.password() == null ? "" : req.password();
        new Validator().required("email", email).required("password", req.password()).throwIfInvalid();

        LoginResult result = db.inTransaction(c -> {
            lockoutService.checkNotLocked(c, email, LockoutAction.LOGIN);   // 429 before any password work

            Optional<User> user = userDao.findByEmail(c, email);
            Optional<String> hash = user.flatMap(u -> userDao.findPasswordHash(c, u.userId()));
            // Always run one bcrypt check so unknown emails take as long as wrong passwords.
            boolean passwordOk = passwordHasher.verify(password, hash.orElse(PasswordHasher.DUMMY_HASH))
                    && hash.isPresent();

            if (!passwordOk) {
                lockoutService.recordFailure(c, email, LockoutAction.LOGIN);
                return null;   // commit the failure, then throw below
            }
            if (!user.get().activated()) {
                throw ApiException.invalidState(NOT_ACTIVATED);
            }
            lockoutService.reset(c, email, LockoutAction.LOGIN);
            return startSession(c, user.get());
        });
        if (result == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, BAD_CREDENTIALS);
        }
        return result;
    }

    /**
     * POST /api/auth/google. Verifies the Google ID token, finds the user by google_sub (or links by verified
     * email, or creates an activated account), then creates a session like login().
     */
    public LoginResult loginWithGoogle(GoogleLoginRequest req) {
        GoogleIdentity id = googleVerifier.verify(req.credential());   // network call: outside the transaction
        String email = normalizeEmail(id.email());

        return db.inTransaction(c -> {
            Optional<User> bySub = userDao.findByGoogleSub(c, id.sub());
            if (bySub.isPresent()) {
                return startSession(c, bySub.get());
            }
            if (!id.emailVerified() || email == null || email.isBlank()) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED,
                        "Your Google account's email is not verified, so it cannot be used to sign in.");
            }
            Optional<User> byEmail = userDao.findByEmail(c, email);
            long userId;
            if (byEmail.isPresent()) {
                // Google has verified this address, so linking it to the existing account is safe.
                userId = byEmail.get().userId();
                userDao.linkGoogleSub(c, userId, id.sub());
                userDao.setActivated(c, userId);
            } else {
                userId = userDao.insert(c, email, null, id.sub(), googleDisplayName(id, email), true);
            }
            return startSession(c, userDao.findById(c, userId).orElseThrow());
        });
    }

    /** POST /api/auth/logout. Invalidates the session (controller clears the cookie). */
    public void logout(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return;
        }
        db.inTransaction(c -> {
            sessionManager.invalidate(c, rawSessionId);
            return null;
        });
    }

    /** GET /api/auth/me. The user plus their team memberships. */
    public MeResponse me(AuthContext ctx) {
        return db.withConnection(c -> {
            User user = userDao.findById(c, ctx.userId()).orElseThrow(ApiException::unauthenticated);
            return new MeResponse(user, userDao.findTeamSummaries(c, user.userId()));
        });
    }

    /**
     * POST /api/auth/password-reset/request. Every request counts toward the PASSWORD_RESET lockout.
     * Always returns normally, whether or not the email exists, so it does not reveal accounts.
     */
    public void requestPasswordReset(PasswordResetRequest req) {
        String email = normalizeEmail(req.email());
        new Validator().required("email", email).throwIfInvalid();
        db.inTransaction(c -> {
            lockoutService.checkNotLocked(c, email, LockoutAction.PASSWORD_RESET);
            lockoutService.recordFailure(c, email, LockoutAction.PASSWORD_RESET);
            Optional<User> user = userDao.findByEmail(c, email);
            if (user.isPresent()) {
                String code = codeService.issue(c, user.get().userId(), CodePurpose.PASSWORD_RESET);
                emailQueue.enqueue(c, user.get().email(), EmailTemplates.passwordResetCode(code));
            }
            return null;
        });
    }

    /**
     * POST /api/auth/password-reset/confirm. Verifies the code, sets the new hash, invalidates all sessions,
     * clears the login lockout, and activates the account (the code proves the user owns the email).
     */
    public void confirmPasswordReset(PasswordResetConfirmRequest req) {
        String email = normalizeEmail(req.email());
        new Validator()
                .required("email", email)
                .required("code", req.code())
                .password("newPassword", req.newPassword())
                .throwIfInvalid();

        String newHash = passwordHasher.hash(req.newPassword());
        CodeCheck result = db.inTransaction(c -> {
            Optional<User> user = userDao.findByEmail(c, email);
            if (user.isEmpty()) {
                return CodeCheck.MISSING;
            }
            long userId = user.get().userId();
            CodeCheck check = codeService.check(c, userId, CodePurpose.PASSWORD_RESET, req.code());
            if (check == CodeCheck.OK) {
                userDao.updatePasswordHash(c, userId, newHash);
                userDao.setActivated(c, userId);
                sessionManager.invalidateAllForUser(c, userId);
                lockoutService.reset(c, email, LockoutAction.LOGIN);
            }
            return check;
        });
        throwIfFailed(result);
    }

    /**
     * Used by AuthFilter on every request. Validates and touches the session, loads the user (must be
     * activated) and their team roles, and builds an AuthContext.
     * Returns empty for missing/expired/invalid sessions. Never throws for a bad cookie.
     */
    public Optional<AuthContext> authenticate(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank() || !db.isConfigured()) {
            return Optional.empty();
        }
        try {
            return db.inTransaction(c -> {
                Optional<Long> userId = sessionManager.validateAndTouch(c, rawSessionId);
                if (userId.isEmpty()) {
                    return Optional.<AuthContext>empty();
                }
                Optional<User> user = userDao.findById(c, userId.get());
                if (user.isEmpty() || !user.get().activated()) {
                    return Optional.<AuthContext>empty();
                }
                User u = user.get();
                return Optional.of(new AuthContext(u.userId(), u.email(), u.displayName(), u.systemRole(),
                        teamRoles(c, u.userId())));
            });
        } catch (RuntimeException e) {
            log.warn("Session lookup failed; treating the request as signed out: {}", e.toString());
            return Optional.empty();
        }
    }

    // ---- helpers ---------------------------------------------------------

    private LoginResult startSession(Connection c, User user) {
        String raw = sessionManager.createSession(c, user.userId());
        return new LoginResult(raw, new MeResponse(user, userDao.findTeamSummaries(c, user.userId())));
    }

    /**
     * Team roles for the AuthContext. Uses Solomon's MembershipDao.findRolesForUser (the shared query);
     * until it is implemented, falls back to the same data from UserDao.findTeamSummaries so permission
     * checks already work.
     */
    private Map<Long, TeamRole> teamRoles(Connection c, long userId) throws SQLException {
        try {
            return Map.copyOf(membershipDao.findRolesForUser(c, userId));
        } catch (ApiException e) {
            if (e.code() != ErrorCode.NOT_IMPLEMENTED) {
                throw e;
            }
            Map<Long, TeamRole> roles = new HashMap<>();
            for (TeamMembershipSummary s : userDao.findTeamSummaries(c, userId)) {
                roles.put(s.teamId(), s.role());
            }
            return Map.copyOf(roles);
        }
    }

    private static void throwIfFailed(CodeCheck result) {
        if (result != CodeCheck.OK) {
            throw result.toException();
        }
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String googleDisplayName(GoogleIdentity id, String email) {
        String name = id.name() == null || id.name().isBlank() ? email.substring(0, email.indexOf('@')) : id.name().trim();
        int[] cps = name.codePoints().limit(60).toArray();   // display_name is max 60 characters
        return new String(cps, 0, cps.length);
    }
}
