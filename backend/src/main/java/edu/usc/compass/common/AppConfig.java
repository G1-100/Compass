package edu.usc.compass.common;

import java.util.Map;

/**
 * Settings read from environment variables. Secrets live only on the servlet host, never in the repo.
 * In Eclipse: Tomcat server launch configuration -> Environment.
 * PLATFORM — complete.
 *
 * @param databaseUrl     SUPABASE_DB_URL (JDBC session-pooler string with &sslmode=require)
 * @param googleClientId  GOOGLE_CLIENT_ID (same value as VITE_GOOGLE_CLIENT_ID)
 * @param publicSiteUrl   PUBLIC_SITE_URL, e.g. https://compass.vercel.app — used in email and guest links
 * @param devAuth         COMPASS_DEV_AUTH=true lets requests authenticate with the X-Dev-User-Id header.
 *                        LOCAL DEVELOPMENT ONLY. Never set this in production.
 * @param jobsEnabled     COMPASS_JOBS_ENABLED (default true) — background email sender and scheduled jobs
 */
public record AppConfig(
        String databaseUrl,
        String googleClientId,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        String emailFrom,
        String publicSiteUrl,
        boolean devAuth,
        boolean jobsEnabled) {

    public static AppConfig fromEnv() {
        return from(System.getenv());
    }

    public static AppConfig from(Map<String, String> env) {
        return new AppConfig(
                env.get("SUPABASE_DB_URL"),
                env.get("GOOGLE_CLIENT_ID"),
                env.getOrDefault("SMTP_HOST", "localhost"),
                Integer.parseInt(env.getOrDefault("SMTP_PORT", "1025")),   // Mailpit default
                env.get("SMTP_USERNAME"),
                env.get("SMTP_PASSWORD"),
                env.getOrDefault("EMAIL_FROM", "Compass <no-reply@compass.local>"),
                env.getOrDefault("PUBLIC_SITE_URL", "http://localhost:5173"),
                Boolean.parseBoolean(env.getOrDefault("COMPASS_DEV_AUTH", "false")),
                Boolean.parseBoolean(env.getOrDefault("COMPASS_JOBS_ENABLED", "true")));
    }

    public boolean hasDatabase() {
        return databaseUrl != null && !databaseUrl.isBlank();
    }
}
