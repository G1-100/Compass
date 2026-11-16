package edu.usc.compass.guest.dao;

import edu.usc.compass.common.Todo;
import edu.usc.compass.guest.model.GuestModels.GuestLink;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SQL for guest_links. Owner: Ramanjot. */
public class GuestLinkDao {

    public long insert(Connection c, long projectId, String tokenHash, long createdBy, Instant expiresAt) {
        throw Todo.of("Ramanjot", "GuestLinkDao.insert");
    }

    public List<GuestLink> findByProject(Connection c, long projectId) {
        throw Todo.of("Ramanjot", "GuestLinkDao.findByProject");
    }

    public Optional<GuestLink> findByTokenHash(Connection c, String tokenHash) {
        throw Todo.of("Ramanjot", "GuestLinkDao.findByTokenHash");
    }

    public void revoke(Connection c, long linkId, Instant now) {
        throw Todo.of("Ramanjot", "GuestLinkDao.revoke");
    }
}
