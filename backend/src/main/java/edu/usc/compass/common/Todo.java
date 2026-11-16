package edu.usc.compass.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks unimplemented code so the app still compiles and runs.
 *
 * <ul>
 *   <li>{@code throw Todo.of("Adam", "TaskService.create");} — feature code. The endpoint returns
 *       501 NOT_IMPLEMENTED until the owner replaces it.</li>
 *   <li>{@code Todo.noop("Raegen", "NotificationService.notify");} — cross-feature hooks other people
 *       call (notify, enqueue email, record activity/audit). These log and return instead of throwing,
 *       so nobody is blocked waiting for someone else.</li>
 * </ul>
 *
 * Search the repo for {@code Todo.of("YourName"} to find your remaining work.
 * PLATFORM — complete.
 */
public final class Todo {

    private static final Logger log = LoggerFactory.getLogger(Todo.class);

    private Todo() {
    }

    public static ApiException of(String owner, String what) {
        return new ApiException(ErrorCode.NOT_IMPLEMENTED, "Not implemented yet: " + what + " (owner: " + owner + ")");
    }

    public static void noop(String owner, String what) {
        log.debug("[TODO {}] {} is a stub; skipping.", owner, what);
    }
}
