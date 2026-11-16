package edu.usc.compass.teams.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.teams.model.TeamModels.Team;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** SQL for teams. Owner: Solomon. */
public class TeamDao {

    public long insert(Connection c, String name, long createdBy) {
        throw Todo.of("Solomon", "TeamDao.insert");
    }

    public Optional<Team> findById(Connection c, long teamId) {
        throw Todo.of("Solomon", "TeamDao.findById");
    }

    public List<Team> findAll(Connection c) {
        throw Todo.of("Solomon", "TeamDao.findAll");
    }

    /** Returns false on version mismatch. */
    public boolean rename(Connection c, long teamId, String name, int expectedVersion) {
        throw Todo.of("Solomon", "TeamDao.rename");
    }

    /** ON DELETE CASCADE in schema.sql removes memberships, invitations, projects and check-ins. */
    public void delete(Connection c, long teamId) {
        throw Todo.of("Solomon", "TeamDao.delete");
    }
}
