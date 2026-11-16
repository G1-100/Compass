package edu.usc.compass.sprints.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.sprints.model.SprintModels.Sprint;
import edu.usc.compass.sprints.model.SprintModels.SprintStatus;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** SQL for sprints. Owner: Siya. */
public class SprintDao {

    public Optional<Sprint> findById(Connection c, long sprintId) {
        throw Todo.of("Siya", "SprintDao.findById");
    }

    /** SELECT ... FOR UPDATE — the first step of CompleteSprint so two completions run one at a time. */
    public Optional<Sprint> findForUpdate(Connection c, long sprintId) {
        throw Todo.of("Siya", "SprintDao.findForUpdate");
    }

    public List<Sprint> findByProject(Connection c, long projectId) {
        throw Todo.of("Siya", "SprintDao.findByProject");
    }

    public long insert(Connection c, long projectId, String name, LocalDate start, LocalDate end) {
        throw Todo.of("Siya", "SprintDao.insert");
    }

    public boolean update(Connection c, long sprintId, String name, LocalDate start, LocalDate end, int expectedVersion) {
        throw Todo.of("Siya", "SprintDao.update");
    }

    /** Sets status (and completed_at when COMPLETED) and version = newVersion. */
    public void updateStatus(Connection c, long sprintId, SprintStatus status, int newVersion) {
        throw Todo.of("Siya", "SprintDao.updateStatus");
    }

    public void delete(Connection c, long sprintId) {
        throw Todo.of("Siya", "SprintDao.delete");
    }
}
