package edu.usc.compass.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest {

    @Test
    void collectsAllFieldErrors() {
        ApiException e = assertThrows(ApiException.class, () -> new Validator()
                .required("title", "  ")
                .maxLength("description", "x".repeat(11), 10)
                .throwIfInvalid());
        assertEquals(ErrorCode.VALIDATION_FAILED, e.code());
        assertEquals("Title is required.", e.fields().get("title"));
        assertTrue(e.fields().containsKey("description"));
    }

    @Test
    void emojiCountAsOneCharacter() {
        assertTrue(new Validator().maxLength("t", "😀😀", 2).isValid());
    }

    @Test
    void passwordPolicy() {
        assertFalse(new Validator().password("p", "short1").isValid());
        assertFalse(new Validator().password("p", "longenough").isValid());
        assertTrue(new Validator().password("p", "Password1").isValid());
    }

    @Test
    void labelsCamelCase() {
        assertEquals("Due date", Validator.label("dueDate"));
    }
}
