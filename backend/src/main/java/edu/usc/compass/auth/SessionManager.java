package edu.usc.compass.auth;

import edu.usc.compass.auth.dao.SessionDao;
import edu.usc.compass.common.Todo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.time.Clock;
import java.util.Optional;

/**
 * Creates, validates and ends sessions (Detailed Design 3.1.1). Only the SHA-256 hash of the session id is
 * stored; the raw id lives only in the HttpOnly cookie. 30-minute inactivity timeout (Rules.SESSION_TIMEOUT).
 * Owner: Gavin.
 */
public class SessionManager {

    public static final String COOKIE_NAME = "compass_session";

    private final SessionDao sessionDao;
    private final Clock clock;

    public SessionManager(SessionDao sessionDao, Clock clock) {
        this.sessionDao = sessionDao;
        this.clock = clock;
    }

    /** Generates a random session id (Hashing.randomToken), stores its hash, returns the raw id for the cookie. */
    public String createSession(Connection c, long userId) {
        throw Todo.of("Gavin", "SessionManager.createSession");
    }

    /**
     * Returns the user id if the session exists, is not invalidated, and was active in the last 30 minutes.
     * On success also updates last_activity_at to now.
     */
    public Optional<Long> validateAndTouch(Connection c, String rawSessionId) {
        throw Todo.of("Gavin", "SessionManager.validateAndTouch");
    }

    /** Marks one session invalidated (logout). */
    public void invalidate(Connection c, String rawSessionId) {
        throw Todo.of("Gavin", "SessionManager.invalidate");
    }

    /** Marks all of a user's sessions invalidated (after a password reset). */
    public void invalidateAllForUser(Connection c, long userId) {
        throw Todo.of("Gavin", "SessionManager.invalidateAllForUser");
    }

    /** Reads the raw session id from the request cookie, if present. */
    public Optional<String> readCookie(HttpServletRequest req) {
        throw Todo.of("Gavin", "SessionManager.readCookie");
    }

    /** Sets the cookie: HttpOnly, Secure, SameSite=Lax, Path=/. Never put the id in a URL or localStorage. */
    public void writeCookie(HttpServletResponse resp, String rawSessionId) {
        throw Todo.of("Gavin", "SessionManager.writeCookie");
    }

    /** Expires the cookie (Max-Age=0). */
    public void clearCookie(HttpServletResponse resp) {
        throw Todo.of("Gavin", "SessionManager.clearCookie");
    }
}
