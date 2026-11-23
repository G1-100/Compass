package edu.usc.compass.auth.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small JDBC helpers for the auth DAOs, so each query is a few lines and SQLExceptions become unchecked
 * (the DAO signatures do not declare them). Database.inTransaction still rolls back on these.
 * Owner: Gavin.
 */
final class Jdbc {

    /** Postgres SQLSTATE for unique_violation. */
    static final String UNIQUE_VIOLATION = "23505";

    private Jdbc() {
    }

    @FunctionalInterface
    interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    interface Mapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    static <T> Optional<T> one(Connection c, String sql, Binder binder, Mapper<T> mapper) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    static <T> List<T> list(Connection c, String sql, Binder binder, Mapper<T> mapper) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    /** Runs INSERT/UPDATE/DELETE and returns the number of rows changed. */
    static int update(Connection c, String sql, Binder binder) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    static Instant getInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
        return odt == null ? null : odt.toInstant();
    }

    static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    static RuntimeException wrap(SQLException e) {
        return new DataAccessException(e);
    }

    /** Unchecked wrapper so callers can still inspect the SQLSTATE. */
    static final class DataAccessException extends RuntimeException {
        DataAccessException(SQLException cause) {
            super("Database error: " + cause.getMessage(), cause);
        }
    }
}
