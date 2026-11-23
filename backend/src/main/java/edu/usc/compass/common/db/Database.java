package edu.usc.compass.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection pool + transaction helper. Services own transactions; DAOs receive the Connection.
 *
 * <pre>
 * return db.inTransaction(c -> {
 *     Task t = taskDao.findForUpdate(c, taskId);       // SELECT ... FOR UPDATE
 *     taskDao.updateStatus(c, taskId, status, version + 1);
 *     activityService.record(c, ...);                  // same transaction
 *     notificationService.notify(c, ...);              // queued rows commit with it
 *     return taskDao.findById(c, taskId);
 * });
 * </pre>
 * Any exception rolls back; ApiException is rethrown unchanged so it reaches the client.
 * PLATFORM — complete.
 */
public final class Database implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final HikariDataSource dataSource;

    private Database(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database fromConfig(AppConfig config) {
        if (!config.hasDatabase()) {
            log.warn("SUPABASE_DB_URL is not set. Endpoints that touch the database will return 500.");
            return new Database(null);
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.databaseUrl());
        // Tomcat's driver discovery does not scan JDBC drivers inside the WAR.
        hc.setDriverClassName(org.postgresql.Driver.class.getName());
        hc.setMaximumPoolSize(10);
        hc.setPoolName("compass");
        return new Database(new HikariDataSource(hc));
    }

    public boolean isConfigured() {
        return dataSource != null;
    }

    /** Runs work in one transaction: commit on success, rollback on any exception. */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Read-only or single-statement work with autocommit. */
    public <T> T withConnection(SqlWork<T> work) {
        try (Connection c = connection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    public boolean ping() {
        try (Connection c = connection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private Connection connection() throws SQLException {
        if (dataSource == null) {
            throw new ApiException(ErrorCode.SERVER_ERROR, "Database is not configured (set SUPABASE_DB_URL).");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
