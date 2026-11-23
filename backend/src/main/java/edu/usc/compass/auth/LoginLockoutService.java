package edu.usc.compass.auth;

import edu.usc.compass.auth.dao.LoginLockoutDao;
import edu.usc.compass.auth.dao.LoginLockoutDao.LockoutRow;
import edu.usc.compass.auth.model.AuthEnums.LockoutAction;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Rules;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Brute-force protection (Detailed Design 3.1.1.1). Tracked per email + action.
 * 5 failures inside a 30-minute window lock that email+action for 30 minutes.
 * Owner: Gavin. Tests: White box 1-2, Black box 3, Stress 4.
 *
 * IMPORTANT for callers: Database.inTransaction rolls back on any exception, so call recordFailure in a
 * transaction that COMMITS, and throw the 401/400 only after it returns (see AuthService.login).
 */
public class LoginLockoutService {

    private final LoginLockoutDao lockoutDao;
    private final Clock clock;

    public LoginLockoutService(LoginLockoutDao lockoutDao, Clock clock) {
        this.lockoutDao = lockoutDao;
        this.clock = clock;
    }

    /**
     * Call BEFORE checking the password. If locked_until is in the future, throw 429 with Retry-After
     * without checking anything else.
     */
    public void checkNotLocked(Connection c, String email, LockoutAction action) {
        Optional<LockoutRow> row = lockoutDao.findForUpdate(c, key(email), action);
        if (row.isEmpty() || row.get().lockedUntil() == null) {
            return;
        }
        Instant now = Instant.now(clock);
        Instant lockedUntil = row.get().lockedUntil();
        if (lockedUntil.isAfter(now)) {
            long seconds = Math.max(1, (Duration.between(now, lockedUntil).toMillis() + 999) / 1000);
            throw ApiException.lockedOut(seconds, message(action));
        }
    }

    /**
     * Records one failure: if the current window started 30+ minutes ago, reset failed_count to 1 and start a
     * new window; otherwise increment. On the 5th failure set locked_until = now + 30 minutes.
     * The row is created first and then locked FOR UPDATE, so parallel attempts cannot exceed 5.
     */
    public void recordFailure(Connection c, String email, LockoutAction action) {
        String key = key(email);
        Instant now = Instant.now(clock);
        lockoutDao.ensureExists(c, key, action, now);
        LockoutRow current = lockoutDao.findForUpdate(c, key, action)
                .orElse(new LockoutRow(key, action, 0, now, null));
        lockoutDao.upsert(c, next(current, now));
    }

    /** Pure counting rule, separated so it can be unit tested without a database. */
    static LockoutRow next(LockoutRow current, Instant now) {
        boolean windowOver = current.windowStart() == null
                || !now.isBefore(current.windowStart().plus(Rules.LOCKOUT_WINDOW));
        int count;
        Instant windowStart;
        Instant lockedUntil;
        if (windowOver) {
            count = 1;
            windowStart = now;
            lockedUntil = null;
        } else {
            count = current.failedCount() + 1;
            windowStart = current.windowStart();
            lockedUntil = current.lockedUntil();
        }
        if (count >= Rules.LOCKOUT_MAX_FAILURES) {
            lockedUntil = now.plus(Rules.LOCKOUT_DURATION);
        }
        return new LockoutRow(current.email(), current.action(), count, windowStart, lockedUntil);
    }

    /** Clears the counter after a successful login. */
    public void reset(Connection c, String email, LockoutAction action) {
        lockoutDao.delete(c, key(email), action);
    }

    static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String message(LockoutAction action) {
        return switch (action) {
            case LOGIN -> "Too many login attempts. Try again in 30 minutes.";
            case PASSWORD_RESET -> "Too many password reset requests. Try again in 30 minutes.";
            case RESEND_CODE -> "Too many code requests. Try again in 30 minutes.";
        };
    }
}
