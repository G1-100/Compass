package edu.usc.compass.common;

import java.time.Duration;

/**
 * Shared configuration values (Detailed Design 3.2.2 and 3.1). Change numbers here, not inline.
 * PLATFORM — complete.
 */
public final class Rules {

    private Rules() {
    }

    // Workload and risk (Liv)
    public static final double OVERLOAD_THRESHOLD = 0.20;
    public static final double AVAILABLE_CAPACITY_THRESHOLD = 0.20;
    public static final int DRIFT_INACTIVITY_DAYS = 7;
    public static final int DRIFT_DUE_WINDOW_DAYS = 14;
    public static final int LATE_RISK_WINDOW_DAYS = 7;
    /** Stored on every alert so old alerts can be explained after thresholds change. */
    public static final String CURRENT_RULE_VERSION = "2026.11-v1";

    // Email (Raegen)
    public static final int MAX_EMAIL_ATTEMPTS = 3;

    // Auth (Gavin)
    public static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);
    public static final int LOCKOUT_MAX_FAILURES = 5;
    public static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(30);
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);
    public static final Duration VERIFICATION_CODE_TTL = Duration.ofHours(1);
    public static final int VERIFICATION_CODE_MAX_ATTEMPTS = 5;

    // Teams (Solomon)
    public static final Duration INVITATION_TTL = Duration.ofDays(7);

    // Check-ins (Graham)
    public static final Duration DEFAULT_CHECKIN_REMINDER_LEAD = Duration.ofHours(24);
    public static final int CHECKIN_TEXT_LIMIT = 2_000;
    public static final int CHECKIN_LOCATION_LIMIT = 300;
    public static final int CHECKIN_BLOCKER_LIMIT = 500;
}
