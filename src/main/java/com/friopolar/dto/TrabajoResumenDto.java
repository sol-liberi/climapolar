package com.friopolar.dto;

import com.friopolar.model.Trabajo;

import java.time.LocalDate;

public record TrabajoResumenDto(
        Long id,
        String titulo,
        String descripcion,
        LocalDate fecha,
        CategoriaDto categoria,
        MedioDto portada,
        long cantidadImagenes,
        long cantidadVideos
) {
    public static TrabajoResumenDto desde(Trabajo trabajo) {
        return new TrabajoResumenDto(
                trabajo.getId(),
                trabajo.getTitulo(),
                trabajo.getDescripcion(),
                trabajo.getFecha(),
                CategoriaDto.desde(trabajo.getCategoria()),
                trabajo.getPortada() == null ? null : MedioDto.desde(trabajo.getPortada()),
                trabajo.getCantidadImagenes(),
                trabajo.getCantidadVideos()
        );
    }
}
