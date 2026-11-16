package edu.usc.compass.teams.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.teams.model.TeamModels.Invitation;
import edu.usc.compass.teams.model.TeamModels.InvitationStatus;
import edu.usc.compass.teams.model.TeamRole;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SQL for team_invitations. Owner: Solomon. */
public class InvitationDao {

    public long insert(Connection c, long teamId, String inviteeEmail, TeamRole role, long invitedBy, Instant expiresAt) {
        throw Todo.of("Solomon", "InvitationDao.insert");
    }

    public Optional<Invitation> findForUpdate(Connection c, long invitationId) {
        throw Todo.of("Solomon", "InvitationDao.findForUpdate");
    }

    public List<Invitation> findByTeam(Connection c, long teamId) {
        throw Todo.of("Solomon", "InvitationDao.findByTeam");
    }

    /** Case-insensitive email match. */
    public List<Invitation> findPendingByEmail(Connection c, String email) {
        throw Todo.of("Solomon", "InvitationDao.findPendingByEmail");
    }

    public boolean hasPending(Connection c, long teamId, String email) {
        throw Todo.of("Solomon", "InvitationDao.hasPending");
    }

    public void setStatus(Connection c, long invitationId, InvitationStatus status, Instant respondedAt) {
        throw Todo.of("Solomon", "InvitationDao.setStatus");
    }
}
