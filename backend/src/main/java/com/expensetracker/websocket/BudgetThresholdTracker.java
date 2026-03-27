package com.expensetracker.websocket;

import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker for fired budget threshold alerts per user per month.
 * Tracks the last known usage so that only newly crossed thresholds fire
 * on transaction changes, while subscribe always reports the current status.
 */
@Component
public class BudgetThresholdTracker {

    private final Map<Long, Map<YearMonth, Set<Integer>>> firedThresholds = new ConcurrentHashMap<>();

    public boolean shouldFire(Long userId, YearMonth month, int threshold) {
        Set<Integer> thresholds = firedThresholds
            .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(month, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        return thresholds.add(threshold);
    }

    /**
     * Marks thresholds as fired without actually sending alerts.
     * Used after subscribe sends current status to avoid duplicate alerts
     * on the next transaction change.
     */
    public void markFired(Long userId, YearMonth month, int threshold) {
        firedThresholds
            .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(month, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
            .add(threshold);
    }

    /**
     * Resets fired state for thresholds that are no longer crossed.
     * Called when usage decreases (e.g., transaction deleted) so that
     * re-crossing triggers a new alert.
     */
    public void resetAbove(Long userId, YearMonth month, double usagePercent) {
        Map<YearMonth, Set<Integer>> userMap = firedThresholds.get(userId);
        if (userMap == null) return;
        Set<Integer> thresholds = userMap.get(month);
        if (thresholds == null) return;
        thresholds.removeIf(t -> t > usagePercent);
    }

    public void reset(Long userId, YearMonth month) {
        Map<YearMonth, Set<Integer>> userMap = firedThresholds.get(userId);
        if (userMap != null) {
            userMap.remove(month);
        }
    }
}
