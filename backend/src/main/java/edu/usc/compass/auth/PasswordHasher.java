package edu.usc.compass.auth;

import edu.usc.compass.common.Todo;

/**
 * Adaptive one-way password hashing. Use BCrypt (org.mindrot.jbcrypt.BCrypt, already in pom.xml).
 * Never log or return passwords or hashes.
 * Owner: Gavin.
 */
public class PasswordHasher {

    /** Returns a bcrypt hash of the password (BCrypt.hashpw with BCrypt.gensalt()). */
    public String hash(String rawPassword) {
        throw Todo.of("Gavin", "PasswordHasher.hash");
    }

    /** True if rawPassword matches the stored hash (BCrypt.checkpw). False for a null hash. */
    public boolean verify(String rawPassword, String storedHash) {
        throw Todo.of("Gavin", "PasswordHasher.verify");
    }
}
