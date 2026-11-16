package edu.usc.compass.auth;

import edu.usc.compass.auth.dao.VerificationCodeDao;
import edu.usc.compass.auth.model.AuthEnums.CodePurpose;
import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.time.Clock;

/**
 * 6-digit one-time codes for account activation and password reset (Detailed Design 3.1.1).
 * Codes are hashed at rest, expire after 1 hour, allow 5 attempts, and one active code per user+purpose.
 * Owner: Gavin. Tests: White box 4.
 */
public class VerificationCodeService {

    private final VerificationCodeDao codeDao;
    private final Clock clock;

    public VerificationCodeService(VerificationCodeDao codeDao, Clock clock) {
        this.codeDao = codeDao;
        this.clock = clock;
    }

    /**
     * Invalidates any existing active code for this user+purpose, creates a new one
     * (Hashing.sixDigitCode), stores only its hash, and returns the raw code so the caller can email it.
     */
    public String issue(Connection c, long userId, CodePurpose purpose) {
        throw Todo.of("Gavin", "VerificationCodeService.issue");
    }

    /**
     * Checks a code. Wrong code: increment attempts_used and throw validation("code", ...); after the 5th
     * wrong attempt the code is consumed and the user must resend. Expired or missing code: validation error.
     * Correct code: mark consumed_at = now.
     */
    public void verify(Connection c, long userId, CodePurpose purpose, String rawCode) {
        throw Todo.of("Gavin", "VerificationCodeService.verify");
    }
}
