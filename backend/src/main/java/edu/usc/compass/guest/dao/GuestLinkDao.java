package edu.usc.compass.guest.dao;

import edu.usc.compass.guest.model.GuestModels.GuestLink;
import edu.usc.compass.tasks.model.EffortLevel;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL for guest_links, plus the read-only queries behind the public guest view. Owner: Ramanjot.
 *
 * <p>Links come back with {@code status = null}: status depends on "now", so GuestLinkService fills it in with the
 * injected Clock (see {@code GuestLinkService.withStatus}).
 *
 * <p>The guest-view queries select ONLY the columns a guest may see (Black box 7). Keep it that way: never add
 * emails, descriptions of tasks, check-in data or anything from another project here.
 */
public class GuestLinkDao {

    private static final String LINK_COLUMNS =
            "link_id, project_id, created_by, created_at, expires_at, revoked_at";

    /** Project fields a guest may see. */
    public record GuestProjectRow(String name, String description) {
    }

    /** Task fields a guest may see, plus effort (used only to compute overall progress, never sent). */
    public record GuestTaskRow(String title, TaskStatus status, EffortLevel effort) {
    }

    public long insert(Connection c, long projectId, String tokenHash, long createdBy, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO guest_links (project_id, token_hash, created_by, expires_at) VALUES (?, ?, ?, ?) "
                + "RETURNING link_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setString(2, tokenHash);
            ps.setLong(3, createdBy);
            setInstant(ps, 4, expiresAt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Newest first. */
    public List<GuestLink> findByProject(Connection c, long projectId) throws SQLException {
        String sql = "SELECT " + LINK_COLUMNS + " FROM guest_links WHERE project_id = ? ORDER BY created_at DESC, link_id DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<GuestLink> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(map(rs));
                }
                return links;
            }
        }
    }

    public Optional<GuestLink> findById(Connection c, long linkId) throws SQLException {
        return findOne(c, "SELECT " + LINK_COLUMNS + " FROM guest_links WHERE link_id = ?", linkId);
    }

    /** Same as findById but locks the row so concurrent revokes record one audit event. */
    public Optional<GuestLink> findForUpdate(Connection c, long linkId) throws SQLException {
        return findOne(c, "SELECT " + LINK_COLUMNS + " FROM guest_links WHERE link_id = ? FOR UPDATE", linkId);
    }

    public Optional<GuestLink> findByTokenHash(Connection c, String tokenHash) throws SQLException {
        String sql = "SELECT " + LINK_COLUMNS + " FROM guest_links WHERE token_hash = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Sets revoked_at once; a link that is already revoked keeps its original time. */
    public void revoke(Connection c, long linkId, Instant now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE guest_links SET revoked_at = ? WHERE link_id = ? AND revoked_at IS NULL")) {
            setInstant(ps, 1, now);
            ps.setLong(2, linkId);
            ps.executeUpdate();
        }
    }

    // ---- Guest view (approved fields only) -------------------------------

    public Optional<GuestProjectRow> findProjectForGuest(Connection c, long projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name, description FROM projects WHERE project_id = ?")) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new GuestProjectRow(rs.getString(1), rs.getString(2))) : Optional.empty();
            }
        }
    }

    /** Real tasks only (no pending or rejected suggestions), soonest due first. */
    public List<GuestTaskRow> findTasksForGuest(Connection c, long projectId) throws SQLException {
        String sql = "SELECT title, status, effort FROM tasks "
                + "WHERE project_id = ? AND (suggestion_status IS NULL OR suggestion_status = 'APPROVED') "
                + "ORDER BY due_date ASC NULLS LAST, task_id ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<GuestTaskRow> tasks = new ArrayList<>();
                while (rs.next()) {
                    tasks.add(new GuestTaskRow(rs.getString(1), TaskStatus.valueOf(rs.getString(2)),
                            EffortLevel.valueOf(rs.getString(3))));
                }
                return tasks;
            }
        }
    }

    /** Display names of the project's team members, alphabetical. Never emails. */
    public List<String> findMemberDisplayNamesForGuest(Connection c, long projectId) throws SQLException {
        String sql = "SELECT u.display_name FROM projects p "
                + "JOIN team_memberships m ON m.team_id = p.team_id "
                + "JOIN users u ON u.user_id = m.user_id "
                + "WHERE p.project_id = ? ORDER BY lower(u.display_name), u.user_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                return names;
            }
        }
    }

    // ---- helpers ----------------------------------------------------------

    private Optional<GuestLink> findOne(Connection c, String sql, long linkId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, linkId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static GuestLink map(ResultSet rs) throws SQLException {
        return new GuestLink(
                rs.getLong("link_id"),
                rs.getLong("project_id"),
                rs.getLong("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("revoked_at")),
                null);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }
}
