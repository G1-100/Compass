package edu.usc.compass.auth;

import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.teams.model.TeamRole;

import java.util.List;

/** Request and response bodies for /api/auth and /api/users (Detailed Design 3.5.10). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(String email, String password, String displayName) {
    }

    public record ActivateRequest(String email, String code) {
    }

    public record ResendCodeRequest(String email) {
    }

    public record LoginRequest(String email, String password) {
    }

    /** credential = the ID token from Google's sign-in pop-up. */
    public record GoogleLoginRequest(String credential) {
    }

    public record PasswordResetRequest(String email) {
    }

    public record PasswordResetConfirmRequest(String email, String code, String newPassword) {
    }

    public record UpdateProfileRequest(String displayName, int version) {
    }

    public record ChangeSystemRoleRequest(SystemRole systemRole, int version) {
    }

    public record TeamMembershipSummary(long teamId, String teamName, TeamRole role) {
    }

    /** GET /api/auth/me */
    public record MeResponse(User user, List<TeamMembershipSummary> teams) {
    }

    /** Internal: what a successful login produces. The raw session id goes only into the cookie. */
    public record LoginResult(String rawSessionId, MeResponse me) {
    }
}
