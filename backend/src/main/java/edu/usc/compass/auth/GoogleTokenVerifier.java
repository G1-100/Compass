package edu.usc.compass.auth;

import edu.usc.compass.auth.model.AuthEnums.GoogleIdentity;
import edu.usc.compass.common.Todo;

/**
 * Verifies the ID token the frontend receives from Google's sign-in pop-up (Deployment doc, Google Sign-In).
 * Must check the signature, that the audience equals GOOGLE_CLIENT_ID, the issuer is accounts.google.com,
 * and that it has not expired. Suggested library: com.google.api-client:google-api-client
 * (GoogleIdTokenVerifier) — add it to pom.xml when you implement this.
 * Owner: Gavin.
 */
public class GoogleTokenVerifier {

    private final String googleClientId;

    public GoogleTokenVerifier(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    /** Returns the verified identity, or throws ApiException.unauthenticated() if the token is invalid. */
    public GoogleIdentity verify(String idToken) {
        throw Todo.of("Gavin", "GoogleTokenVerifier.verify");
    }
}
