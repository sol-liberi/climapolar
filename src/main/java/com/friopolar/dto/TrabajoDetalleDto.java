package com.friopolar.dto;

import com.friopolar.model.Trabajo;

import java.time.LocalDate;
import java.util.List;

public record TrabajoDetalleDto(
        Long id,
        String titulo,
        String descripcion,
        String ubicacion,
        LocalDate fecha,
        CategoriaDto categoria,
        boolean destacado,
        List<MedioDto> medios
) {
    public static TrabajoDetalleDto desde(Trabajo trabajo) {
        return new TrabajoDetalleDto(
                trabajo.getId(),
                trabajo.getTitulo(),
                trabajo.getDescripcion(),
                trabajo.getUbicacion(),
                trabajo.getFecha(),
                CategoriaDto.desde(trabajo.getCategoria()),
                trabajo.isDestacado(),
                trabajo.getMedios().stream().map(MedioDto::desde).toList()
        );
    }
}
