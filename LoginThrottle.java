package app.privmicro.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection keyed by account name (never by IP address, so no network identifiers are kept).
 * State disappears on restart and is purged periodically.
 */
@Component
public class LoginThrottle {
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 5 * 60_000L;

    private record Entry(int failures, long lockedUntil, long touched) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    public void check(String key) {
        Entry e = entries.get(key);
        if (e != null && e.lockedUntil() > System.currentTimeMillis()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        }
    }

    public void fail(String key) {
        long now = System.currentTimeMillis();
        entries.merge(key, new Entry(1, 0, now), (old, ignored) -> {
            int f = old.lockedUntil() != 0 && old.lockedUntil() <= now ? 1 : old.failures() + 1;
            return new Entry(f, f >= MAX_FAILURES ? now + LOCK_MS : 0, now);
        });
    }

    public void success(String key) {
        entries.remove(key);
    }

    /** Global fixed-window limiter, e.g. registrations per minute. */
    public void limitGlobal(String bucket, int max, long windowMs) {
        long now = System.currentTimeMillis();
        long[] w = windows.compute(bucket, (k, cur) -> {
            if (cur == null || now - cur[0] > windowMs) return new long[]{now, 1};
            cur[1]++;
            return cur;
        });
        if (w[1] > max) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Busy right now. Try again in a minute.");
        }
    }

    public void purge() {
        long cutoff = System.currentTimeMillis() - 3_600_000L;
        entries.entrySet().removeIf(e -> e.getValue().touched() < cutoff && e.getValue().lockedUntil() < System.currentTimeMillis());
        windows.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue()[0] > 3_600_000L);
    }
}
