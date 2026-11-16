package edu.usc.compass.auth.model;

import java.time.Instant;

/** A row of users, safe to return to clients (no password hash). */
public record User(
        long userId,
        String email,
        String displayName,
        SystemRole systemRole,
        boolean activated,
        Instant createdAt,
        int version) {
}
