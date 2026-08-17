package com.friopolar.dto;

import com.friopolar.model.CategoriaTrabajo;

public record CategoriaDto(String codigo, String etiqueta) {
    public static CategoriaDto desde(CategoriaTrabajo categoria) {
        return new CategoriaDto(categoria.getCodigo(), categoria.getEtiqueta());
    }
}
