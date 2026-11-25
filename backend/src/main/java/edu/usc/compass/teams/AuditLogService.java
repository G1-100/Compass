package edu.usc.compass.teams;

import edu.usc.compass.auth.Permission;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.teams.dao.AuditDao;
import edu.usc.compass.teams.model.TeamModels.AuditEvent;
import edu.usc.compass.teams.model.TeamModels.AuditEventType;

import java.sql.Connection;
import java.sql.SQLException;

/** Audit trail for sensitive team actions. Owner: Solomon. */
public class AuditLogService {

    private final Database db;
    private final AuditDao auditDao;
    private final PermissionService permissions;

    public AuditLogService(Database db, AuditDao auditDao, PermissionService permissions) {
        this.db = db;
        this.auditDao = auditDao;
        this.permissions = permissions;
    }

    /**
     * SHARED CONTRACT (Ramanjot calls this for guest links). Inserts an audit_log row inside the caller's
     * transaction, so a failed audit write rolls back the change it records (Unit 6).
     */
    public void record(Connection c, long teamId, Long actorUserId, Long targetUserId, AuditEventType type, String details)
            throws SQLException {
        auditDao.insert(
                c,
                teamId,
                actorUserId,
                targetUserId,
                type,
                details
        );
    }

    /** GET /api/teams/{teamId}/audit-log. Team lead. Newest first. */
    public Page<AuditEvent> list(AuthContext ctx, long teamId, PageRequest page) {
        permissions.require(
                ctx,
                Permission.VIEW_AUDIT_LOG,
                teamId
        );
    
        return db.withConnection(c -> {
            var events = auditDao.findByTeam(c, teamId, page);
            long total = auditDao.countByTeam(c, teamId);
    
            return Page.of(events, page, total);
        });
    }
}
