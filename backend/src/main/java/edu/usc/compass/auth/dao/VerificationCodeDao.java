package edu.usc.compass.auth.dao;

import edu.usc.compass.auth.model.AuthEnums.CodePurpose;
import edu.usc.compass.common.Todo;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

/** SQL for verification_codes. Owner: Gavin. */
public class VerificationCodeDao {

    public record CodeRow(long codeId, long userId, String codeHash, CodePurpose purpose, Instant expiresAt,
                          int attemptsUsed, Instant consumedAt) {
    }

    /** The unconsumed code for user+purpose, locked FOR UPDATE. */
    public Optional<CodeRow> findActiveForUpdate(Connection c, long userId, CodePurpose purpose) {
        throw Todo.of("Gavin", "VerificationCodeDao.findActiveForUpdate");
    }

    /** Marks any active code for user+purpose consumed, so a new one can be inserted. */
    public void consumeActive(Connection c, long userId, CodePurpose purpose, Instant now) {
        throw Todo.of("Gavin", "VerificationCodeDao.consumeActive");
    }

    public void insert(Connection c, long userId, String codeHash, CodePurpose purpose, Instant expiresAt) {
        throw Todo.of("Gavin", "VerificationCodeDao.insert");
    }

    public void incrementAttempts(Connection c, long codeId) {
        throw Todo.of("Gavin", "VerificationCodeDao.incrementAttempts");
    }

    public void markConsumed(Connection c, long codeId, Instant now) {
        throw Todo.of("Gavin", "VerificationCodeDao.markConsumed");
    }
}
