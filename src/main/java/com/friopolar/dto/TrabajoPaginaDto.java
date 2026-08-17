package com.friopolar.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record TrabajoPaginaDto(
        List<TrabajoResumenDto> content,
        int page,
        int totalPages,
        long totalElements
) {
    public static TrabajoPaginaDto desde(Page<TrabajoResumenDto> pagina) {
        return new TrabajoPaginaDto(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getTotalPages(),
                pagina.getTotalElements()
        );
    }
}
