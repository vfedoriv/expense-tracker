package com.expensetracker.websocket;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetThresholdTrackerTest {

    private final BudgetThresholdTracker tracker = new BudgetThresholdTracker();

    @Test
    void shouldFire_firstTime_returnsTrue() {
        assertThat(tracker.shouldFire(1L, YearMonth.of(2026, 3), 50)).isTrue();
    }

    @Test
    void shouldFire_secondTime_returnsFalse() {
        tracker.shouldFire(1L, YearMonth.of(2026, 3), 50);
        assertThat(tracker.shouldFire(1L, YearMonth.of(2026, 3), 50)).isFalse();
    }

    @Test
    void shouldFire_differentThreshold_returnsTrue() {
        tracker.shouldFire(1L, YearMonth.of(2026, 3), 50);
        assertThat(tracker.shouldFire(1L, YearMonth.of(2026, 3), 80)).isTrue();
    }

    @Test
    void shouldFire_differentUser_returnsTrue() {
        tracker.shouldFire(1L, YearMonth.of(2026, 3), 50);
        assertThat(tracker.shouldFire(2L, YearMonth.of(2026, 3), 50)).isTrue();
    }

    @Test
    void shouldFire_differentMonth_returnsTrue() {
        tracker.shouldFire(1L, YearMonth.of(2026, 3), 50);
        assertThat(tracker.shouldFire(1L, YearMonth.of(2026, 4), 50)).isTrue();
    }

    @Test
    void reset_allowsFiringAgain() {
        tracker.shouldFire(1L, YearMonth.of(2026, 3), 50);
        tracker.reset(1L, YearMonth.of(2026, 3));
        assertThat(tracker.shouldFire(1L, YearMonth.of(2026, 3), 50)).isTrue();
    }
}
