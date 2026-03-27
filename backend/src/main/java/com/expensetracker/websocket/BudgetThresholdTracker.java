package com.expensetracker.websocket;

import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker for fired budget threshold alerts per user per month.
 * Prevents duplicate alerts for the same threshold within the same month.
 */
@Component
public class BudgetThresholdTracker {

    // Map of userId -> YearMonth -> set of fired thresholds
    private final Map<Long, Map<YearMonth, Set<Integer>>> firedThresholds = new ConcurrentHashMap<>();

    public boolean shouldFire(Long userId, YearMonth month, int threshold) {
        Set<Integer> thresholds = firedThresholds
            .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(month, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        return thresholds.add(threshold);
    }

    public void reset(Long userId, YearMonth month) {
        Map<YearMonth, Set<Integer>> userMap = firedThresholds.get(userId);
        if (userMap != null) {
            userMap.remove(month);
        }
    }
}
