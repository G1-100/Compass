package edu.usc.compass.teams.dao;

import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.teams.model.TeamModels.AuditEvent;
import edu.usc.compass.teams.model.TeamModels.AuditEventType;

import java.sql.Connection;
import java.util.List;

/** SQL for audit_log. Owner: Solomon. */
public class AuditDao {

    public void insert(Connection c, long teamId, Long actorUserId, Long targetUserId, AuditEventType type, String details) {
        throw Todo.of("Solomon", "AuditDao.insert");
    }

    public List<AuditEvent> findByTeam(Connection c, long teamId, PageRequest page) {
        throw Todo.of("Solomon", "AuditDao.findByTeam");
    }

    public long countByTeam(Connection c, long teamId) {
        throw Todo.of("Solomon", "AuditDao.countByTeam");
    }
}
