package edu.usc.compass.common.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that receives the connection to use. */
@FunctionalInterface
public interface SqlWork<T> {
    T run(Connection c) throws SQLException;
}
