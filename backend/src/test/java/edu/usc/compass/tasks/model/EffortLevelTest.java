package edu.usc.compass.tasks.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffortLevelTest {

    @Test
    void pointsMatchDesign() {
        assertEquals(1, EffortLevel.MINOR.points());
        assertEquals(3, EffortLevel.MODERATE.points());
        assertEquals(5, EffortLevel.HIGH.points());
        assertEquals(8, EffortLevel.EXTREME.points());
    }
}
