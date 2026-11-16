package edu.usc.compass.common;

/**
 * ?page= (default 1) and ?pageSize= (default 25, max 100).
 * Use {@link #limit()} and {@link #offset()} in SQL: {@code LIMIT ? OFFSET ?}.
 * PLATFORM — complete.
 */
public record PageRequest(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    public PageRequest {
        if (page < 1) {
            throw ApiException.validation("page", "Page must be 1 or more.");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.validation("pageSize", "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    public static PageRequest first() {
        return new PageRequest(1, DEFAULT_PAGE_SIZE);
    }

    public int limit() {
        return pageSize;
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
