package com.friopolar.repository;

import com.friopolar.model.Trabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrabajoRepository extends JpaRepository<Trabajo, Long> {
    List<Trabajo> findByDestacadoTrueOrderByFechaDesc();
    List<Trabajo> findAllByOrderByFechaDesc();
}
