package com.pulsevote.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Slows down password guessing: at most 5 login attempts per 15 minutes for the same email from the same
// address (a successful login clears the count). In memory only (resets when the backend restarts).
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    // Counts this attempt BEFORE the password is checked (so parallel requests cannot all slip through).
    // Returns false when the limit is reached. A successful login calls reset(), which clears the count.
    public boolean tryAcquire(String key) {
        Deque<Long> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (times) {
            prune(times);
            allowed = times.size() < MAX_FAILURES;
            if (allowed) times.addLast(System.currentTimeMillis());
        }
        if (failures.size() > 10_000) { // keep memory bounded
            long now = System.currentTimeMillis();
            failures.values().removeIf(d -> {
                synchronized (d) { return d.isEmpty() || now - d.peekLast() > WINDOW_MS; }
            });
        }
        return allowed;
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Long> times) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();
    }
}
