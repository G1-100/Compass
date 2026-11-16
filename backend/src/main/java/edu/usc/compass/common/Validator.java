package edu.usc.compass.common;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collects field errors, then throws one 400 VALIDATION_FAILED with all of them.
 *
 * <pre>
 * new Validator()
 *     .required("title", req.title())
 *     .maxLength("title", req.title(), 200)
 *     .required("effort", req.effort())
 *     .throwIfInvalid();
 * </pre>
 * Only the first error per field is kept. Lengths count Unicode code points, so emoji count as 1.
 * PLATFORM — complete.
 */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Map<String, String> errors = new LinkedHashMap<>();

    public Validator required(String field, Object value) {
        boolean missing = value == null
                || (value instanceof String s && s.isBlank())
                || (value instanceof Collection<?> c && c.isEmpty());
        return check(!missing, field, label(field) + " is required.");
    }

    public Validator maxLength(String field, String value, int max) {
        return check(value == null || length(value) <= max, field, label(field) + " must be at most " + max + " characters.");
    }

    /** Required, trimmed length between min and max. */
    public Validator lengthBetween(String field, String value, int min, int max) {
        required(field, value);
        if (value != null && !value.isBlank()) {
            int len = length(value.trim());
            check(len >= min && len <= max, field, label(field) + " must be " + min + " to " + max + " characters.");
        }
        return this;
    }

    public Validator range(String field, Number value, double min, double max) {
        return check(value == null || (value.doubleValue() >= min && value.doubleValue() <= max),
                field, label(field) + " must be between " + fmt(min) + " and " + fmt(max) + ".");
    }

    public Validator email(String field, String value) {
        return check(value == null || (value.length() <= 254 && EMAIL.matcher(value).matches()),
                field, "Enter a valid email address.");
    }

    /** At least 8 characters with at least one letter and one number (Detailed Design 3.1.1). */
    public Validator password(String field, String value) {
        boolean ok = value != null && value.length() >= 8
                && value.chars().anyMatch(Character::isLetter)
                && value.chars().anyMatch(Character::isDigit);
        return check(ok, field, "Password must be at least 8 characters with a letter and a number.");
    }

    /** Strictly after {@code now}. */
    public Validator future(String field, Instant value, Instant now) {
        return check(value == null || value.isAfter(now), field, label(field) + " must be in the future.");
    }

    /** {@code end} strictly after {@code start}. Reported on the end field. */
    public Validator dateOrder(String startField, LocalDate start, String endField, LocalDate end) {
        return check(start == null || end == null || end.isAfter(start), endField,
                label(endField) + " must be after " + label(startField).toLowerCase() + ".");
    }

    /** Generic rule. */
    public Validator check(boolean ok, String field, String message) {
        if (!ok) {
            errors.putIfAbsent(field, message);
        }
        return this;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public Map<String, String> errors() {
        return Map.copyOf(errors);
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    /** "dueDate" -> "Due date". */
    static String label(String field) {
        String spaced = field.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
