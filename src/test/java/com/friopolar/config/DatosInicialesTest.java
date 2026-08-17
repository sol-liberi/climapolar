package com.friopolar.config;

import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.model.TipoMedio;
import com.friopolar.model.Trabajo;
import com.friopolar.repository.ConfiguracionAplicacionRepository;
import com.friopolar.repository.TrabajoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatosInicialesTest {
    private static final List<String> TITULOS_DEMO = List.of(
            "Instalación Split 4500 frigorías",
            "Limpieza completa de equipo",
            "Instalación residencial",
            "Reparación de unidad exterior",
            "Reubicación de aire acondicionado"
    );

    private static final List<String> TITULOS_LOCALES = List.of(
            "Preinstalación: cañerías empotradas",
            "Distribución de cañerías en obra",
            "Conductos galvanizados y distribución de aire",
            "Equipo bajo silueta: montaje y conexión",
            "Instalación y distribución por conductos",
            "Equipos exteriores sobre estructura de apoyo"
    );

    private static final List<String> IMAGENES_DEMO = List.of(
            "https://images.unsplash.com/photo-1631545806609-8a83254468c6?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1590756254933-2873d72a83b6?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1581094794329-c8112a89af12?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1200&q=80"
    );

    private static final List<String> IMAGENES_LOCALES = List.of(
            "/img/trabajos/preinstalacion-empotrada.jpeg",
            "/img/trabajos/canerias-en-obra.jpeg",
            "/img/trabajos/conductos-galvanizados.jpeg",
            "/img/trabajos/equipo-bajo-silueta.jpeg",
            "/img/trabajos/climatizacion-por-conductos.jpeg",
            "/img/trabajos/equipos-exteriores.jpeg"
    );

    @Test
    void migraLosCincoDemosUnsplashYAgregaElSextoTrabajoLocal() {
        TrabajoRepository trabajos = mock(TrabajoRepository.class);
        ConfiguracionAplicacionRepository configuracion = mock(ConfiguracionAplicacionRepository.class);
        List<Trabajo> persistidos = new ArrayList<>();

        for (int indice = 0; indice < TITULOS_DEMO.size(); indice++) {
            persistidos.add(crearDemo(TITULOS_DEMO.get(indice), indice));
        }

        when(configuracion.registrarSiAusente("trabajos-inicializados", "1")).thenReturn(1);
        when(trabajos.count()).thenReturn((long) persistidos.size());
        when(trabajos.buscarTodosConMedios()).thenAnswer(invocacion -> new ArrayList<>(persistidos));
        when(trabajos.save(any(Trabajo.class))).thenAnswer(invocacion -> {
            Trabajo trabajo = invocacion.getArgument(0);
            if (!persistidos.contains(trabajo)) persistidos.add(trabajo);
            return trabajo;
        });

        new DatosIniciales().migrarYCompletar(trabajos, configuracion);

        assertThat(persistidos)
                .hasSize(6)
                .extracting(Trabajo::getTitulo)
                .containsExactlyInAnyOrderElementsOf(TITULOS_LOCALES)
                .doesNotContainAnyElementsOf(TITULOS_DEMO);

        for (int indice = 0; indice < TITULOS_LOCALES.size(); indice++) {
            int indiceActual = indice;
            Trabajo migrado = buscarPorTitulo(persistidos, TITULOS_LOCALES.get(indice));
            assertThat(migrado.getImagenUrl()).isEqualTo(IMAGENES_LOCALES.get(indiceActual));
            assertThat(migrado.getMedios()).singleElement().satisfies(medio -> {
                assertThat(medio.getTipo()).isEqualTo(TipoMedio.IMAGEN);
                assertThat(medio.getRutaPublica()).isEqualTo(IMAGENES_LOCALES.get(indiceActual));
                assertThat(medio.getRutaMiniatura()).isEqualTo(IMAGENES_LOCALES.get(indiceActual));
                assertThat(medio.isGestionado()).isFalse();
                assertThat(medio.getTrabajo()).isSameAs(migrado);
            });
        }

        assertThat(buscarPorTitulo(persistidos, TITULOS_LOCALES.get(0)).getCategoria())
                .isEqualTo(CategoriaTrabajo.PREINSTALACIONES);
        assertThat(buscarPorTitulo(persistidos, TITULOS_LOCALES.get(2)).getCategoria())
                .isEqualTo(CategoriaTrabajo.CONDUCTOS);
        assertThat(buscarPorTitulo(persistidos, TITULOS_LOCALES.get(3)).getCategoria())
                .isEqualTo(CategoriaTrabajo.SISTEMAS);
        assertThat(buscarPorTitulo(persistidos, TITULOS_LOCALES.get(5)).getCategoria())
                .isEqualTo(CategoriaTrabajo.MONTAJES);

        verify(configuracion).registrarSiAusente("trabajos-inicializados", "1");
    }

    @Test
    void noVuelveACrearEjemplosSiElMarcadorExisteYElPortfolioEstaVacio() {
        TrabajoRepository trabajos = mock(TrabajoRepository.class);
        ConfiguracionAplicacionRepository configuracion = mock(ConfiguracionAplicacionRepository.class);

        when(configuracion.registrarSiAusente("trabajos-inicializados", "1")).thenReturn(0);

        new DatosIniciales().migrarYCompletar(trabajos, configuracion);

        verify(trabajos, never()).count();
        verify(trabajos, never()).save(any(Trabajo.class));
        verify(configuracion).registrarSiAusente("trabajos-inicializados", "1");
    }

    @Test
    void noSobrescribeUnTrabajoRealAunqueCoincidaSuTituloConUnDemo() {
        TrabajoRepository trabajos = mock(TrabajoRepository.class);
        ConfiguracionAplicacionRepository configuracion = mock(ConfiguracionAplicacionRepository.class);
        Trabajo trabajoReal = crearDemo(TITULOS_DEMO.get(0), 0);
        trabajoReal.setImagenUrl("/media/images/foto-del-cliente.jpg");

        when(configuracion.registrarSiAusente("trabajos-inicializados", "1")).thenReturn(1);
        when(trabajos.count()).thenReturn(1L);
        when(trabajos.buscarTodosConMedios()).thenReturn(List.of(trabajoReal));
        when(trabajos.save(any(Trabajo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        new DatosIniciales().migrarYCompletar(trabajos, configuracion);

        assertThat(trabajoReal.getTitulo()).isEqualTo(TITULOS_DEMO.get(0));
        assertThat(trabajoReal.getImagenUrl()).isEqualTo("/media/images/foto-del-cliente.jpg");
    }

    private Trabajo crearDemo(String titulo, int indice) {
        Trabajo trabajo = new Trabajo();
        trabajo.setTitulo(titulo);
        trabajo.setDescripcion("Descripción demo " + indice);
        trabajo.setTipoServicio("Categoría demo");
        trabajo.setUbicacion("Ubicación demo");
        trabajo.setFecha(LocalDate.of(2026, 1, indice + 1));
        trabajo.setImagenUrl(IMAGENES_DEMO.get(indice));
        return trabajo;
    }

    private Trabajo buscarPorTitulo(List<Trabajo> trabajos, String titulo) {
        return trabajos.stream()
                .filter(trabajo -> titulo.equals(trabajo.getTitulo()))
                .findFirst()
                .orElseThrow();
    }
}
