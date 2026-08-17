package com.friopolar.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriaTrabajoTest {
    @Test
    void normalizaCategoriasAnterioresSinPerderSuSignificado() {
        assertThat(CategoriaTrabajo.desdeValorGuardado("Preinstalación en obra"))
                .isEqualTo(CategoriaTrabajo.PREINSTALACIONES);
        assertThat(CategoriaTrabajo.desdeValorGuardado("Climatización por conductos"))
                .isEqualTo(CategoriaTrabajo.SISTEMAS);
        assertThat(CategoriaTrabajo.desdeValorGuardado("Conductos y distribución de aire"))
                .isEqualTo(CategoriaTrabajo.CONDUCTOS);
        assertThat(CategoriaTrabajo.desdeValorGuardado("Categoría antigua"))
                .isEqualTo(CategoriaTrabajo.OTROS);
    }
}
