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
import edu.usc.compass.auth.dao.UserDao;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.EmailQueue;
import edu.usc.compass.teams.dao.MembershipDao;

import java.util.Optional;

/**
 * Account lifecycle and sign-in (Detailed Design 3.1.1, routes 3.5.10).
 * Owner: Gavin.
 */
public class AuthService {

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
     * unactivated user, issues an ACTIVATION code and queues the code email (EmailTemplates.activationCode).
     * Duplicate email: validation("email", ...).
     */
    public User register(RegisterRequest req) {
        throw Todo.of("Gavin", "AuthService.register");
    }

    /** POST /api/auth/activate. Verifies the code and sets activated = true. */
    public void activate(ActivateRequest req) {
        throw Todo.of("Gavin", "AuthService.activate");
    }

    /** POST /api/auth/resend-code. Lockout applies (RESEND_CODE). Issues a new code, invalidating the old one. */
    public void resendCode(ResendCodeRequest req) {
        throw Todo.of("Gavin", "AuthService.resendCode");
    }

    /**
     * POST /api/auth/login. Order matters: checkNotLocked -> load user -> verify password.
     * Any failure: recordFailure and throw 401 "Incorrect email or password." (same for unknown emails).
     * Success: reset lockout, create a session, return LoginResult (controller sets the cookie).
     */
    public LoginResult login(LoginRequest req) {
        throw Todo.of("Gavin", "AuthService.login");
    }

    /**
     * POST /api/auth/google. Verifies the Google ID token, finds the user by google_sub (or links by email,
     * or creates an activated account), then creates a session like login().
     */
    public LoginResult loginWithGoogle(GoogleLoginRequest req) {
        throw Todo.of("Gavin", "AuthService.loginWithGoogle");
    }

    /** POST /api/auth/logout. Invalidates the session (controller clears the cookie). */
    public void logout(String rawSessionId) {
        throw Todo.of("Gavin", "AuthService.logout");
    }

    /** GET /api/auth/me. The user plus their team memberships. */
    public MeResponse me(AuthContext ctx) {
        throw Todo.of("Gavin", "AuthService.me");
    }

    /**
     * POST /api/auth/password-reset/request. Lockout applies (PASSWORD_RESET). Always returns normally,
     * whether or not the email exists, so it does not reveal accounts.
     */
    public void requestPasswordReset(PasswordResetRequest req) {
        throw Todo.of("Gavin", "AuthService.requestPasswordReset");
    }

    /** POST /api/auth/password-reset/confirm. Verifies the code, sets the new hash, invalidates all sessions. */
    public void confirmPasswordReset(PasswordResetConfirmRequest req) {
        throw Todo.of("Gavin", "AuthService.confirmPasswordReset");
    }

    /**
     * Used by AuthFilter on every request. Validates and touches the session, loads the user (must be
     * activated) and their team roles (MembershipDao.findRolesForUser), and builds an AuthContext.
     * Returns empty for missing/expired/invalid sessions. Must NOT throw for a bad cookie.
     */
    public Optional<AuthContext> authenticate(String rawSessionId) {
        Todo.noop("Gavin", "AuthService.authenticate");
        return Optional.empty();
    }
}
