package com.friopolar.repository;

import com.friopolar.model.Trabajo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrabajoRepository extends JpaRepository<Trabajo, Long> {
    Page<Trabajo> findAllByOrderByFechaDescIdDesc(Pageable pageable);
    Page<Trabajo> findByTipoServicioOrderByFechaDescIdDesc(String tipoServicio, Pageable pageable);
    Page<Trabajo> findByDestacadoTrueOrderByFechaDescIdDesc(Pageable pageable);
    Page<Trabajo> findByTipoServicioAndDestacadoTrueOrderByFechaDescIdDesc(String tipoServicio, Pageable pageable);

    @EntityGraph(attributePaths = "medios")
    @Query("select distinct t from Trabajo t order by t.fecha desc, t.id desc")
    List<Trabajo> buscarTodosConMedios();

    @EntityGraph(attributePaths = "medios")
    @Query("select t from Trabajo t where t.id = :id")
    Optional<Trabajo> buscarPorIdConMedios(@Param("id") Long id);
}
