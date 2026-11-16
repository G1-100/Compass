package edu.usc.compass.tasks;

import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.tasks.dao.ActivityDao;
import edu.usc.compass.tasks.model.TaskModels.ActivityEntry;
import edu.usc.compass.tasks.model.TaskModels.ActivityType;

import java.sql.Connection;

/** Change history for the task activity modal. Owner: Adam. Tests: Regression 7, Stress 17. */
public class ActivityService {

    private final Database db;
    private final ActivityDao activityDao;

    public ActivityService(Database db, ActivityDao activityDao) {
        this.db = db;
        this.activityDao = activityDao;
    }

    /**
     * SHARED CONTRACT (Siya's CompleteSprint calls this as RecordActivity). Inserts one task_activity row
     * inside the caller's transaction. Pass taskId for task events, sprintId for sprint events.
     * Stub: no-op until implemented. Viewing a task must never call this.
     */
    public void record(Connection c, Long taskId, Long sprintId, long actorUserId, ActivityType type,
                       String oldValue, String newValue) {
        Todo.noop("Adam", "ActivityService.record");
    }

    /** GET /api/tasks/{taskId}/activity. Team member. Chronological, paged. */
    public Page<ActivityEntry> list(AuthContext ctx, long taskId, PageRequest page) {
        throw Todo.of("Adam", "ActivityService.list");
    }
}
