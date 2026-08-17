package com.friopolar.controller;

import com.friopolar.dto.TrabajoPaginaDto;
import com.friopolar.service.TrabajoService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicControllerTest {
    @Test
    void inicioIncluyeTrabajosDestacadosRenderizadosEnServidor() {
        TrabajoService service = mock(TrabajoService.class);
        TrabajoPaginaDto pagina = new TrabajoPaginaDto(List.of(), 0, 0, 0);
        when(service.listarPagina(null, true, 0, 6)).thenReturn(pagina);
        ExtendedModelMap model = new ExtendedModelMap();

        String vista = new HomeController(service).inicio(model);

        assertThat(vista).isEqualTo("index");
        assertThat(model.get("paginaTrabajos")).isSameAs(pagina);
    }

    @Test
    void portfolioConservaFiltroYPaginaParaNavegacionSinJavaScript() {
        TrabajoService service = mock(TrabajoService.class);
        TrabajoPaginaDto pagina = new TrabajoPaginaDto(List.of(), 2, 4, 30);
        when(service.listarPagina("conductos", false, 2, 9)).thenReturn(pagina);
        ExtendedModelMap model = new ExtendedModelMap();

        String vista = new TrabajoController(service).listar(" CONDUCTOS ", 2, model);

        assertThat(vista).isEqualTo("trabajos");
        assertThat(model.get("paginaTrabajos")).isSameAs(pagina);
        assertThat(model.get("categoriaActiva")).isEqualTo("conductos");
        verify(service).listarPagina("conductos", false, 2, 9);
    }

    @Test
    void portfolioRechazaFiltroManipuladoAntesDeConsultar() {
        TrabajoService service = mock(TrabajoService.class);

        assertThatThrownBy(() -> new TrabajoController(service)
                .listar("categoria-inventada", 0, new ExtendedModelMap()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(service);
    }
}
