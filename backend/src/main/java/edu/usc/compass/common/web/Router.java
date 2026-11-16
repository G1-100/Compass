package edu.usc.compass.common.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Route table for every /api/ endpoint. Each feature registers its routes in its own *Controller
 * class, so nobody edits a shared file to add an endpoint.
 *
 * <pre>
 * router.get("/api/projects/{projectId}/tasks", Access.SIGNED_IN, ctx -> taskService.list(...));
 * </pre>
 * Literal segments win over {params}, so /api/users/me matches before /api/users/{userId}.
 * PLATFORM — complete.
 */
public final class Router {

    /** Who may reach a route before the service runs its own permission checks. */
    public enum Access {
        /** No session required (login, register, health, guest links). */
        PUBLIC,
        /** Must have a valid session; the service then checks team role with PermissionService. */
        SIGNED_IN
    }

    @FunctionalInterface
    public interface Handler {
        /** Return the response body (serialized to JSON), or null for an empty {} body. */
        Object handle(RequestContext ctx) throws Exception;
    }

    public record Route(String method, String pattern, Access access, Handler handler, String[] segments) {
        int literalCount() {
            int n = 0;
            for (String s : segments) {
                if (!isParam(s)) {
                    n++;
                }
            }
            return n;
        }
    }

    public record Match(Route route, Map<String, String> pathParams) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String pattern, Access access, Handler h) {
        return add("GET", pattern, access, h);
    }

    public Router post(String pattern, Access access, Handler h) {
        return add("POST", pattern, access, h);
    }

    public Router put(String pattern, Access access, Handler h) {
        return add("PUT", pattern, access, h);
    }

    public Router patch(String pattern, Access access, Handler h) {
        return add("PATCH", pattern, access, h);
    }

    public Router delete(String pattern, Access access, Handler h) {
        return add("DELETE", pattern, access, h);
    }

    private Router add(String method, String pattern, Access access, Handler h) {
        String[] segs = split(pattern);
        for (Route r : routes) {
            if (r.method().equals(method) && r.pattern().equals(pattern)) {
                throw new IllegalStateException("Duplicate route: " + method + " " + pattern);
            }
        }
        routes.add(new Route(method, pattern, access, h, segs));
        return this;
    }

    /** Finds the most specific route for a method and path such as "/api/teams/5/members". */
    public Optional<Match> match(String method, String path) {
        String[] parts = split(path);
        Match best = null;
        int bestScore = -1;
        for (Route r : routes) {
            if (!r.method().equals(method) || r.segments().length != parts.length) {
                continue;
            }
            Map<String, String> params = new HashMap<>();
            boolean ok = true;
            for (int i = 0; i < parts.length; i++) {
                String seg = r.segments()[i];
                if (isParam(seg)) {
                    params.put(seg.substring(1, seg.length() - 1), parts[i]);
                } else if (!seg.equals(parts[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok && r.literalCount() > bestScore) {
                best = new Match(r, params);
                bestScore = r.literalCount();
            }
        }
        return Optional.ofNullable(best);
    }

    /** True if any route exists for this path with a different method (lets the dispatcher return 405 vs 404). */
    public boolean pathExists(String path) {
        String[] parts = split(path);
        for (Route r : routes) {
            if (r.segments().length != parts.length) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < parts.length && ok; i++) {
                ok = isParam(r.segments()[i]) || r.segments()[i].equals(parts[i]);
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    public List<Route> routes() {
        return List.copyOf(routes);
    }

    private static boolean isParam(String seg) {
        return seg.startsWith("{") && seg.endsWith("}");
    }

    private static String[] split(String path) {
        return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
