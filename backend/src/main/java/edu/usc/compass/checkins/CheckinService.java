package edu.usc.compass.checkins;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.checkins.CheckinDtos.ScheduleCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.SubmitUpdateRequest;
import edu.usc.compass.checkins.CheckinDtos.UpdateCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.When;
import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.checkins.model.CheckinModels.Blocker;
import edu.usc.compass.checkins.model.CheckinModels.Checkin;
import edu.usc.compass.checkins.model.CheckinModels.CheckinUpdate;
import edu.usc.compass.common.Todo;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;

import java.time.Clock;
import java.util.List;

/**
 * Team check-ins with member progress updates and blockers.
 * Owner: Graham. Tests: Unit 13-20, Regression 5 & 11, Stress 7 & 16.
 */
public class CheckinService {

    private final Database db;
    private final CheckinDao checkinDao;
    private final PermissionService permissions;
    private final NotificationService notifications;
    private final Clock clock;

    public CheckinService(Database db, CheckinDao checkinDao, PermissionService permissions,
                          NotificationService notifications, Clock clock) {
        this.db = db;
        this.checkinDao = checkinDao;
        this.permissions = permissions;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** GET /api/checkins?teamId=&when=upcoming|past. Team member. */
    public List<Checkin> list(AuthContext ctx, long teamId, When when) {
        throw Todo.of("Graham", "CheckinService.list");
    }

    /**
     * POST /api/checkins. Team lead. scheduledAt strictly in the future (UTC); description max 2000;
     * location max 300, plain text or an http/https link (reject other schemes like javascript:).
     * Notify members.
     */
    public Checkin schedule(AuthContext ctx, ScheduleCheckinRequest req) {
        throw Todo.of("Graham", "CheckinService.schedule");
    }

    /** GET /api/checkins/{checkinId}. Team member. */
    public Checkin get(AuthContext ctx, long checkinId) {
        throw Todo.of("Graham", "CheckinService.get");
    }

    /** PATCH /api/checkins/{checkinId}. Team lead. On reschedule, delete this check-in's reminder rows. Notify members. */
    public Checkin update(AuthContext ctx, long checkinId, UpdateCheckinRequest req) {
        throw Todo.of("Graham", "CheckinService.update");
    }

    /** POST .../cancel. Team lead. Only SCHEDULED can be canceled. Notify members. */
    public Checkin cancel(AuthContext ctx, long checkinId, VersionRequest req) {
        throw Todo.of("Graham", "CheckinService.cancel");
    }

    /** POST .../complete. Team lead. Locks updates; a second call changes nothing and sends no second email. */
    public Checkin complete(AuthContext ctx, long checkinId, VersionRequest req) {
        throw Todo.of("Graham", "CheckinService.complete");
    }

    /** GET .../updates. Team member. */
    public List<CheckinUpdate> listUpdates(AuthContext ctx, long checkinId) {
        throw Todo.of("Graham", "CheckinService.listUpdates");
    }

    /**
     * PUT .../updates/me. Team member. In ONE transaction: upsert the update, delete old blockers, insert new
     * ones (description max 500, valid severity, taskId optional). 409 if the check-in no longer accepts updates.
     * Must not change any task.
     */
    public CheckinUpdate submitMyUpdate(AuthContext ctx, long checkinId, SubmitUpdateRequest req) {
        throw Todo.of("Graham", "CheckinService.submitMyUpdate");
    }

    /** PATCH .../blockers/{blockerId}. Blocker author or team lead (403 otherwise). Sets resolvedAt/resolvedBy. */
    public Blocker resolveBlocker(AuthContext ctx, long checkinId, long blockerId) {
        throw Todo.of("Graham", "CheckinService.resolveBlocker");
    }
}
