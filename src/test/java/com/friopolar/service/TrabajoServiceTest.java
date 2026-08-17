package com.friopolar.service;

import com.friopolar.dto.TrabajoForm;
import com.friopolar.model.TipoMedio;
import com.friopolar.model.Trabajo;
import com.friopolar.model.TrabajoMedia;
import com.friopolar.repository.TrabajoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrabajoServiceTest {
    private TrabajoRepository repository;
    private TrabajoService service;

    @BeforeEach
    void preparar() {
        repository = mock(TrabajoRepository.class);
        service = new TrabajoService(repository, mock(MediaStorageService.class));
    }

    @Test
    void rechazaUnaPaginaConOffsetExcesivoSinConsultarLaBase() {
        assertThatThrownBy(() -> service.listarPagina(null, false, Integer.MAX_VALUE, 24))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(repository, never()).findAllByOrderByFechaDescIdDesc(any());
    }

    @Test
    void rechazaPaginaTamanoYCategoriaInvalidos() {
        assertBadRequest(() -> service.listarPagina(null, false, -1, 9));
        assertBadRequest(() -> service.listarPagina(null, false, 0, 0));
        assertBadRequest(() -> service.listarPagina(null, false, 0, 25));
        assertBadRequest(() -> service.listarPagina("reparaciones", false, 0, 9));
    }

    @Test
    void normalizaUnaCategoriaValidaAntesDeConsultar() {
        when(repository.findByTipoServicioOrderByFechaDescIdDesc(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listarPagina(" CONDUCTOS ", false, 0, 9);

        verify(repository).findByTipoServicioOrderByFechaDescIdDesc(
                org.mockito.ArgumentMatchers.eq("conductos"),
                any(Pageable.class)
        );
    }

    @Test
    void rechazaEdicionConVersionObsoleta() {
        Trabajo trabajo = trabajoExistente();
        trabajo.setVersion(4);
        when(repository.buscarPorIdConMedios(10L)).thenReturn(Optional.of(trabajo));

        TrabajoForm form = formularioValido(10L, 3L);

        assertThatThrownBy(() -> service.guardar(form))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("modificado por otra sesión");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void noConvierteUnaCategoriaManipuladaEnOtros() {
        Trabajo trabajo = trabajoExistente();
        trabajo.setVersion(4);
        when(repository.buscarPorIdConMedios(10L)).thenReturn(Optional.of(trabajo));

        TrabajoForm form = formularioValido(10L, 4L);
        form.setCategoria("servicio-inventado");

        assertThatThrownBy(() -> service.guardar(form))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("categoría seleccionada no es válida");
        verify(repository, never()).saveAndFlush(any());
    }

    private void assertBadRequest(Runnable llamada) {
        assertThatThrownBy(llamada::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Trabajo trabajoExistente() {
        Trabajo trabajo = new Trabajo();
        trabajo.setId(10L);
        trabajo.setTitulo("Trabajo existente");
        trabajo.setDescripcion("Descripción existente");
        trabajo.setTipoServicio("conductos");
        trabajo.setUbicacion("");
        trabajo.setFecha(LocalDate.of(2026, 8, 17));

        TrabajoMedia medio = new TrabajoMedia();
        medio.setId(20L);
        medio.setTipo(TipoMedio.IMAGEN);
        medio.setRutaPublica("/img/trabajos/conductos-galvanizados.jpeg");
        trabajo.agregarMedio(medio);
        return trabajo;
    }

    private TrabajoForm formularioValido(Long id, Long version) {
        TrabajoForm form = new TrabajoForm();
        form.setId(id);
        form.setVersion(version);
        form.setTitulo("Trabajo editado");
        form.setDescripcion("Descripción editada");
        form.setCategoria("conductos");
        form.setUbicacion("");
        form.setFecha(LocalDate.of(2026, 8, 17));
        return form;
    }
}
