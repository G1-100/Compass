package edu.usc.compass.tasks.dao;

import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.model.TaskModels.Comment;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SQL for task_comments. Owner: Adam. */
public class CommentDao {

    public long insert(Connection c, long taskId, long authorId, String text) {
        throw Todo.of("Adam", "CommentDao.insert");
    }

    /** Oldest first. */
    public List<Comment> findByTask(Connection c, long taskId, PageRequest page) {
        throw Todo.of("Adam", "CommentDao.findByTask");
    }

    public long countByTask(Connection c, long taskId) {
        throw Todo.of("Adam", "CommentDao.countByTask");
    }

    /** SHARED (drift detection). Latest comment the user posted on any task in the project. */
    public Optional<Instant> findLastCommentBy(Connection c, long projectId, long userId) {
        throw Todo.of("Adam", "CommentDao.findLastCommentBy");
    }
}
