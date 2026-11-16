package edu.usc.compass.teams.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.teams.model.TeamModels.Membership;
import edu.usc.compass.teams.model.TeamRole;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SQL for team_memberships. Owner: Solomon. SHARED: auth, tasks, check-ins and workload read from it. */
public class MembershipDao {

    /** team_id -> role for every team the user is in. Used by AuthService.authenticate on every request. */
    public Map<Long, TeamRole> findRolesForUser(Connection c, long userId) {
        throw Todo.of("Solomon", "MembershipDao.findRolesForUser");
    }

    public List<Membership> findByTeam(Connection c, long teamId) {
        throw Todo.of("Solomon", "MembershipDao.findByTeam");
    }

    public Optional<Membership> find(Connection c, long teamId, long userId) {
        throw Todo.of("Solomon", "MembershipDao.find");
    }

    /** SELECT ... WHERE team_id = ? AND role = 'LEAD' FOR UPDATE (Last-Lead Protection). */
    public List<Membership> findLeadsForUpdate(Connection c, long teamId) {
        throw Todo.of("Solomon", "MembershipDao.findLeadsForUpdate");
    }

    public Optional<Membership> findForUpdate(Connection c, long teamId, long userId) {
        throw Todo.of("Solomon", "MembershipDao.findForUpdate");
    }

    public long insert(Connection c, long teamId, long userId, TeamRole role) {
        throw Todo.of("Solomon", "MembershipDao.insert");
    }

    /** Sets role and version = newVersion. */
    public void updateRole(Connection c, long membershipId, TeamRole role, int newVersion) {
        throw Todo.of("Solomon", "MembershipDao.updateRole");
    }

    public void delete(Connection c, long membershipId) {
        throw Todo.of("Solomon", "MembershipDao.delete");
    }
}
