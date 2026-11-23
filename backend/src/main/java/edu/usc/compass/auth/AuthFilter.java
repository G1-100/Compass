package edu.usc.compass.auth;

import edu.usc.compass.AppContext;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.common.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Runs before every /api/ request (Detailed Design 3.1.1.2). Looks up the session cookie and, if valid,
 * stores an {@link AuthContext} on the request. It does NOT reject requests itself: the dispatcher returns
 * 401 for SIGNED_IN routes that have no AuthContext, and PUBLIC routes (login, guest links) pass through.
 * Owner: Gavin.
 */
@WebFilter(urlPatterns = "/api/*")
public class AuthFilter extends HttpFilter {

    public static final String DEV_USER_HEADER = "X-Dev-User-Id";

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        AppContext app = AppContext.get(getServletContext());

        Optional<AuthContext> auth = devAuth(app, req);
        if (auth.isEmpty()) {
            // Validates the session, updates last_activity_at and loads team memberships. Never throws.
            auth = app.sessionManager().readCookie(req).flatMap(app.authService()::authenticate);
        }
        auth.ifPresent(a -> req.setAttribute(RequestContext.AUTH_ATTRIBUTE, a));
        chain.doFilter(req, resp);
    }

    /**
     * LOCAL DEVELOPMENT ONLY (COMPASS_DEV_AUTH=true): lets anyone build features before login exists.
     * Send header X-Dev-User-Id: 3 and you are treated as user 3 with SYSTEM_ADMIN rights.
     * Ignored entirely when COMPASS_DEV_AUTH is not true, which is the default and the production setting.
     */
    private static Optional<AuthContext> devAuth(AppContext app, HttpServletRequest req) {
        String header = req.getHeader(DEV_USER_HEADER);
        if (!app.config().devAuth() || header == null) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(header.trim());
            return Optional.of(new AuthContext(userId, "dev" + userId + "@compass.local", "Dev User " + userId,
                    SystemRole.SYSTEM_ADMIN, Map.of()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
