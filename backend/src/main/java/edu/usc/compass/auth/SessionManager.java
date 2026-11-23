package edu.usc.compass.auth;

import edu.usc.compass.auth.dao.SessionDao;
import edu.usc.compass.auth.dao.SessionDao.SessionRow;
import edu.usc.compass.common.Hashing;
import edu.usc.compass.common.Rules;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Creates, validates and ends sessions (Detailed Design 3.1.1). Only the SHA-256 hash of the session id is
 * stored; the raw id lives only in the HttpOnly cookie. 30-minute inactivity timeout (Rules.SESSION_TIMEOUT).
 * Owner: Gavin.
 */
public class SessionManager {

    public static final String COOKIE_NAME = "compass_session";

    private static final int SESSION_ID_BYTES = 32;

    private final SessionDao sessionDao;
    private final Clock clock;

    public SessionManager(SessionDao sessionDao, Clock clock) {
        this.sessionDao = sessionDao;
        this.clock = clock;
    }

    /** Generates a random session id, stores its hash, returns the raw id for the cookie. */
    public String createSession(Connection c, long userId) {
        String raw = Hashing.randomToken(SESSION_ID_BYTES);
        sessionDao.insert(c, Hashing.sha256Hex(raw), userId, Instant.now(clock));
        return raw;
    }

    /**
     * Returns the user id if the session exists, is not invalidated, and was active in the last 30 minutes.
     * On success also updates last_activity_at to now.
     */
    public Optional<Long> validateAndTouch(Connection c, String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return Optional.empty();
        }
        String hash = Hashing.sha256Hex(rawSessionId);
        Optional<SessionRow> row = sessionDao.findByHash(c, hash);
        if (row.isEmpty() || row.get().invalidated()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        if (isExpired(row.get().lastActivityAt(), now)) {
            return Optional.empty();
        }
        sessionDao.touch(c, hash, now);
        return Optional.of(row.get().userId());
    }

    /** Exactly 30 minutes of inactivity counts as expired. */
    static boolean isExpired(Instant lastActivityAt, Instant now) {
        return lastActivityAt == null || !now.isBefore(lastActivityAt.plus(Rules.SESSION_TIMEOUT));
    }

    /** Marks one session invalidated (logout). */
    public void invalidate(Connection c, String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return;
        }
        sessionDao.invalidate(c, Hashing.sha256Hex(rawSessionId));
    }

    /** Marks all of a user's sessions invalidated (after a password reset). */
    public void invalidateAllForUser(Connection c, long userId) {
        sessionDao.invalidateAllForUser(c, userId);
    }

    /** Reads the raw session id from the request cookie, if present. */
    public Optional<String> readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Sets the cookie: HttpOnly, Secure, SameSite=Lax, Path=/. No Max-Age, so it is a browser-session cookie;
     * the 30-minute inactivity limit is enforced on the server. Never put the id in a URL or localStorage.
     * Written as a raw header because jakarta Cookie has no SameSite setter.
     */
    public void writeCookie(HttpServletResponse resp, String rawSessionId) {
        resp.addHeader("Set-Cookie", COOKIE_NAME + "=" + rawSessionId + "; Path=/; HttpOnly; Secure; SameSite=Lax");
    }

    /** Expires the cookie (Max-Age=0). */
    public void clearCookie(HttpServletResponse resp) {
        resp.addHeader("Set-Cookie", COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
    }
}
