package com.friopolar.repository;

import com.friopolar.model.TrabajoMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TrabajoMediaRepository extends JpaRepository<TrabajoMedia, Long> {
    @Query("select m.rutaPublica from TrabajoMedia m where m.gestionado = true")
    List<String> buscarRutasOriginalesGestionadas();

    @Query("select m.rutaMiniatura from TrabajoMedia m where m.gestionado = true and m.rutaMiniatura is not null")
    List<String> buscarRutasMiniaturasGestionadas();
}
