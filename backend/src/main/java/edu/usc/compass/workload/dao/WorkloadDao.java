package edu.usc.compass.workload.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.workload.model.WorkloadModels.Availability;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** SQL for member_availability. Owner: Liv. */
public class WorkloadDao {

    /** INSERT ... ON CONFLICT (project_id, user_id) DO UPDATE. */
    public void upsertAvailability(Connection c, long projectId, long userId, BigDecimal hoursPerWeek, boolean reportsOverload) {
        throw Todo.of("Liv", "WorkloadDao.upsertAvailability");
    }

    public List<Availability> findAvailability(Connection c, long projectId) {
        throw Todo.of("Liv", "WorkloadDao.findAvailability");
    }

    public Optional<Availability> findAvailability(Connection c, long projectId, long userId) {
        throw Todo.of("Liv", "WorkloadDao.findAvailability(user)");
    }

    /** Ids of active (non-archived) projects, for the daily risk sweep. */
    public List<Long> findActiveProjectIds(Connection c) {
        throw Todo.of("Liv", "WorkloadDao.findActiveProjectIds");
    }
}
