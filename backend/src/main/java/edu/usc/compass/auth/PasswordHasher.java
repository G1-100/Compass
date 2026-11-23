package edu.usc.compass.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Adaptive one-way password hashing with BCrypt. Never log or return passwords or hashes.
 * Owner: Gavin.
 */
public class PasswordHasher {

    /** Cost factor 12 is about 250 ms per hash on a laptop: slow for attackers, fine for one login. */
    private static final int COST = 12;

    /**
     * A real hash of a random value. When the email is unknown we still run one bcrypt check against this,
     * so a login for a missing account takes as long as one with a wrong password (no account probing by timing).
     */
    static final String DUMMY_HASH = BCrypt.hashpw("compass-dummy-password-1", BCrypt.gensalt(COST));

    /** Returns a bcrypt hash of the password. */
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("password is required");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    /** True if rawPassword matches the stored hash. False for a null or malformed hash. */
    public boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, storedHash);
        } catch (IllegalArgumentException e) {
            return false;   // not a bcrypt hash
        }
    }
}
