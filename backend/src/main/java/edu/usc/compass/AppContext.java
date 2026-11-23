package edu.usc.compass;

import edu.usc.compass.auth.AuthController;
import edu.usc.compass.auth.AuthService;
import edu.usc.compass.auth.GoogleTokenVerifier;
import edu.usc.compass.auth.LoginLockoutService;
import edu.usc.compass.auth.PasswordHasher;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.SessionManager;
import edu.usc.compass.auth.UserService;
import edu.usc.compass.auth.VerificationCodeService;
import edu.usc.compass.auth.dao.LoginLockoutDao;
import edu.usc.compass.auth.dao.SessionDao;
import edu.usc.compass.auth.dao.UserDao;
import edu.usc.compass.auth.dao.VerificationCodeDao;
import edu.usc.compass.checkins.CheckinController;
import edu.usc.compass.checkins.CheckinReminderJob;
import edu.usc.compass.checkins.CheckinService;
import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.AppConfig;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.jobs.JobScheduler;
import edu.usc.compass.common.web.HealthController;
import edu.usc.compass.common.web.Router;
import edu.usc.compass.guest.GuestController;
import edu.usc.compass.guest.GuestLinkService;
import edu.usc.compass.guest.dao.GuestLinkDao;
import edu.usc.compass.milestones.MilestoneController;
import edu.usc.compass.milestones.MilestoneProgressCalculator;
import edu.usc.compass.milestones.MilestoneService;
import edu.usc.compass.milestones.dao.MilestoneDao;
import edu.usc.compass.notifications.EmailQueue;
import edu.usc.compass.notifications.EmailSenderJob;
import edu.usc.compass.notifications.LoggingMailer;
import edu.usc.compass.notifications.Mailer;
import edu.usc.compass.notifications.NotificationController;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.notifications.SmtpMailer;
import edu.usc.compass.notifications.dao.EmailQueueDao;
import edu.usc.compass.notifications.dao.NotificationDao;
import edu.usc.compass.projects.DashboardService;
import edu.usc.compass.projects.ProjectController;
import edu.usc.compass.projects.ProjectService;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.sprints.SprintController;
import edu.usc.compass.sprints.SprintService;
import edu.usc.compass.sprints.dao.SprintDao;
import edu.usc.compass.tasks.ActivityService;
import edu.usc.compass.tasks.CommentService;
import edu.usc.compass.tasks.MentionParser;
import edu.usc.compass.tasks.SuggestionService;
import edu.usc.compass.tasks.TaskController;
import edu.usc.compass.tasks.TaskService;
import edu.usc.compass.tasks.dao.ActivityDao;
import edu.usc.compass.tasks.dao.CommentDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.teams.AuditLogService;
import edu.usc.compass.teams.InvitationService;
import edu.usc.compass.teams.MembershipService;
import edu.usc.compass.teams.TeamController;
import edu.usc.compass.teams.TeamService;
import edu.usc.compass.teams.dao.AuditDao;
import edu.usc.compass.teams.dao.InvitationDao;
import edu.usc.compass.teams.dao.MembershipDao;
import edu.usc.compass.teams.dao.TeamDao;
import edu.usc.compass.workload.AlertService;
import edu.usc.compass.workload.DriftDetector;
import edu.usc.compass.workload.LateRiskDetector;
import edu.usc.compass.workload.RiskDetectionJob;
import edu.usc.compass.workload.WorkloadCalculator;
import edu.usc.compass.workload.WorkloadController;
import edu.usc.compass.workload.WorkloadService;
import edu.usc.compass.workload.dao.AlertDao;
import edu.usc.compass.workload.dao.WorkloadDao;
import jakarta.servlet.ServletContext;

import java.time.Clock;

/**
 * Builds every DAO, service and controller once at startup (plain constructor injection, no framework).
 * When you add a dependency to your service's constructor, update the matching line here.
 * PLATFORM — complete. Each block is labeled with its owner to keep merge conflicts small.
 */
public final class AppContext implements AutoCloseable {

    private static final String ATTRIBUTE = AppContext.class.getName();

    private final AppConfig config;
    private final Database db;
    private final Router router = new Router();
    private final JobScheduler scheduler = new JobScheduler();
    private final AuthService authService;
    private final SessionManager sessionManager;

    public AppContext(AppConfig config) {
        this.config = config;
        this.db = Database.fromConfig(config);
        Clock clock = Clock.systemUTC();

        // ---- DAOs (stateless) ----
        UserDao userDao = new UserDao();
        SessionDao sessionDao = new SessionDao();
        VerificationCodeDao codeDao = new VerificationCodeDao();
        LoginLockoutDao lockoutDao = new LoginLockoutDao();
        TeamDao teamDao = new TeamDao();
        MembershipDao membershipDao = new MembershipDao();
        InvitationDao invitationDao = new InvitationDao();
        AuditDao auditDao = new AuditDao();
        ProjectDao projectDao = new ProjectDao();
        TaskDao taskDao = new TaskDao();
        CommentDao commentDao = new CommentDao();
        ActivityDao activityDao = new ActivityDao();
        SprintDao sprintDao = new SprintDao();
        MilestoneDao milestoneDao = new MilestoneDao();
        CheckinDao checkinDao = new CheckinDao();
        WorkloadDao workloadDao = new WorkloadDao();
        AlertDao alertDao = new AlertDao();
        GuestLinkDao guestLinkDao = new GuestLinkDao();
        NotificationDao notificationDao = new NotificationDao();
        EmailQueueDao emailQueueDao = new EmailQueueDao();

        // ---- Shared building blocks ----
        PermissionService permissions = new PermissionService();                                       // Gavin
        EmailQueue emailQueue = new EmailQueue(emailQueueDao);                                         // Raegen
        ProjectService projectService = new ProjectService(db, projectDao, permissions);               // Solomon
        NotificationService notificationService =
                new NotificationService(db, notificationDao, emailQueue, projectService);              // Raegen
        AuditLogService auditLog = new AuditLogService(db, auditDao, permissions);                     // Solomon
        ActivityService activity = new ActivityService(db, activityDao);                               // Adam

        // ---- Workload and risk (Liv) ----
        AlertService alerts = new AlertService(db, alertDao, projectService, notificationService);
        WorkloadService workloadService =
                new WorkloadService(db, workloadDao, taskDao, new WorkloadCalculator(), alerts, projectService);
        LateRiskDetector lateRisk = new LateRiskDetector(checkinDao, workloadDao);
        DriftDetector drift = new DriftDetector(taskDao, commentDao, checkinDao, membershipDao, alerts);

        // ---- Auth (Gavin) ----
        this.sessionManager = new SessionManager(sessionDao, clock);
        this.authService = new AuthService(db, userDao, membershipDao, new PasswordHasher(), sessionManager,
                new LoginLockoutService(lockoutDao, clock), new VerificationCodeService(codeDao, clock),
                new GoogleTokenVerifier(config.googleClientId()), emailQueue);
        UserService userService = new UserService(db, userDao, permissions);

        // ---- Teams (Solomon) ----
        TeamService teamService = new TeamService(db, teamDao, membershipDao, permissions);
        MembershipService membershipService =
                new MembershipService(db, membershipDao, auditLog, notificationService, permissions);
        InvitationService invitationService = new InvitationService(db, invitationDao, membershipDao, emailQueue,
                notificationService, permissions, clock);

        // ---- Tasks (Adam) ----
        TaskService taskService = new TaskService(db, taskDao, projectService, permissions, activity,
                notificationService, workloadService, lateRisk, clock);
        SuggestionService suggestionService =
                new SuggestionService(db, taskDao, projectService, permissions, activity, notificationService);
        CommentService commentService = new CommentService(db, commentDao, taskDao, membershipDao, projectService,
                permissions, new MentionParser(), notificationService);

        // ---- Sprints and milestones (Siya) ----
        SprintService sprintService =
                new SprintService(db, sprintDao, taskDao, projectService, activity, notificationService);
        MilestoneService milestoneService =
                new MilestoneService(db, milestoneDao, new MilestoneProgressCalculator(), projectService, clock);

        // ---- Dashboard (Solomon) ----
        DashboardService dashboard = new DashboardService(db, projectService, taskDao, milestoneService, clock);

        // ---- Check-ins (Graham) ----
        CheckinService checkinService = new CheckinService(db, checkinDao, permissions, notificationService,
                taskDao, projectDao, clock);

        // ---- Guest links (Ramanjot) ----
        GuestLinkService guestLinkService = new GuestLinkService(db, guestLinkDao, projectService, taskDao,
                milestoneService, auditLog, config, clock);

        // ---- Routes ----
        new HealthController(db).register(router);
        new AuthController(authService, userService, sessionManager).register(router);
        new TeamController(teamService, membershipService, invitationService, auditLog).register(router);
        new ProjectController(projectService, dashboard).register(router);
        new TaskController(taskService, suggestionService, commentService, activity).register(router);
        new SprintController(sprintService).register(router);
        new MilestoneController(milestoneService).register(router);
        new CheckinController(checkinService).register(router);
        new WorkloadController(workloadService, alerts).register(router);
        new GuestController(guestLinkService).register(router);
        new NotificationController(notificationService).register(router);

        // ---- Background jobs ----
        Mailer mailer = config.smtpHost() == null ? new LoggingMailer() : new SmtpMailer(config);
        scheduler.register(new EmailSenderJob(db, emailQueueDao, mailer, clock));                       // Raegen
        scheduler.register(new CheckinReminderJob(db, checkinDao, notificationService, clock));          // Graham
        scheduler.register(new RiskDetectionJob(db, workloadDao, drift, lateRisk, alerts, clock));        // Liv
    }

    public void start() {
        if (config.jobsEnabled() && db.isConfigured()) {
            scheduler.start();
        }
    }

    public static AppContext get(ServletContext servletContext) {
        return (AppContext) servletContext.getAttribute(ATTRIBUTE);
    }

    void attach(ServletContext servletContext) {
        servletContext.setAttribute(ATTRIBUTE, this);
    }

    public AppConfig config() {
        return config;
    }

    public Router router() {
        return router;
    }

    public AuthService authService() {
        return authService;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    @Override
    public void close() {
        scheduler.close();
        db.close();
    }
}
