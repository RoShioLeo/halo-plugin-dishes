package com.dishes.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import com.dishes.api.BusinessException;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MealScheduleTest {
    @Test
    void defaultsSwitchAtTenAndFifteenAtTheExactMinute() {
        var schedule = MealSchedule.normalize(null);
        assertEquals(new MealSchedule("10:00", "15:00"), schedule);
        assertEquals("breakfast", schedule.periodAt(LocalTime.of(0, 0)));
        assertEquals("breakfast", schedule.periodAt(LocalTime.of(9, 59, 59)));
        assertEquals("lunch", schedule.periodAt(LocalTime.of(10, 0)));
        assertEquals("lunch", schedule.periodAt(LocalTime.of(14, 59, 59)));
        assertEquals("dinner", schedule.periodAt(LocalTime.of(15, 0)));
        assertEquals("dinner", schedule.periodAt(LocalTime.of(23, 59)));
    }

    @Test
    void customMinuteBoundariesKeepAllThreePeriodsNonempty() {
        var schedule = MealSchedule.normalize(new MealSchedule("09:45", "18:30"));
        assertEquals("breakfast", schedule.periodAt(LocalTime.of(9, 44, 59)));
        assertEquals("lunch", schedule.periodAt(LocalTime.of(9, 45)));
        assertEquals("lunch", schedule.periodAt(LocalTime.of(18, 29, 59)));
        assertEquals("dinner", schedule.periodAt(LocalTime.of(18, 30)));
        assertEquals(new MealSchedule("00:01", "23:59"), MealSchedule.normalize(new MealSchedule("00:01", "23:59")));
    }

    @Test
    void rejectsMalformedEqualOrReversedBoundaries() {
        for (var schedule : List.of(new MealSchedule("9:00", "15:00"), new MealSchedule("10:00:00", "15:00"),
            new MealSchedule("10:00", "24:00"), new MealSchedule("10:60", "15:00"), new MealSchedule("00:00", "15:00"),
            new MealSchedule("15:00", "15:00"), new MealSchedule("16:00", "15:00"), new MealSchedule(null, "15:00"))) {
            assertThrows(BusinessException.class, () -> MealSchedule.normalize(schedule));
        }
    }
}
