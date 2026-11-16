package edu.usc.compass.common;

import java.util.List;

/**
 * Standard list response: {"items": [...], "page": 1, "pageSize": 25, "total": 42}.
 * PLATFORM — complete.
 */
public record Page<T>(List<T> items, int page, int pageSize, long total) {

    public static <T> Page<T> of(List<T> items, PageRequest req, long total) {
        return new Page<>(items, req.page(), req.pageSize(), total);
    }

    /** For small lists that are not paged in SQL. */
    public static <T> Page<T> all(List<T> items) {
        return new Page<>(items, 1, Math.max(items.size(), 1), items.size());
    }
}
