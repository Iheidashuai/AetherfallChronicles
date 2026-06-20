package com.mythicrealm.api.gameplay.robot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Short-term, in-memory behavioural memory for robots.
 *
 * <p>The decision engine is otherwise stateless (it re-perceives the DB every
 * tick), which made robots flip-flop A→B→A→B and repeat the same chat lines. This
 * keeps a tiny per-robot ring buffer of the last few action kinds and chat lines
 * so actions can apply a *decaying* anti-repeat penalty and chat can de-duplicate.
 *
 * <p>State is intentionally process-local and non-persistent: it is "what was I
 * just doing" flavour, not game data. Losing it on restart is fine. Access is
 * guarded for the (rare) case of overlapping scheduler ticks.
 */
@Service
public class RobotMemoryService {
    private static final int KIND_HISTORY = 6;
    private static final int CHAT_HISTORY = 8;

    private final Map<Long, Deque<String>> recentKinds = new ConcurrentHashMap<>();
    private final Map<Long, Deque<String>> recentChats = new ConcurrentHashMap<>();

    /** Histogram of the robot's last {@value #KIND_HISTORY} action kinds. */
    public Map<String, Integer> recentKindCounts(long robotId) {
        Deque<String> history = recentKinds.get(robotId);
        if (history == null) {
            return Map.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        synchronized (history) {
            for (String kind : history) {
                counts.merge(kind, 1, Integer::sum);
            }
        }
        return counts;
    }

    public void recordKind(long robotId, String kind) {
        if (kind == null || kind.isBlank()) {
            return;
        }
        Deque<String> history = recentKinds.computeIfAbsent(robotId, id -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(kind);
            while (history.size() > KIND_HISTORY) {
                history.removeFirst();
            }
        }
    }

    /** True if this robot recently said the same line (so we should pick another). */
    public boolean recentlySaid(long robotId, String text) {
        if (text == null) {
            return false;
        }
        Deque<String> history = recentChats.get(robotId);
        if (history == null) {
            return false;
        }
        synchronized (history) {
            return history.contains(text);
        }
    }

    public void recordChat(long robotId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Deque<String> history = recentChats.computeIfAbsent(robotId, id -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(text);
            while (history.size() > CHAT_HISTORY) {
                history.removeFirst();
            }
        }
    }
}
