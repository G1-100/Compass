package edu.usc.compass.auth.dao;

import edu.usc.compass.auth.model.AuthEnums.CodePurpose;

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
        return Jdbc.one(c, """
                        SELECT code_id, user_id, code_hash, purpose, expires_at, attempts_used, consumed_at
                        FROM verification_codes
                        WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL
                        FOR UPDATE""",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setString(2, purpose.name());
                },
                rs -> new CodeRow(rs.getLong("code_id"), rs.getLong("user_id"), rs.getString("code_hash"),
                        CodePurpose.valueOf(rs.getString("purpose")), Jdbc.getInstant(rs, "expires_at"),
                        rs.getInt("attempts_used"), Jdbc.getInstant(rs, "consumed_at")));
    }

    /** Marks any active code for user+purpose consumed, so a new one can be inserted. */
    public void consumeActive(Connection c, long userId, CodePurpose purpose, Instant now) {
        Jdbc.update(c, """
                UPDATE verification_codes SET consumed_at = ?
                WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL""", ps -> {
            Jdbc.setInstant(ps, 1, now);
            ps.setLong(2, userId);
            ps.setString(3, purpose.name());
        });
    }

    public void insert(Connection c, long userId, String codeHash, CodePurpose purpose, Instant expiresAt) {
        Jdbc.update(c, """
                INSERT INTO verification_codes (user_id, code_hash, purpose, expires_at)
                VALUES (?, ?, ?, ?)""", ps -> {
            ps.setLong(1, userId);
            ps.setString(2, codeHash);
            ps.setString(3, purpose.name());
            Jdbc.setInstant(ps, 4, expiresAt);
        });
    }

    public void incrementAttempts(Connection c, long codeId) {
        Jdbc.update(c, "UPDATE verification_codes SET attempts_used = attempts_used + 1 WHERE code_id = ?",
                ps -> ps.setLong(1, codeId));
    }

    public void markConsumed(Connection c, long codeId, Instant now) {
        Jdbc.update(c, "UPDATE verification_codes SET consumed_at = ? WHERE code_id = ? AND consumed_at IS NULL", ps -> {
            Jdbc.setInstant(ps, 1, now);
            ps.setLong(2, codeId);
        });
    }
}
