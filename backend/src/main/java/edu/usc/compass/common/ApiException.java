package edu.usc.compass.common;

import java.util.Map;

/**
 * The one exception type every service throws to produce an error response.
 * ApiDispatcherServlet turns it into the standard JSON body:
 * <pre>{ "error": { "code": "...", "message": "...", "fields": {...}, "current": {...} } }</pre>
 * PLATFORM — complete. Use the static factories instead of the constructor.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fields;
    private final Object current;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode code, String message, Map<String, String> fields, Object current, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.fields = fields;
        this.current = current;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    // ---- factories -------------------------------------------------------

    /** 400 with per-field messages the frontend shows next to each input. */
    public static ApiException validation(Map<String, String> fields) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Some fields need attention.", Map.copyOf(fields), null, null);
    }

    public static ApiException validation(String field, String message) {
        return validation(Map.of(field, message));
    }

    /** 400 without field details (malformed JSON, wrong Content-Type, bad query param). */
    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, "Please sign in.");
    }

    public static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to do that.");
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Not found.");
    }

    public static ApiException guestLinkUnavailable() {
        return new ApiException(ErrorCode.GUEST_LINK_UNAVAILABLE, "This link has expired or is no longer available.");
    }

    /** 409 with the latest saved record so the frontend can show the conflict dialog. */
    public static ApiException versionConflict(Object current) {
        return new ApiException(ErrorCode.VERSION_CONFLICT,
                "Someone else changed this. Review the latest version and try again.", null, current, null);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE, message);
    }

    /** 429 with a Retry-After header. */
    public static ApiException lockedOut(long retryAfterSeconds, String message) {
        return new ApiException(ErrorCode.LOCKED_OUT, message, null, null, retryAfterSeconds);
    }

    public static ApiException rateLimited(long retryAfterSeconds) {
        return new ApiException(ErrorCode.RATE_LIMITED, "Too many requests. Try again later.", null, null, retryAfterSeconds);
    }

    // ---- accessors -------------------------------------------------------

    public ErrorCode code() {
        return code;
    }

    public int httpStatus() {
        return code.httpStatus();
    }

    public Map<String, String> fields() {
        return fields;
    }

    public Object current() {
        return current;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
