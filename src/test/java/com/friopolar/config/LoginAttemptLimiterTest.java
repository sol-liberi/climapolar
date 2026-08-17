package com.friopolar.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {
    @Test
    void aplicaVentanaBackoffYReinicioTrasUnAccesoCorrecto() {
        var clock = new MutableClock(Instant.parse("2026-08-17T12:00:00Z"));
        var limiter = new LoginAttemptLimiter(clock);

        for (int attempt = 0; attempt < LoginAttemptLimiter.COMBINATION_LIMIT; attempt++) {
            limiter.recordFailure("198.51.100.50", "admin");
        }
        assertTrue(limiter.check("198.51.100.50", "admin").blocked());

        clock.advance(Duration.ofSeconds(31));
        assertFalse(limiter.check("198.51.100.50", "admin").blocked());

        limiter.recordFailure("198.51.100.50", "admin");
        var secondBlock = limiter.check("198.51.100.50", "admin");
        assertTrue(secondBlock.blocked());
        assertTrue(secondBlock.retryAfterSeconds() >= 60);

        limiter.recordSuccess("198.51.100.50", "admin");
        assertFalse(limiter.check("198.51.100.50", "admin").blocked());
    }

    @Test
    void acotaLaMemoriaYRecuperaCapacidadAlExpirarEntradas() {
        var clock = new MutableClock(Instant.parse("2026-08-17T12:00:00Z"));
        var limiter = new LoginAttemptLimiter(clock);

        int identitiesNeededToFillMap = LoginAttemptLimiter.MAX_ENTRIES / 3 + 1;
        for (int identity = 0; identity < identitiesNeededToFillMap; identity++) {
            limiter.recordFailure("client-" + identity, "user-" + identity);
        }
        assertTrue(limiter.check("cliente-nuevo", "usuario-nuevo").blocked());

        clock.advance(Duration.ofMinutes(61));
        limiter.recordFailure("cliente-nuevo", "usuario-nuevo");
        assertFalse(limiter.check("cliente-nuevo", "usuario-nuevo").blocked());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
