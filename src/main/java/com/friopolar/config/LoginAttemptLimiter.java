package com.friopolar.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Límite en memoria para el único punto de autenticación del sitio.
 *
 * <p>Se limita la combinación IP/usuario y, con un umbral más amplio, cada IP
 * y usuario por separado. Esto evita que cambiar el nombre de usuario o repartir
 * los intentos entre varias direcciones eluda completamente el control.</p>
 */
public final class LoginAttemptLimiter {
    static final int COMBINATION_LIMIT = 5;
    static final int AGGREGATE_LIMIT = 20;

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration INITIAL_BLOCK = Duration.ofSeconds(30);
    private static final Duration MAX_BLOCK = Duration.ofMinutes(15);
    private static final Duration RETENTION = Duration.ofHours(1);
    static final int MAX_ENTRIES = 20_000;
    private static final long CLEANUP_INTERVAL = 128;
    private static final long SATURATION_RETRY_SECONDS = 60;

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision check(String clientAddress, String username) {
        Instant now = clock.instant();
        cleanIfNeeded(now);

        List<String> keys = keys(clientAddress, username);
        long retryAfterSeconds = keys.stream()
                .map(attempts::get)
                .filter(state -> state != null && state.blockedUntil().isAfter(now))
                .mapToLong(state -> Math.max(1, Duration.between(now, state.blockedUntil()).toSeconds() + 1))
                .max()
                .orElse(0);

        // Si una inundación de IP/usuarios llenó el mapa, los identificadores
        // completamente nuevos fallan de forma cerrada sin aumentar la memoria.
        if (retryAfterSeconds == 0 && attempts.size() >= MAX_ENTRIES
                && keys.stream().noneMatch(attempts::containsKey)) {
            retryAfterSeconds = SATURATION_RETRY_SECONDS;
        }

        return retryAfterSeconds == 0
                ? RateLimitDecision.allowed()
                : RateLimitDecision.blocked(retryAfterSeconds);
    }

    public synchronized void recordFailure(String clientAddress, String username) {
        Instant now = clock.instant();
        cleanIfNeeded(now);

        if (attempts.size() >= MAX_ENTRIES) {
            removeExpired(now);
        }

        List<String> keys = keys(clientAddress, username);
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            int limit = index == 0 ? COMBINATION_LIMIT : AGGREGATE_LIMIT;

            // En una inundación de identificadores diferentes se conserva el
            // límite por IP ya existente y no se permite crecer sin cota.
            if (attempts.size() >= MAX_ENTRIES && !attempts.containsKey(key)) {
                continue;
            }
            attempts.compute(key, (ignored, previous) -> nextState(previous, now, limit));
        }
    }

    public synchronized void recordSuccess(String clientAddress, String username) {
        keys(clientAddress, username).forEach(attempts::remove);
    }

    private AttemptState nextState(AttemptState previous, Instant now, int limit) {
        boolean newWindow = previous == null || !previous.windowStarted().plus(WINDOW).isAfter(now);
        int failures = newWindow ? 1 : previous.failures() + 1;
        Instant windowStarted = newWindow ? now : previous.windowStarted();
        Instant blockedUntil = Instant.EPOCH;

        if (failures >= limit) {
            int exponent = Math.min(failures - limit, 10);
            long seconds = Math.min(MAX_BLOCK.toSeconds(), INITIAL_BLOCK.toSeconds() << exponent);
            blockedUntil = now.plusSeconds(seconds);
        }

        return new AttemptState(failures, windowStarted, now, blockedUntil);
    }

    private List<String> keys(String clientAddress, String username) {
        String safeAddress = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
        String safeUsername = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        safeAddress = truncate(safeAddress, 64);
        safeUsername = truncate(safeUsername, 128);
        return List.of(
                "combination:" + safeAddress + '\u0000' + safeUsername,
                "ip:" + safeAddress,
                "user:" + safeUsername);
    }

    private String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private void cleanIfNeeded(Instant now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            removeExpired(now);
        }
    }

    private void removeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> isExpired(entry, now));
    }

    private boolean isExpired(Map.Entry<String, AttemptState> entry, Instant now) {
        AttemptState state = entry.getValue();
        return !state.blockedUntil().isAfter(now) && state.lastSeen().plus(RETENTION).isBefore(now);
    }

    synchronized void clearAll() {
        attempts.clear();
    }

    private record AttemptState(int failures, Instant windowStarted, Instant lastSeen, Instant blockedUntil) {
    }

    public record RateLimitDecision(boolean blocked, long retryAfterSeconds) {
        private static RateLimitDecision allowed() {
            return new RateLimitDecision(false, 0);
        }

        private static RateLimitDecision blocked(long retryAfterSeconds) {
            return new RateLimitDecision(true, retryAfterSeconds);
        }
    }
}
