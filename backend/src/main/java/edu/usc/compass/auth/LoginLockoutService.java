package edu.usc.compass.auth;

import edu.usc.compass.auth.dao.LoginLockoutDao;
import edu.usc.compass.auth.model.AuthEnums.LockoutAction;
import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.time.Clock;

/**
 * Brute-force protection (Detailed Design 3.1.1.1). Tracked per email + action.
 * 5 failures inside a 30-minute window lock that email+action for 30 minutes.
 * Owner: Gavin. Tests: White box 1-2, Black box 3, Stress 4.
 */
public class LoginLockoutService {

    private final LoginLockoutDao lockoutDao;
    private final Clock clock;

    public LoginLockoutService(LoginLockoutDao lockoutDao, Clock clock) {
        this.lockoutDao = lockoutDao;
        this.clock = clock;
    }

    /**
     * Call BEFORE checking the password. If locked_until is in the future, throw
     * ApiException.lockedOut(secondsRemaining, "Too many login attempts. Try again in 30 minutes.")
     * without checking anything else.
     */
    public void checkNotLocked(Connection c, String email, LockoutAction action) {
        throw Todo.of("Gavin", "LoginLockoutService.checkNotLocked");
    }

    /**
     * Records one failure: if the current window started more than 30 minutes ago, reset failed_count to 1
     * and start a new window; otherwise increment. On the 5th failure set locked_until = now + 30 minutes.
     * Use SELECT ... FOR UPDATE (or an upsert) so parallel attempts cannot exceed 5.
     */
    public void recordFailure(Connection c, String email, LockoutAction action) {
        throw Todo.of("Gavin", "LoginLockoutService.recordFailure");
    }

    /** Clears the counter after a successful login. */
    public void reset(Connection c, String email, LockoutAction action) {
        throw Todo.of("Gavin", "LoginLockoutService.reset");
    }
}
