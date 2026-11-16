package edu.usc.compass.common;

/**
 * Error codes returned in every error body (Detailed Design 3.5.1).
 * PLATFORM — complete. Do not rename values; the frontend matches on them.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    GUEST_LINK_UNAVAILABLE(404),
    VERSION_CONFLICT(409),
    INVALID_STATE(409),
    LOCKED_OUT(429),
    RATE_LIMITED(429),
    SERVER_ERROR(500),
    /** Returned by stubs that have not been implemented yet (see {@link Todo}). */
    NOT_IMPLEMENTED(501);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
