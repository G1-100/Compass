package edu.usc.compass.tasks.dao;

import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.model.TaskModels.ActivityEntry;
import edu.usc.compass.tasks.model.TaskModels.ActivityType;

import java.sql.Connection;
import java.util.List;

/** SQL for task_activity. Owner: Adam. */
public class ActivityDao {

    public void insert(Connection c, Long taskId, Long sprintId, long actorUserId, ActivityType type, String oldValue, String newValue) {
        throw Todo.of("Adam", "ActivityDao.insert");
    }

    /** Chronological order. */
    public List<ActivityEntry> findByTask(Connection c, long taskId, PageRequest page) {
        throw Todo.of("Adam", "ActivityDao.findByTask");
    }

    public long countByTask(Connection c, long taskId) {
        throw Todo.of("Adam", "ActivityDao.countByTask");
    }
}
