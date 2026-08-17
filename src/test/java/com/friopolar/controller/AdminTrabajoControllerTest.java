package com.friopolar.controller;

import com.friopolar.config.MediaStorageProperties;
import com.friopolar.model.Trabajo;
import com.friopolar.service.MediaStorageException;
import com.friopolar.service.TrabajoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminTrabajoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MediaStorageProperties.class)
class AdminTrabajoControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    TrabajoService service;

    @BeforeEach
    void prepararListado() {
        when(service.listarTodos()).thenReturn(List.of());
    }

    @Test
    void incluyeLaVersionEnElFormularioDeEliminacion() throws Exception {
        Trabajo trabajo = trabajoDePrueba();
        when(service.listarTodos()).thenReturn(List.of(trabajo));

        mvc.perform(get("/admin/trabajos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"version\"")))
                .andExpect(content().string(containsString("value=\"6\"")));
    }

    @Test
    void enviaLaVersionAlServicioYConfirmaLaEliminacion() throws Exception {
        mvc.perform(post("/admin/trabajos/eliminar/7")
                        .param("version", "6"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/trabajos"))
                .andExpect(flash().attribute("mensaje", "Trabajo eliminado."));

        verify(service).eliminar(7L, 6L);
    }

    @Test
    void muestraElConflictoDeConcurrenciaComoErrorFlash() throws Exception {
        String mensaje = "Este trabajo fue modificado por otra sesión.";
        doThrow(new MediaStorageException(mensaje)).when(service).eliminar(7L, 6L);

        mvc.perform(post("/admin/trabajos/eliminar/7")
                        .param("version", "6"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/trabajos"))
                .andExpect(flash().attribute("error", mensaje));

        verify(service).eliminar(7L, 6L);
    }

    @Test
    void renderizaElErrorFlashEnElListado() throws Exception {
        String mensaje = "Recargá la lista antes de eliminarlo.";

        mvc.perform(get("/admin/trabajos").flashAttr("error", mensaje))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert alert-danger")))
                .andExpect(content().string(containsString(mensaje)));
    }

    private Trabajo trabajoDePrueba() {
        Trabajo trabajo = new Trabajo();
        trabajo.setId(7L);
        trabajo.setVersion(6);
        trabajo.setTitulo("Trabajo con versión");
        trabajo.setDescripcion("Descripción");
        trabajo.setTipoServicio("conductos");
        trabajo.setUbicacion("");
        trabajo.setFecha(LocalDate.of(2026, 8, 17));
        return trabajo;
    }
}
