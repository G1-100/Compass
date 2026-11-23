package edu.usc.compass.checkins;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.usc.compass.auth.PermissionService;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.checkins.CheckinDtos.BlockerInput;
import edu.usc.compass.checkins.CheckinDtos.ScheduleCheckinRequest;
import edu.usc.compass.checkins.CheckinDtos.SubmitUpdateRequest;
import edu.usc.compass.checkins.CheckinDtos.UpdateCheckinRequest;
import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.checkins.model.CheckinModels.Checkin;
import edu.usc.compass.checkins.model.CheckinModels.CheckinStatus;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.ErrorCode;
import edu.usc.compass.common.Json;
import edu.usc.compass.common.Severity;
import edu.usc.compass.common.VersionRequest;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.db.SqlWork;
import edu.usc.compass.notifications.NotificationService;
import edu.usc.compass.projects.dao.ProjectDao;
import edu.usc.compass.tasks.dao.TaskDao;
import edu.usc.compass.teams.model.TeamRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CheckinServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T20:00:00Z");
    private static final Instant FUTURE = NOW.plusSeconds(3600);
    private static final AuthContext LEAD = new AuthContext(2, "lead@usc.edu", "Lena Lead",
            SystemRole.USER, Map.of(1L, TeamRole.LEAD));

    private final Connection connection = mock(Connection.class);
    private final CheckinDao dao = mock(CheckinDao.class);
    private CheckinService service;

    @BeforeEach
    void setUp() throws Exception {
        Database db = mock(Database.class);
        when(db.inTransaction(any())).thenAnswer(call -> call.<SqlWork<?>>getArgument(0).run(connection));
        service = new CheckinService(db, dao, mock(PermissionService.class), mock(NotificationService.class),
                new TaskDao(), new ProjectDao(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unit13RequiresStrictlyFutureTime() throws Exception {
        for (Instant time : List.of(NOW.minusSeconds(1), NOW, NOW.plusNanos(1))) {
            var error = assertThrows(ApiException.class, () -> service.schedule(LEAD, schedule(time, null, null)));
            assertEquals(Map.of("scheduledAt", "Scheduled at must be in the future."), error.fields());
        }
        var missing = assertThrows(ApiException.class, () -> service.schedule(LEAD, schedule(null, null, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, missing.code());
        verifyNoInteractions(dao);

        mockSuccessfulSchedule();
        var parsed = Json.read("""
                {"teamId":1,"scheduledAt":"2026-11-21T13:00:00-08:00"}
                """, ScheduleCheckinRequest.class);
        assertEquals(FUTURE, service.schedule(LEAD, parsed).scheduledAt());
    }

    @Test
    void unit14ValidatesLengthsAndLocationSchemes() throws Exception {
        mockSuccessfulSchedule();
        String description = "🧭".repeat(2_000);
        for (String location : List.of("A".repeat(300), "Room 204, Science Hall",
                "http://example.com/meeting", "HTTPS://example.com/meeting")) {
            var saved = service.schedule(LEAD, schedule(FUTURE, description, location));
            assertEquals(description, saved.description());
            assertEquals(location, saved.location());
        }
        for (String location : List.of("A".repeat(301), "ftp://example.com/meeting",
                "javascript:alert(1)", "file:/tmp/meeting", "https://", "https://example.com:99999")) {
            var error = assertThrows(ApiException.class, () -> service.schedule(LEAD, schedule(FUTURE, null, location)));
            assertEquals(List.of("location"), List.copyOf(error.fields().keySet()));
        }
        var tooLong = assertThrows(ApiException.class,
                () -> service.schedule(LEAD, schedule(FUTURE, description + "x", "Room 3")));
        assertEquals(List.of("description"), List.copyOf(tooLong.fields().keySet()));
    }

    @Test
    void rejectsMissingTeamIdAndInvalidMeetingVersions() throws Exception {
        var missingTeam = assertThrows(ApiException.class,
                () -> service.schedule(LEAD, new ScheduleCheckinRequest(0, FUTURE, null, null)));
        assertEquals(Set.of("teamId"), missingTeam.fields().keySet());

        when(dao.findForUpdate(connection, 1)).thenReturn(Optional.of(checkin(FUTURE, null, null)));
        var missingVersion = assertThrows(ApiException.class, () -> service.update(LEAD, 1,
                new UpdateCheckinRequest(null, null, null, 0)));
        assertEquals(Set.of("version"), missingVersion.fields().keySet());
        var negativeVersion = assertThrows(ApiException.class, () -> service.complete(LEAD, 1, new VersionRequest(-1)));
        assertEquals(Set.of("version"), negativeVersion.fields().keySet());
    }

    @Test
    void rejectsNullCharactersBeforeWritingTextToPostgres() throws Exception {
        var invalid = "Text\0with a null character";
        var meetingError = assertThrows(ApiException.class,
                () -> service.schedule(LEAD, schedule(FUTURE, invalid, invalid)));
        assertEquals(Set.of("description", "location"), meetingError.fields().keySet());

        when(dao.findForShare(connection, 1)).thenReturn(Optional.of(checkin(FUTURE, null, null)));
        var request = new SubmitUpdateRequest(0, invalid, invalid,
                List.of(new BlockerInput(null, invalid, Severity.LOW, null)));
        var progressError = assertThrows(ApiException.class, () -> service.submitMyUpdate(LEAD, 1, request));
        assertEquals(Set.of("completedWork", "upcomingTasks", "blockers[0].description"), progressError.fields().keySet());
    }

    @Test
    void unit19RequiresAVersionAndValidatesProgressFields() throws Exception {
        when(dao.findForShare(connection, 1)).thenReturn(Optional.of(checkin(FUTURE, null, null)));
        var missingVersion = Json.read("""
                {"completedWork":"Done","upcomingTasks":"Next","blockers":[]}
                """, SubmitUpdateRequest.class);
        var error = assertThrows(ApiException.class, () -> service.submitMyUpdate(LEAD, 1, missingVersion));
        assertEquals(Map.of("version", "Version is required."), error.fields());

        var bad = new SubmitUpdateRequest(-1, "a".repeat(2_001), "b".repeat(2_001),
                List.of(new BlockerInput(null, "c".repeat(501), null, null)));
        var validation = assertThrows(ApiException.class, () -> service.submitMyUpdate(LEAD, 1, bad));
        assertEquals(Set.of("version", "completedWork", "upcomingTasks",
                "blockers[0].description", "blockers[0].severity"), validation.fields().keySet());

        var empty = new SubmitUpdateRequest(0, "", "", List.of(new BlockerInput(null, " ", Severity.LOW, null)));
        var emptyDescription = assertThrows(ApiException.class, () -> service.submitMyUpdate(LEAD, 1, empty));
        assertEquals(Set.of("blockers[0].description"), emptyDescription.fields().keySet());

        assertThrows(JsonProcessingException.class, () -> Json.read("""
                {"version":0,"blockers":[{"description":"Review","severity":"URGENT"}]}
                """, SubmitUpdateRequest.class));
    }

    private void mockSuccessfulSchedule() throws Exception {
        when(dao.insert(eq(connection), eq(1L), any(Instant.class), nullable(String.class), nullable(String.class),
                anyLong())).thenAnswer(call -> Optional.of(checkin(
                call.getArgument(2), call.getArgument(3), call.getArgument(4))));
        when(dao.findRecipients(connection, 1, false)).thenReturn(List.of(LEAD.userId()));
    }

    private static ScheduleCheckinRequest schedule(Instant scheduledAt, String description, String location) {
        return new ScheduleCheckinRequest(1, scheduledAt, description, location);
    }

    private static Checkin checkin(Instant scheduledAt, String description, String location) {
        return new Checkin(1, 1, description, scheduledAt, location, CheckinStatus.SCHEDULED, LEAD.userId(),
                NOW, null, 1);
    }
}
