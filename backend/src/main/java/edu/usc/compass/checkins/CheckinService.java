package edu.usc.compass.checkins;

import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.checkins.CheckinDtos.BlockerInput;
import edu.usc.compass.checkins.CheckinDtos.ScheduleCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.SubmitUpdateRequest;
import edu.usc.compass.checkins.CheckinDtos.UpdateCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.When;
import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.checkins.model.CheckinModels.Blocker;
import edu.usc.compass.checkins.model.CheckinModels.Checkin;
import edu.usc.compass.checkins.model.CheckinModels.CheckinStatus;
import edu.usc.compass.checkins.model.CheckinModels.CheckinUpdate;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Validator;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.notifications.model.NotificationModels.NotificationRequest;
import edu.usc.compass.notifications.model.NotificationModels.NotificationType;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.tasks.dao.TaskDao;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static edu.usc.compass.auth.Permission.MANAGE_CHECKINS;
import static edu.usc.compass.auth.Permission.SUBMIT_CHECKIN_UPDATE;
import static edu.usc.compass.auth.Permission.VIEW_WORKSPACE;
import static edu.usc.compass.common.Rules.CHECKIN_BLOCKER_LIMIT;
import static edu.usc.compass.common.Rules.CHECKIN_LOCATION_LIMIT;
import static edu.usc.compass.common.Rules.CHECKIN_TEXT_LIMIT;
import static edu.usc.compass.notifications.model.NotificationModels.NotificationType.CHECKIN_CANCELED;
import static edu.usc.compass.notifications.model.NotificationModels.NotificationType.CHECKIN_COMPLETED;
import static edu.usc.compass.notifications.model.NotificationModels.NotificationType.CHECKIN_SCHEDULED;
import static edu.usc.compass.notifications.model.NotificationModels.NotificationType.CHECKIN_UPDATED;

/** Team check-ins with member progress updates and blockers. Owner: Graham. */
public class CheckinService {

    private static final Pattern URL_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private final Database db;
    private final CheckinDao checkinDao;
    private final PermissionService permissions;
    private final NotificationService notifications;
    private final TaskDao tasks;
    private final ProjectDao projects;
    private final Clock clock;

    public CheckinService(Database db, CheckinDao checkinDao, PermissionService permissions,
                          NotificationService notifications, TaskDao tasks, ProjectDao projects, Clock clock) {
        this.db = db;
        this.checkinDao = checkinDao;
        this.permissions = permissions;
        this.notifications = notifications;
        this.tasks = tasks;
        this.projects = projects;
        this.clock = clock;
    }

    public List<Checkin> list(AuthContext ctx, long teamId, When when) {
        permissions.require(ctx, VIEW_WORKSPACE, teamId);
        return db.withConnection(c -> checkinDao.findByTeam(c, teamId, when == When.upcoming, Instant.now(clock)));
    }

    public Checkin schedule(AuthContext ctx, ScheduleCheckinRequest req) {
        new Validator().check(req.teamId() > 0, "teamId", "A positive team ID is required.").throwIfInvalid();
        permissions.require(ctx, MANAGE_CHECKINS, req.teamId());
        return db.inTransaction(c -> {
            Instant scheduledAt = toDatabaseTime(req.scheduledAt());
            validateMeetingFields(req.description(), req.location())
                    .required("scheduledAt", scheduledAt)
                    .future("scheduledAt", scheduledAt, Instant.now(clock))
                    .throwIfInvalid();
            Checkin created = checkinDao.insert(c, req.teamId(), scheduledAt, emptyToNull(req.description()),
                    emptyToNull(req.location()), ctx.userId()).orElseThrow(ApiException::notFound);
            notifyTeam(c, created, CHECKIN_SCHEDULED, "Check-in scheduled for " + created.scheduledAt() + ".");
            return created;
        });
    }

    public Checkin get(AuthContext ctx, long checkinId) {
        return db.withConnection(c -> {
            Checkin checkin = checkinDao.findById(c, checkinId).orElseThrow(ApiException::notFound);
            permissions.require(ctx, VIEW_WORKSPACE, checkin.teamId());
            return checkin;
        });
    }

    public Checkin update(AuthContext ctx, long checkinId, UpdateCheckinRequest req) {
        return db.inTransaction(c -> {
            Checkin current = checkinDao.findForUpdate(c, checkinId).orElseThrow(ApiException::notFound);
            permissions.require(ctx, MANAGE_CHECKINS, current.teamId());
            validateVersion(req.version());
            requireScheduled(current);
            if (current.version() != req.version()) throw ApiException.versionConflict(current);

            Instant scheduledAt = req.scheduledAt() == null ? current.scheduledAt() : toDatabaseTime(req.scheduledAt());
            boolean rescheduled = !scheduledAt.equals(current.scheduledAt());
            validateMeetingFields(req.description(), req.location())
                    .future("scheduledAt", rescheduled ? scheduledAt : null, Instant.now(clock))
                    .throwIfInvalid();

            String description = req.description() == null ? current.description() : emptyToNull(req.description());
            String location = req.location() == null ? current.location() : emptyToNull(req.location());
            if (!rescheduled && Objects.equals(description, current.description())
                    && Objects.equals(location, current.location())) {
                return current;
            }

            Checkin saved = checkinDao.update(c, checkinId, scheduledAt, description, location, req.version())
                    .orElseThrow(() -> ApiException.versionConflict(current));
            if (rescheduled) checkinDao.deleteReminders(c, checkinId);
            String message = rescheduled ? "Check-in rescheduled to " + saved.scheduledAt() + "."
                    : "Details changed for the check-in at " + saved.scheduledAt() + ".";
            notifyTeam(c, saved, CHECKIN_UPDATED, message);
            return saved;
        });
    }

    public Checkin cancel(AuthContext ctx, long checkinId, VersionRequest req) {
        return close(ctx, checkinId, req, CheckinStatus.CANCELED);
    }

    public Checkin complete(AuthContext ctx, long checkinId, VersionRequest req) {
        return close(ctx, checkinId, req, CheckinStatus.COMPLETED);
    }

    private Checkin close(AuthContext ctx, long checkinId, VersionRequest req, CheckinStatus status) {
        return db.inTransaction(c -> {
            Checkin current = checkinDao.findForUpdate(c, checkinId).orElseThrow(ApiException::notFound);
            permissions.require(ctx, MANAGE_CHECKINS, current.teamId());
            validateVersion(req.version());
            boolean completing = status == CheckinStatus.COMPLETED;
            if (completing && current.status() == status) return current;
            requireScheduled(current);

            Instant completedAt = completing ? Instant.now(clock) : null;
            Checkin saved = checkinDao.setStatus(c, checkinId, status, completedAt, req.version())
                    .orElseThrow(() -> ApiException.versionConflict(current));
            NotificationType type = completing ? CHECKIN_COMPLETED : CHECKIN_CANCELED;
            String action = completing ? "completed" : "canceled";
            notifyTeam(c, saved, type, "The check-in at " + saved.scheduledAt() + " was " + action + ".");
            return saved;
        });
    }

    public List<CheckinUpdate> listUpdates(AuthContext ctx, long checkinId) {
        return db.withConnection(c -> {
            Checkin checkin = checkinDao.findById(c, checkinId).orElseThrow(ApiException::notFound);
            permissions.require(ctx, VIEW_WORKSPACE, checkin.teamId());
            return checkinDao.findUpdates(c, checkinId);
        });
    }

    public CheckinUpdate submitMyUpdate(AuthContext ctx, long checkinId, SubmitUpdateRequest req) {
        List<BlockerInput> blockers = req.blockers() == null ? List.of() : req.blockers();
        return db.inTransaction(c -> {
            Checkin checkin = checkinDao.findForShare(c, checkinId).orElseThrow(ApiException::notFound);
            permissions.require(ctx, SUBMIT_CHECKIN_UPDATE, checkin.teamId());
            validateProgress(req, blockers);

            var current = checkinDao.findMemberUpdateForUpdate(c, checkinId, ctx.userId());
            if (current.isPresent()) return saveExisting(c, checkin, current.get(), req, blockers);

            requireScheduled(checkin);
            if (req.version() != 0) throw ApiException.versionConflict(null);
            validateBlockerLinks(c, checkin.teamId(), blockers, Set.of());
            var inserted = checkinDao.insertUpdate(c, checkinId, ctx.userId(), req.completedWork(),
                    req.upcomingTasks(), Instant.now(clock));
            if (inserted.isPresent()) {
                long updateId = inserted.get();
                for (BlockerInput blocker : blockers) {
                    checkinDao.insertBlocker(c, updateId, blocker);
                }
                return checkinDao.findUpdateById(c, updateId);
            }

            // A concurrent first save won the unique key. Lock and compare its committed update.
            var winner = checkinDao.findMemberUpdateForUpdate(c, checkinId, ctx.userId()).orElseThrow();
            return saveExisting(c, checkin, winner, req, blockers);
        });
    }

    private CheckinUpdate saveExisting(Connection c, Checkin checkin, CheckinUpdate current, SubmitUpdateRequest req,
                                       List<BlockerInput> blockers) throws SQLException {
        Map<Long, Blocker> saved = new HashMap<>();
        for (Blocker blocker : current.blockers()) {
            saved.put(blocker.blockerId(), blocker);
        }
        if (Objects.equals(current.completedWork(), req.completedWork())
                && Objects.equals(current.upcomingTasks(), req.upcomingTasks())
                && sameBlockers(saved, blockers, req.version() < current.version())) {
            return current;
        }

        requireScheduled(checkin);
        if (current.version() != req.version()) throw ApiException.versionConflict(current);
        validateBlockerLinks(c, checkin.teamId(), blockers, saved.keySet());

        long updateId = current.updateId();
        if (!checkinDao.updateProgress(c, updateId, req.version(), req.completedWork(),
                req.upcomingTasks(), Instant.now(clock))) {
            throw ApiException.versionConflict(current);
        }
        List<Long> retainedIds = blockers.stream().map(BlockerInput::blockerId).filter(Objects::nonNull).toList();
        checkinDao.deleteOmittedBlockers(c, updateId, retainedIds);
        for (BlockerInput blocker : blockers) {
            if (blocker.blockerId() == null) {
                checkinDao.insertBlocker(c, updateId, blocker);
            } else if (!editableFields(saved.get(blocker.blockerId())).equals(withoutId(blocker))) {
                checkinDao.updateBlocker(c, updateId, blocker);
            }
        }
        return checkinDao.findUpdateById(c, updateId);
    }

    public Blocker resolveBlocker(AuthContext ctx, long checkinId, long blockerId) {
        return db.inTransaction(c -> {
            Checkin checkin = checkinDao.findById(c, checkinId).orElseThrow(ApiException::notFound);
            permissions.require(ctx, VIEW_WORKSPACE, checkin.teamId());
            long author = checkinDao.lockBlockerAuthor(c, checkinId, blockerId).orElseThrow(ApiException::notFound);
            if (author != ctx.userId()) permissions.require(ctx, MANAGE_CHECKINS, checkin.teamId());

            Blocker blocker = checkinDao.findBlocker(c, blockerId).orElseThrow(ApiException::notFound);
            if (blocker.isResolved()) return blocker;
            return checkinDao.resolveBlocker(c, blockerId, ctx.userId(), Instant.now(clock));
        });
    }

    private void notifyTeam(Connection c, Checkin checkin, NotificationType type, String message) throws SQLException {
        var recipients = checkinDao.findRecipients(c, checkin.teamId(), type == CHECKIN_COMPLETED);
        if (!recipients.isEmpty()) {
            notifications.notify(c, new NotificationRequest(recipients, type, null, null, message, true));
        }
    }

    private static void requireScheduled(Checkin checkin) {
        if (!checkin.acceptsUpdates()) throw ApiException.invalidState("This check-in is closed.");
    }

    private static void validateVersion(int version) {
        if (version < 1) throw ApiException.validation("version", "A positive version is required.");
    }

    private static Validator validateMeetingFields(String description, String location) {
        var validator = new Validator();
        validateText(validator, "description", description, CHECKIN_TEXT_LIMIT);
        validateText(validator, "location", location, CHECKIN_LOCATION_LIMIT);
        return validator.check(validLocation(location), "location", "Enter a location or a valid HTTP or HTTPS link.");
    }

    private static boolean validLocation(String location) {
        if (location == null) return true;
        String value = location.strip();
        if (!URL_SCHEME.matcher(value).find()) return true;
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && uri.getPort() <= 65_535 && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static void validateProgress(SubmitUpdateRequest req, List<BlockerInput> blockers) {
        var validator = new Validator()
                .required("version", req.version())
                .range("version", req.version(), 0, Integer.MAX_VALUE);
        validateText(validator, "completedWork", req.completedWork(), CHECKIN_TEXT_LIMIT);
        validateText(validator, "upcomingTasks", req.upcomingTasks(), CHECKIN_TEXT_LIMIT);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < blockers.size(); i++) {
            BlockerInput blocker = blockers.get(i);
            String field = "blockers[" + i + "]";
            validator.required(field, blocker);
            if (blocker == null) continue;
            validator.required(field + ".description", blocker.description());
            validateText(validator, field + ".description", blocker.description(), CHECKIN_BLOCKER_LIMIT);
            validator.required(field + ".severity", blocker.severity())
                    .check(blocker.blockerId() == null || ids.add(blocker.blockerId()),
                            field + ".blockerId", "Each blocker ID can appear only once.");
        }
        validator.throwIfInvalid();
    }

    private static void validateText(Validator validator, String field, String value, int limit) {
        validator.maxLength(field, value, limit)
                .check(value == null || value.indexOf('\0') < 0, field, "Text cannot contain null characters.");
    }

    private void validateBlockerLinks(Connection c, long teamId, List<BlockerInput> blockers, Set<Long> savedIds)
            throws SQLException {
        for (int i = 0; i < blockers.size(); i++) {
            BlockerInput blocker = blockers.get(i);
            String field = "blockers[" + i + "]";
            if (blocker.blockerId() != null && !savedIds.contains(blocker.blockerId())) {
                throw ApiException.validation(field + ".blockerId", "This blocker does not belong to your update.");
            }
            if (blocker.taskId() != null && !taskBelongsToTeam(c, blocker.taskId(), teamId)) {
                throw ApiException.validation(field + ".taskId", "Choose a task from this check-in's team.");
            }
        }
    }

    private boolean taskBelongsToTeam(Connection c, long taskId, long teamId) throws SQLException {
        try {
            return projects.findTeamId(c, tasks.findProjectId(c, taskId)) == teamId;
        } catch (ApiException e) {
            if (e.code() == ErrorCode.NOT_FOUND) return false;
            throw e;
        }
    }

    /** Match explicit IDs first. An older request may have been retried before it saw the assigned IDs. */
    private static boolean sameBlockers(Map<Long, Blocker> saved, List<BlockerInput> submitted, boolean allowUnassignedIds) {
        if (saved.size() != submitted.size()) return false;
        Set<Long> matchedIds = new HashSet<>();
        for (BlockerInput input : submitted) {
            if (input.blockerId() == null) continue;
            Blocker match = saved.get(input.blockerId());
            if (match == null || !editableFields(match).equals(withoutId(input))) return false;
            matchedIds.add(input.blockerId());
        }
        if (matchedIds.size() == saved.size()) return true;
        if (!allowUnassignedIds) return false;

        Map<BlockerInput, Integer> remaining = new HashMap<>();
        for (Blocker blocker : saved.values()) {
            if (!matchedIds.contains(blocker.blockerId())) remaining.merge(editableFields(blocker), 1, Integer::sum);
        }
        for (BlockerInput input : submitted) {
            if (input.blockerId() != null) continue;
            Integer count = remaining.computeIfPresent(input, (content, matches) -> matches - 1);
            if (count == null || count < 0) return false;
        }
        return true;
    }

    private static BlockerInput editableFields(Blocker blocker) {
        return new BlockerInput(null, blocker.description(), blocker.severity(), blocker.taskId());
    }

    private static BlockerInput withoutId(BlockerInput blocker) {
        return new BlockerInput(null, blocker.description(), blocker.severity(), blocker.taskId());
    }

    private static Instant toDatabaseTime(Instant time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
