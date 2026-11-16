package edu.usc.compass.tasks;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.tasks.TaskDtos.AddCommentRequest;
import edu.usc.compass.tasks.dao.CommentDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.tasks.model.TaskModels.Comment;
import edu.usc.compass.teams.dao.MembershipDao;

/** Task comments with @mentions. Owner: Adam. Tests: Stress 3, 17, 18. */
public class CommentService {

    private final Database db;
    private final CommentDao commentDao;
    private final TaskDao taskDao;
    private final MembershipDao membershipDao;
    private final ProjectService projects;
    private final PermissionService permissions;
    private final MentionParser mentionParser;
    private final NotificationService notifications;

    public CommentService(Database db, CommentDao commentDao, TaskDao taskDao, MembershipDao membershipDao,
                          ProjectService projects, PermissionService permissions, MentionParser mentionParser,
                          NotificationService notifications) {
        this.db = db;
        this.commentDao = commentDao;
        this.taskDao = taskDao;
        this.membershipDao = membershipDao;
        this.projects = projects;
        this.permissions = permissions;
        this.mentionParser = mentionParser;
        this.notifications = notifications;
    }

    /** GET /api/tasks/{taskId}/comments. Team member. Oldest first, paged. */
    public Page<Comment> list(AuthContext ctx, long taskId, PageRequest page) {
        throw Todo.of("Adam", "CommentService.list");
    }

    /**
     * POST /api/tasks/{taskId}/comments. Team member. Text required, max 2000 characters (emoji count as 1).
     * Parse @mentions and send exactly one MENTION notification per mentioned member.
     */
    public Comment add(AuthContext ctx, long taskId, AddCommentRequest req) {
        throw Todo.of("Adam", "CommentService.add");
    }
}
