package edu.usc.compass.auth;

import edu.usc.compass.auth.AuthDtos.ActivateRequest;
import edu.usc.compass.auth.AuthDtos.ChangeSystemRoleRequest;
import edu.usc.compass.auth.AuthDtos.GoogleLoginRequest;
import edu.usc.compass.auth.AuthDtos.LoginRequest;
import edu.usc.compass.auth.AuthDtos.PasswordResetConfirmRequest;
import edu.usc.compass.auth.AuthDtos.PasswordResetRequest;
import edu.usc.compass.auth.AuthDtos.RegisterRequest;
import edu.usc.compass.auth.AuthDtos.ResendCodeRequest;
import edu.usc.compass.auth.AuthDtos.UpdateProfileRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.web.Router;

import static edu.usc.compass.common.web.Router.Access.PUBLIC;
import static edu.usc.compass.common.web.Router.Access.SIGNED_IN;

/**
 * Routes for Accounts and Authentication (Detailed Design 3.5.10). Owner: Gavin.
 * Routes are wired; the service methods they call are stubs.
 */
public final class AuthController {

    private final AuthService auth;
    private final UserService users;
    private final SessionManager sessions;

    public AuthController(AuthService auth, UserService users, SessionManager sessions) {
        this.auth = auth;
        this.users = users;
        this.sessions = sessions;
    }

    public void register(Router r) {
        r.post("/api/auth/register", PUBLIC, ctx -> ctx.created("/api/users/me", auth.register(ctx.body(RegisterRequest.class))));
        r.post("/api/auth/activate", PUBLIC, ctx -> {
            auth.activate(ctx.body(ActivateRequest.class));
            return null;
        });
        r.post("/api/auth/resend-code", PUBLIC, ctx -> {
            auth.resendCode(ctx.body(ResendCodeRequest.class));
            return null;
        });
        r.post("/api/auth/login", PUBLIC, ctx -> {
            // TODO(Gavin): var result = auth.login(...); sessions.writeCookie(ctx.rawResponse(), result.rawSessionId()); return result.me();
            throw Todo.of("Gavin", "POST /api/auth/login");
        });
        r.post("/api/auth/google", PUBLIC, ctx -> {
            // TODO(Gavin): same as login, using auth.loginWithGoogle(ctx.body(GoogleLoginRequest.class))
            throw Todo.of("Gavin", "POST /api/auth/google");
        });
        r.post("/api/auth/logout", SIGNED_IN, ctx -> {
            // TODO(Gavin): read cookie, auth.logout(raw), sessions.clearCookie(ctx.rawResponse())
            throw Todo.of("Gavin", "POST /api/auth/logout");
        });
        r.get("/api/auth/me", SIGNED_IN, ctx -> auth.me(ctx.auth()));
        r.post("/api/auth/password-reset/request", PUBLIC, ctx -> {
            auth.requestPasswordReset(ctx.body(PasswordResetRequest.class));
            return null;
        });
        r.post("/api/auth/password-reset/confirm", PUBLIC, ctx -> {
            auth.confirmPasswordReset(ctx.body(PasswordResetConfirmRequest.class));
            return null;
        });

        r.get("/api/users/me", SIGNED_IN, ctx -> users.getProfile(ctx.auth()));
        r.patch("/api/users/me", SIGNED_IN, ctx -> users.updateProfile(ctx.auth(), ctx.body(UpdateProfileRequest.class)));
        r.get("/api/users", SIGNED_IN, ctx -> users.listUsers(ctx.auth(), ctx.page()));
        r.patch("/api/users/{userId}/role", SIGNED_IN,
                ctx -> users.changeSystemRole(ctx.auth(), ctx.pathLong("userId"), ctx.body(ChangeSystemRoleRequest.class)));
    }
}
