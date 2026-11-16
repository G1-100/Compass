package edu.usc.compass.auth.model;

/** Small enums used only inside the auth feature. */
public final class AuthEnums {

    private AuthEnums() {
    }

    /** login_lockouts.action */
    public enum LockoutAction {
        LOGIN, PASSWORD_RESET, RESEND_CODE
    }

    /** verification_codes.purpose */
    public enum CodePurpose {
        ACTIVATION, PASSWORD_RESET
    }

    /** What Google tells us after verifying an ID token. */
    public record GoogleIdentity(String sub, String email, String name, boolean emailVerified) {
    }
}
