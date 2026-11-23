package edu.usc.compass.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import edu.usc.compass.auth.model.AuthEnums.GoogleIdentity;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Verifies the ID token the frontend receives from Google's sign-in pop-up (Deployment doc, Google Sign-In).
 * GoogleIdTokenVerifier checks the signature against Google's public keys (cached), that the audience equals
 * GOOGLE_CLIENT_ID, the issuer is accounts.google.com, and that the token has not expired.
 * Owner: Gavin.
 */
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final String googleClientId;
    private volatile GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    /** Returns the verified identity, or throws ApiException.unauthenticated() if the token is invalid. */
    public GoogleIdentity verify(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ApiException(ErrorCode.SERVER_ERROR, "Google sign-in is not configured (set GOOGLE_CLIENT_ID).");
        }
        if (idToken == null || idToken.isBlank()) {
            throw ApiException.unauthenticated();
        }
        GoogleIdToken token;
        try {
            token = verifier().verify(idToken);
        } catch (Exception e) {   // malformed token, bad signature, or Google's key endpoint unreachable
            log.info("Google ID token rejected: {}", e.getMessage());
            throw ApiException.unauthenticated();
        }
        if (token == null) {
            throw ApiException.unauthenticated();
        }
        GoogleIdToken.Payload p = token.getPayload();
        return new GoogleIdentity(
                p.getSubject(),
                p.getEmail(),
                (String) p.get("name"),
                Boolean.TRUE.equals(p.getEmailVerified()));
    }

    private GoogleIdTokenVerifier verifier() {
        GoogleIdTokenVerifier v = verifier;
        if (v == null) {
            synchronized (this) {
                if (verifier == null) {
                    verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                            .setAudience(List.of(googleClientId))
                            .build();
                }
                v = verifier;
            }
        }
        return v;
    }
}
