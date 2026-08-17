package com.friopolar.service;

import com.friopolar.repository.TrabajoMediaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MediaReconciliationRunner implements ApplicationRunner {
    private static final Duration ANTIGUEDAD_MINIMA = Duration.ofHours(24);

    private final TrabajoMediaRepository repository;
    private final MediaStorageService storage;

    public MediaReconciliationRunner(TrabajoMediaRepository repository, MediaStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        var rutasReferenciadas = new HashSet<>(repository.buscarRutasOriginalesGestionadas());
        rutasReferenciadas.addAll(repository.buscarRutasMiniaturasGestionadas());
        storage.reconciliarHuerfanos(rutasReferenciadas, ANTIGUEDAD_MINIMA);
    }
}
