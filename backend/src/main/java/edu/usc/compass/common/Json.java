package edu.usc.compass.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shared Jackson configuration (Detailed Design 3.5 "API Conventions").
 * <ul>
 *   <li>camelCase field names (Java record component names are used as-is)</li>
 *   <li>Instants as ISO-8601 UTC strings ("2026-11-14T17:00:00Z"), LocalDate as "2026-11-14"</li>
 *   <li>Unknown JSON fields and unknown enum values are rejected (400)</li>
 * </ul>
 * PLATFORM — complete.
 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private Json() {
    }

    public static <T> T read(InputStream in, Class<T> type) throws IOException {
        return MAPPER.readValue(in, type);
    }

    public static <T> T read(String json, Class<T> type) throws IOException {
        return MAPPER.readValue(json, type);
    }

    public static void write(OutputStream out, Object value) throws IOException {
        MAPPER.writeValue(out, value);
    }

    public static String toString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
