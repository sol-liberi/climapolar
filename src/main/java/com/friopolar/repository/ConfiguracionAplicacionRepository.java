package com.friopolar.repository;

import com.friopolar.model.ConfiguracionAplicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfiguracionAplicacionRepository extends JpaRepository<ConfiguracionAplicacion, String> {
    @Modifying
    @Query(value = """
            INSERT INTO configuracion_aplicacion (clave, valor)
            VALUES (:clave, :valor)
            ON CONFLICT (clave) DO NOTHING
            """, nativeQuery = true)
    int registrarSiAusente(@Param("clave") String clave, @Param("valor") String valor);
}
