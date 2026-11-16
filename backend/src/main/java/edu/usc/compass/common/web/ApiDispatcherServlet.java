package edu.usc.compass.common.web;

import edu.usc.compass.AppContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single servlet behind /api/*. It applies the API conventions from Detailed Design 3.5 once,
 * so feature code never repeats them:
 * <ul>
 *   <li>Cache-Control: no-store and JSON content type on every response</li>
 *   <li>POST/PUT/PATCH must send Content-Type: application/json (else 400)</li>
 *   <li>Route lookup (404), SIGNED_IN routes without a session (401)</li>
 *   <li>ApiException -> standard error body; anything else -> generic 500, details logged only</li>
 * </ul>
 * PLATFORM — complete.
 */
@WebServlet(urlPatterns = "/api/*", loadOnStartup = 1)
public class ApiDispatcherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ApiDispatcherServlet.class);
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "no-store");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String method = req.getMethod();
        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (BODY_METHODS.contains(method)) {
                String ct = req.getContentType();
                if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
                    throw ApiException.badRequest("Content-Type must be application/json.");
                }
            }

            Router router = AppContext.get(getServletContext()).router();
            Optional<Router.Match> match = router.match(method, path);
            if (match.isEmpty()) {
                throw ApiException.notFound();
            }

            Router.Route route = match.get().route();
            RequestContext ctx = new RequestContext(req, resp, match.get().pathParams());
            if (route.access() == Router.Access.SIGNED_IN && ctx.authOptional().isEmpty()) {
                throw ApiException.unauthenticated();
            }

            Object body = route.handler().handle(ctx);
            resp.setStatus(ctx.status());
            Json.write(resp.getOutputStream(), body == null ? Map.of() : body);
        } catch (ApiException e) {
            writeError(resp, e);
        } catch (Exception e) {
            log.error("Unhandled error on {} {}", method, path, e);
            writeError(resp, new ApiException(ErrorCode.SERVER_ERROR, "Something went wrong. Please try again."));
        }
    }

    static void writeError(HttpServletResponse resp, ApiException e) throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        resp.resetBuffer();
        resp.setStatus(e.httpStatus());
        if (e.retryAfterSeconds() != null) {
            resp.setHeader("Retry-After", String.valueOf(e.retryAfterSeconds()));
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", e.code().name());
        error.put("message", e.getMessage());
        if (e.fields() != null) {
            error.put("fields", e.fields());
        }
        if (e.current() != null) {
            error.put("current", e.current());
        }
        Json.write(resp.getOutputStream(), Map.of("error", error));
    }
}
