package com.friopolar.config;

import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.model.TipoMedio;
import com.friopolar.model.Trabajo;
import com.friopolar.model.TrabajoMedia;
import com.friopolar.repository.TrabajoRepository;
import com.friopolar.repository.ConfiguracionAplicacionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

@Configuration
public class DatosIniciales {
    private static final String CLAVE_TRABAJOS_INICIALIZADOS = "trabajos-inicializados";
    private static final List<TrabajoBase> TRABAJOS_BASE = List.of(
            new TrabajoBase(
                    "Instalación Split 4500 frigorías",
                    "https://images.unsplash.com/photo-1631545806609-8a83254468c6?auto=format&fit=crop&w=1200&q=80",
                    "Preinstalación: cañerías empotradas",
                    "Colocación de cañerías empotradas y caja de conexión antes del cierre de paredes.",
                    CategoriaTrabajo.PREINSTALACIONES,
                    "/img/trabajos/preinstalacion-empotrada.jpeg",
                    0
            ),
            new TrabajoBase(
                    "Limpieza completa de equipo",
                    "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=1200&q=80",
                    "Distribución de cañerías en obra",
                    "Distribución de cañerías y cajas de preinstalación durante la etapa de obra.",
                    CategoriaTrabajo.PREINSTALACIONES,
                    "/img/trabajos/canerias-en-obra.jpeg",
                    1
            ),
            new TrabajoBase(
                    "Instalación residencial",
                    "https://images.unsplash.com/photo-1590756254933-2873d72a83b6?auto=format&fit=crop&w=1200&q=80",
                    "Conductos galvanizados y distribución de aire",
                    "Armado y montaje de conductos galvanizados para distribuir el aire entre ambientes.",
                    CategoriaTrabajo.CONDUCTOS,
                    "/img/trabajos/conductos-galvanizados.jpeg",
                    2
            ),
            new TrabajoBase(
                    "Reparación de unidad exterior",
                    "https://images.unsplash.com/photo-1581094794329-c8112a89af12?auto=format&fit=crop&w=1200&q=80",
                    "Equipo bajo silueta: montaje y conexión",
                    "Montaje suspendido y conexión de un equipo bajo silueta integrado al sistema.",
                    CategoriaTrabajo.SISTEMAS,
                    "/img/trabajos/equipo-bajo-silueta.jpeg",
                    3
            ),
            new TrabajoBase(
                    "Reubicación de aire acondicionado",
                    "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1200&q=80",
                    "Instalación y distribución por conductos",
                    "Instalación de equipos y conexión con la red de distribución de aire por conductos.",
                    CategoriaTrabajo.SISTEMAS,
                    "/img/trabajos/climatizacion-por-conductos.jpeg",
                    4
            ),
            new TrabajoBase(
                    null,
                    null,
                    "Equipos exteriores sobre estructura de apoyo",
                    "Montaje de unidades exteriores en línea sobre estructuras metálicas de apoyo.",
                    CategoriaTrabajo.MONTAJES,
                    "/img/trabajos/equipos-exteriores.jpeg",
                    5
            )
    );

    @Bean
    CommandLineRunner cargarDatos(
            TrabajoRepository repository,
            ConfiguracionAplicacionRepository configuracionRepository,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        return args -> transaccion.executeWithoutResult(
                estado -> migrarYCompletar(repository, configuracionRepository)
        );
    }

    void migrarYCompletar(TrabajoRepository repository, ConfiguracionAplicacionRepository configuracionRepository) {
        boolean primeraInicializacion = configuracionRepository.registrarSiAusente(
                CLAVE_TRABAJOS_INICIALIZADOS,
                "1"
        ) == 1;
        if (!primeraInicializacion) return;

        if (repository.count() == 0) {
            TRABAJOS_BASE.forEach(datos -> repository.save(crear(datos)));
        }

        List<Trabajo> trabajos = repository.buscarTodosConMedios();
        boolean seMigraronDemos = false;
        for (TrabajoBase datos : TRABAJOS_BASE) {
            if (datos.tituloAnterior() == null) continue;
            Trabajo demo = trabajos.stream()
                    .filter(trabajo -> datos.tituloAnterior().equals(trabajo.getTitulo()))
                    .filter(trabajo -> datos.imagenAnterior().equals(trabajo.getImagenUrl()))
                    .findFirst()
                    .orElse(null);
            if (demo != null) {
                actualizarDemo(demo, datos);
                repository.save(demo);
                seMigraronDemos = true;
            }
        }

        TrabajoBase ultimoTrabajo = TRABAJOS_BASE.get(TRABAJOS_BASE.size() - 1);
        boolean yaExisteUltimo = trabajos.stream().anyMatch(trabajo -> ultimoTrabajo.titulo().equals(trabajo.getTitulo()));
        if (seMigraronDemos && !yaExisteUltimo) {
            repository.save(crear(ultimoTrabajo));
        }

        for (Trabajo trabajo : repository.buscarTodosConMedios()) {
            boolean modificado = false;
            String codigoNormalizado = CategoriaTrabajo.desdeValorGuardado(trabajo.getTipoServicio()).getCodigo();
            if (!codigoNormalizado.equals(trabajo.getTipoServicio())) {
                trabajo.setTipoServicio(codigoNormalizado);
                modificado = true;
            }

            if (trabajo.getMedios().isEmpty()) {
                int orden = 0;
                if (trabajo.getImagenUrl() != null && !trabajo.getImagenUrl().isBlank()) {
                    trabajo.agregarMedio(crearMedioLegacy(
                            TipoMedio.IMAGEN,
                            trabajo.getImagenUrl(),
                            "image/jpeg",
                            orden++
                    ));
                }
                if (trabajo.getVideoUrl() != null && !trabajo.getVideoUrl().isBlank()) {
                    trabajo.agregarMedio(crearMedioLegacy(
                            TipoMedio.VIDEO,
                            trabajo.getVideoUrl(),
                            tipoVideo(trabajo.getVideoUrl()),
                            orden
                    ));
                }
                modificado = modificado || !trabajo.getMedios().isEmpty();
            }

            if (modificado) repository.save(trabajo);
        }

    }

    private Trabajo crear(TrabajoBase datos) {
        Trabajo trabajo = new Trabajo();
        trabajo.setTitulo(datos.titulo());
        trabajo.setDescripcion(datos.descripcion());
        trabajo.setTipoServicio(datos.categoria().getCodigo());
        trabajo.setUbicacion("");
        trabajo.setFecha(LocalDate.now().minusDays(datos.diasDesdePublicacion()));
        trabajo.setImagenUrl(datos.imagen());
        trabajo.setDestacado(true);
        trabajo.agregarMedio(crearMedioLegacy(TipoMedio.IMAGEN, datos.imagen(), "image/jpeg", 0));
        return trabajo;
    }

    private void actualizarDemo(Trabajo trabajo, TrabajoBase datos) {
        List.copyOf(trabajo.getMedios()).forEach(trabajo::quitarMedio);
        trabajo.setTitulo(datos.titulo());
        trabajo.setDescripcion(datos.descripcion());
        trabajo.setTipoServicio(datos.categoria().getCodigo());
        trabajo.setUbicacion("");
        trabajo.setImagenUrl(datos.imagen());
        trabajo.setVideoUrl(null);
        trabajo.setDestacado(true);
        trabajo.agregarMedio(crearMedioLegacy(TipoMedio.IMAGEN, datos.imagen(), "image/jpeg", 0));
    }

    private TrabajoMedia crearMedioLegacy(TipoMedio tipo, String ruta, String contentType, int orden) {
        TrabajoMedia medio = new TrabajoMedia();
        medio.setTipo(tipo);
        medio.setRutaPublica(ruta);
        medio.setRutaMiniatura(tipo == TipoMedio.IMAGEN ? ruta : null);
        medio.setContentType(contentType);
        medio.setOrden(orden);
        medio.setGestionado(false);
        return medio;
    }

    private String tipoVideo(String ruta) {
        return ruta.toLowerCase().contains(".webm") ? "video/webm" : "video/mp4";
    }

    private record TrabajoBase(
            String tituloAnterior,
            String imagenAnterior,
            String titulo,
            String descripcion,
            CategoriaTrabajo categoria,
            String imagen,
            int diasDesdePublicacion
    ) { }
}
