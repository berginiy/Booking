package com.company.booking.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Простая защита от перебора паролей: 5 неудачных попыток на логин за 15 минут → блокировка входа. */
@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private record Entry(int count, long windowStart) {}

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Entry e = attempts.get(key);
        return e != null && e.count() >= MAX_FAILURES && System.currentTimeMillis() - e.windowStart() < WINDOW_MS;
    }

    public void failed(String key) {
        long now = System.currentTimeMillis();
        attempts.compute(key, (k, e) -> e == null || now - e.windowStart() >= WINDOW_MS
                ? new Entry(1, now) : new Entry(e.count() + 1, e.windowStart()));
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }
}
