package com.friopolar.dto;

import com.friopolar.model.TrabajoMedia;

public record MedioDto(
        Long id,
        String tipo,
        String url,
        String miniaturaUrl,
        String contentType
) {
    public static MedioDto desde(TrabajoMedia medio) {
        return new MedioDto(
                medio.getId(),
                medio.getTipo().name().toLowerCase(),
                medio.getRutaPublica(),
                medio.getMiniaturaOOriginal(),
                medio.getContentType()
        );
    }
}
