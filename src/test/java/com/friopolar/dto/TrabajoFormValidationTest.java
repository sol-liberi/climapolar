package com.friopolar.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TrabajoFormValidationTest {
    @Test
    void rechazaUnaCategoriaQueNoPerteneceAlCatalogo() {
        TrabajoForm form = new TrabajoForm();
        form.setTitulo("Trabajo");
        form.setDescripcion("Descripción");
        form.setCategoria("reparacion-inventada");
        form.setFecha(LocalDate.of(2026, 8, 17));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(form))
                    .anySatisfy(error -> {
                        assertThat(error.getPropertyPath().toString()).isEqualTo("categoria");
                        assertThat(error.getMessage()).contains("no es válida");
                    });
        }
    }
}
