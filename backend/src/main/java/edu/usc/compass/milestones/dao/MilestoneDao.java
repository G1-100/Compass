package edu.usc.compass.milestones.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.milestones.model.MilestoneModels.LinkedTask;
import edu.usc.compass.milestones.model.MilestoneModels.Milestone;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** SQL for milestones and milestone_tasks. Owner: Siya. */
public class MilestoneDao {

    public Optional<Milestone> findById(Connection c, long milestoneId) {
        throw Todo.of("Siya", "MilestoneDao.findById");
    }

    public List<Milestone> findByProject(Connection c, long projectId) {
        throw Todo.of("Siya", "MilestoneDao.findByProject");
    }

    public List<Long> findLinkedTaskIds(Connection c, long milestoneId) {
        throw Todo.of("Siya", "MilestoneDao.findLinkedTaskIds");
    }

    /** JOIN tasks to get each linked task's effort and status. */
    public List<LinkedTask> findLinkedTasks(Connection c, long milestoneId) {
        throw Todo.of("Siya", "MilestoneDao.findLinkedTasks");
    }

    public long insert(Connection c, long projectId, String name, String description, LocalDate targetDate) {
        throw Todo.of("Siya", "MilestoneDao.insert");
    }

    /** Changing the milestone date must NOT change any task's due date. */
    public boolean update(Connection c, long milestoneId, String name, String description, LocalDate targetDate, int expectedVersion) {
        throw Todo.of("Siya", "MilestoneDao.update");
    }

    /** Delete + insert milestone_tasks rows and bump the milestone version. */
    public void replaceLinkedTasks(Connection c, long milestoneId, List<Long> taskIds) {
        throw Todo.of("Siya", "MilestoneDao.replaceLinkedTasks");
    }

    /** Linked tasks are kept (only milestone_tasks rows cascade). */
    public void delete(Connection c, long milestoneId) {
        throw Todo.of("Siya", "MilestoneDao.delete");
    }
}
