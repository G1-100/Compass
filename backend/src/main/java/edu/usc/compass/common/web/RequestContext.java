package edu.usc.compass.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Json;
import edu.usc.compass.common.PageRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a route handler needs: path params, query params, the JSON body, the signed-in user,
 * and a way to set 201 Created. Handlers should not touch the raw servlet objects except for cookies.
 * PLATFORM — complete.
 */
public final class RequestContext {

    /** Request attribute where AuthFilter stores the {@link AuthContext}. */
    public static final String AUTH_ATTRIBUTE = "compass.auth";

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final Map<String, String> pathParams;
    private int status = HttpServletResponse.SC_OK;

    public RequestContext(HttpServletRequest request, HttpServletResponse response, Map<String, String> pathParams) {
        this.request = request;
        this.response = response;
        this.pathParams = pathParams;
    }

    // ---- path ------------------------------------------------------------

    /** Numeric path segment such as {projectId}. Non-numeric values return 404. */
    public long pathLong(String name) {
        try {
            return Long.parseLong(pathParams.get(name));
        } catch (NumberFormatException | NullPointerException e) {
            throw ApiException.notFound();
        }
    }

    public String path(String name) {
        return pathParams.get(name);
    }

    // ---- query -----------------------------------------------------------

    public Optional<String> query(String name) {
        String v = request.getParameter(name);
        return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v);
    }

    public Optional<Long> queryLong(String name) {
        try {
            return query(name).map(Long::parseLong);
        } catch (NumberFormatException e) {
            throw ApiException.validation(name, "Must be a number.");
        }
    }

    public boolean queryBool(String name) {
        return query(name).map(Boolean::parseBoolean).orElse(false);
    }

    /** Parses ?value= as an enum, returning 400 for unknown values. */
    public <E extends Enum<E>> Optional<E> queryEnum(String name, Class<E> type) {
        try {
            return query(name).map(v -> Enum.valueOf(type, v));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(name, "Unknown value.");
        }
    }

    /** ?page= and ?pageSize= with defaults 1 and 25. */
    public PageRequest page() {
        try {
            int page = query("page").map(Integer::parseInt).orElse(1);
            int size = query("pageSize").map(Integer::parseInt).orElse(PageRequest.DEFAULT_PAGE_SIZE);
            return new PageRequest(page, size);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("page and pageSize must be numbers.");
        }
    }

    // ---- body ------------------------------------------------------------

    /** Reads the JSON body into a request record. Malformed JSON or unknown fields return 400. */
    public <T> T body(Class<T> type) {
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                throw ApiException.badRequest("Request body is required.");
            }
            return Json.read(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), type);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Malformed JSON: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read request body.");
        }
    }

    // ---- auth ------------------------------------------------------------

    /** The signed-in user. Only call on SIGNED_IN routes (the dispatcher already returned 401 otherwise). */
    public AuthContext auth() {
        return authOptional().orElseThrow(ApiException::unauthenticated);
    }

    public Optional<AuthContext> authOptional() {
        return Optional.ofNullable((AuthContext) request.getAttribute(AUTH_ATTRIBUTE));
    }

    // ---- response --------------------------------------------------------

    /** Sets 201 Created with a Location header and returns the body so a handler can `return ctx.created(...)`. */
    public <T> T created(String location, T body) {
        this.status = HttpServletResponse.SC_CREATED;
        response.setHeader("Location", location);
        return body;
    }

    public void header(String name, String value) {
        response.setHeader(name, value);
    }

    public int status() {
        return status;
    }

    /** Raw objects, only for cookies (session) and similar. */
    public HttpServletRequest rawRequest() {
        return request;
    }

    public HttpServletResponse rawResponse() {
        return response;
    }
}
