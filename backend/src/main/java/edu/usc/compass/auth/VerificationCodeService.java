package edu.usc.compass.auth;

import edu.usc.compass.auth.dao.VerificationCodeDao;
import edu.usc.compass.auth.dao.VerificationCodeDao.CodeRow;
import edu.usc.compass.auth.model.AuthEnums.CodePurpose;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Hashing;
import edu.usc.compass.common.Rules;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 6-digit one-time codes for account activation and password reset (Detailed Design 3.1.1).
 * Codes are hashed at rest, expire after 1 hour, allow 5 attempts, and one active code per user+purpose.
 * Owner: Gavin. Tests: White box 4.
 *
 * {@link #check} returns a result instead of throwing, because a wrong attempt must be COMMITTED
 * (Database.inTransaction rolls back on exceptions). Callers throw {@link CodeCheck#toException()} afterwards.
 */
public class VerificationCodeService {

    /** Outcome of checking a code. */
    public enum CodeCheck {
        OK, WRONG, EXPIRED, MISSING;

        /** The 400 to show for a failed check. Null for OK. */
        public ApiException toException() {
            return switch (this) {
                case OK -> null;
                case WRONG -> ApiException.validation("code", "That code is incorrect.");
                case EXPIRED -> ApiException.validation("code", "That code has expired. Request a new one.");
                case MISSING -> ApiException.validation("code", "No active code. Request a new one.");
            };
        }
    }

    private final VerificationCodeDao codeDao;
    private final Clock clock;

    public VerificationCodeService(VerificationCodeDao codeDao, Clock clock) {
        this.codeDao = codeDao;
        this.clock = clock;
    }

    /**
     * Invalidates any existing active code for this user+purpose, creates a new one, stores only its hash,
     * and returns the raw code so the caller can email it.
     */
    public String issue(Connection c, long userId, CodePurpose purpose) {
        Instant now = Instant.now(clock);
        codeDao.consumeActive(c, userId, purpose, now);
        String raw = Hashing.sixDigitCode();
        codeDao.insert(c, userId, Hashing.sha256Hex(raw), purpose, now.plus(Rules.VERIFICATION_CODE_TTL));
        return raw;
    }

    /**
     * Checks a code. Wrong code: increment attempts_used; the 5th wrong attempt also consumes the code, so the
     * user must resend. Expired code: consumed and EXPIRED. Correct code: consumed_at = now and OK.
     */
    public CodeCheck check(Connection c, long userId, CodePurpose purpose, String rawCode) {
        Optional<CodeRow> found = codeDao.findActiveForUpdate(c, userId, purpose);
        if (found.isEmpty()) {
            return CodeCheck.MISSING;
        }
        CodeRow row = found.get();
        Instant now = Instant.now(clock);
        if (!now.isBefore(row.expiresAt())) {
            codeDao.markConsumed(c, row.codeId(), now);
            return CodeCheck.EXPIRED;
        }
        if (row.attemptsUsed() >= Rules.VERIFICATION_CODE_MAX_ATTEMPTS) {
            codeDao.markConsumed(c, row.codeId(), now);
            return CodeCheck.MISSING;
        }
        String given = rawCode == null ? "" : rawCode.trim();
        if (Hashing.constantTimeEquals(Hashing.sha256Hex(given), row.codeHash())) {
            codeDao.markConsumed(c, row.codeId(), now);
            return CodeCheck.OK;
        }
        codeDao.incrementAttempts(c, row.codeId());
        if (row.attemptsUsed() + 1 >= Rules.VERIFICATION_CODE_MAX_ATTEMPTS) {
            codeDao.markConsumed(c, row.codeId(), now);
        }
        return CodeCheck.WRONG;
    }
}
