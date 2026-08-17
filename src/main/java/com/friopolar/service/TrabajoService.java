package com.friopolar.service;

import com.friopolar.dto.TrabajoDetalleDto;
import com.friopolar.dto.TrabajoForm;
import com.friopolar.dto.TrabajoPaginaDto;
import com.friopolar.dto.TrabajoResumenDto;
import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.model.Trabajo;
import com.friopolar.model.TrabajoMedia;
import com.friopolar.repository.TrabajoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TrabajoService {
    private static final int MAX_IMAGENES = 20;
    private static final int MAX_VIDEOS = 6;
    private static final int MAX_MEDIOS = 24;
    private static final long MAX_OFFSET_PAGINACION = 100_000L;
    private static final String MENSAJE_CONFLICTO_AL_ELIMINAR =
            "Este trabajo fue modificado por otra sesión. Recargá la lista antes de eliminarlo.";

    private final TrabajoRepository repository;
    private final MediaStorageService storage;

    public TrabajoService(TrabajoRepository repository, MediaStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<Trabajo> listarTodos() {
        return repository.buscarTodosConMedios();
    }

    @Transactional(readOnly = true)
    public Trabajo buscarPorId(Long id) {
        return repository.buscarPorIdConMedios(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trabajo no encontrado"));
    }

    @Transactional(readOnly = true)
    public TrabajoPaginaDto listarPagina(String categoria, boolean soloDestacados, int pagina, int tamano) {
        if (pagina < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de página no puede ser negativo");
        }
        if (tamano < 1 || tamano > 24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tamaño de página debe estar entre 1 y 24");
        }
        if ((long) pagina * tamano > MAX_OFFSET_PAGINACION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La página solicitada está fuera del rango permitido");
        }
        var pageable = PageRequest.of(pagina, tamano);

        Page<Trabajo> trabajos;
        if (categoria == null || categoria.isBlank()) {
            trabajos = soloDestacados
                    ? repository.findByDestacadoTrueOrderByFechaDescIdDesc(pageable)
                    : repository.findAllByOrderByFechaDescIdDesc(pageable);
        } else {
            CategoriaTrabajo categoriaValida = CategoriaTrabajo.buscarPorCodigo(categoria)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría inválida"));
            trabajos = soloDestacados
                    ? repository.findByTipoServicioAndDestacadoTrueOrderByFechaDescIdDesc(categoriaValida.getCodigo(), pageable)
                    : repository.findByTipoServicioOrderByFechaDescIdDesc(categoriaValida.getCodigo(), pageable);
        }

        Page<TrabajoResumenDto> resultado = trabajos.map(TrabajoResumenDto::desde);
        return TrabajoPaginaDto.desde(resultado);
    }

    @Transactional(readOnly = true)
    public TrabajoDetalleDto buscarDetalleDto(Long id) {
        return TrabajoDetalleDto.desde(buscarPorId(id));
    }

    @Transactional
    public Trabajo guardar(TrabajoForm form) {
        Trabajo trabajo = form.getId() == null ? new Trabajo() : buscarParaEditar(form);
        List<MultipartFile> imagenes = archivosPresentes(form.getImagenes());
        List<MultipartFile> videos = archivosPresentes(form.getVideos());
        Set<Long> idsAEliminar = new HashSet<>(form.getMediosAEliminar());

        validarIdsDeMedios(trabajo, idsAEliminar);
        List<TrabajoMedia> conservados = trabajo.getMedios().stream()
                .filter(medio -> medio.getId() == null || !idsAEliminar.contains(medio.getId()))
                .toList();
        validarCantidad(conservados, imagenes.size(), videos.size());

        mapearCampos(form, trabajo);
        List<TrabajoMedia> nuevos = new ArrayList<>();
        List<TrabajoMedia> eliminados = trabajo.getMedios().stream()
                .filter(medio -> medio.getId() != null && idsAEliminar.contains(medio.getId()))
                .toList();

        try {
            eliminados.forEach(trabajo::quitarMedio);
            int orden = trabajo.getMedios().size();
            for (MultipartFile imagen : imagenes) {
                TrabajoMedia medio = storage.guardarImagen(imagen, orden++);
                nuevos.add(medio);
                trabajo.agregarMedio(medio);
            }
            for (MultipartFile video : videos) {
                TrabajoMedia medio = storage.guardarVideo(video, orden++);
                nuevos.add(medio);
                trabajo.agregarMedio(medio);
            }
            reordenar(trabajo);
            sincronizarCamposHeredados(trabajo);
            Trabajo guardado = repository.saveAndFlush(trabajo);
            registrarLimpiezaTransaccional(nuevos, eliminados);
            return guardado;
        } catch (OptimisticLockingFailureException ex) {
            nuevos.forEach(storage::eliminar);
            throw new MediaStorageException(
                    "Este trabajo fue modificado por otra sesión. Recargá la página antes de guardar tus cambios.",
                    ex
            );
        } catch (RuntimeException ex) {
            nuevos.forEach(storage::eliminar);
            throw ex;
        }
    }

    @Transactional
    public void eliminar(Long id, Long version) {
        Trabajo trabajo = buscarPorId(id);
        if (version == null || version != trabajo.getVersion()) {
            throw new MediaStorageException(MENSAJE_CONFLICTO_AL_ELIMINAR);
        }

        List<TrabajoMedia> medios = List.copyOf(trabajo.getMedios());
        try {
            repository.delete(trabajo);
            repository.flush();
            registrarBorradoDespuesDelCommit(medios);
        } catch (OptimisticLockingFailureException ex) {
            throw new MediaStorageException(MENSAJE_CONFLICTO_AL_ELIMINAR, ex);
        }
    }

    private void mapearCampos(TrabajoForm form, Trabajo trabajo) {
        CategoriaTrabajo categoria = CategoriaTrabajo.buscarPorCodigo(form.getCategoria())
                .orElseThrow(() -> new MediaStorageException("La categoría seleccionada no es válida."));
        trabajo.setTitulo(form.getTitulo().trim());
        trabajo.setDescripcion(form.getDescripcion().trim());
        trabajo.setTipoServicio(categoria.getCodigo());
        trabajo.setUbicacion(form.getUbicacion() == null ? "" : form.getUbicacion().trim());
        trabajo.setFecha(form.getFecha());
        trabajo.setDestacado(form.isDestacado());
    }

    private Trabajo buscarParaEditar(TrabajoForm form) {
        Trabajo trabajo = buscarPorId(form.getId());
        if (form.getVersion() == null || form.getVersion() != trabajo.getVersion()) {
            throw new MediaStorageException(
                    "Este trabajo fue modificado por otra sesión. Recargá la página antes de guardar tus cambios."
            );
        }
        return trabajo;
    }

    private void validarIdsDeMedios(Trabajo trabajo, Set<Long> idsAEliminar) {
        if (idsAEliminar.isEmpty()) return;
        Set<Long> idsPropios = new HashSet<>();
        trabajo.getMedios().stream().map(TrabajoMedia::getId).forEach(idsPropios::add);
        if (!idsPropios.containsAll(idsAEliminar)) {
            throw new MediaStorageException("No se pudo identificar uno de los archivos seleccionados para eliminar.");
        }
    }

    private void validarCantidad(List<TrabajoMedia> conservados, int nuevasImagenes, int nuevosVideos) {
        long imagenes = conservados.stream().filter(TrabajoMedia::isImagen).count() + nuevasImagenes;
        long videos = conservados.stream().filter(TrabajoMedia::isVideo).count() + nuevosVideos;
        long total = imagenes + videos;
        if (total == 0) throw new MediaStorageException("Agregá al menos una fotografía o un video.");
        if (imagenes > MAX_IMAGENES) throw new MediaStorageException("Podés publicar hasta 20 fotografías por trabajo.");
        if (videos > MAX_VIDEOS) throw new MediaStorageException("Podés publicar hasta 6 videos por trabajo.");
        if (total > MAX_MEDIOS) throw new MediaStorageException("Podés publicar hasta 24 archivos por trabajo.");
    }

    private List<MultipartFile> archivosPresentes(List<MultipartFile> archivos) {
        if (archivos == null) return List.of();
        return archivos.stream().filter(archivo -> archivo != null && !archivo.isEmpty()).toList();
    }

    private void reordenar(Trabajo trabajo) {
        for (int indice = 0; indice < trabajo.getMedios().size(); indice++) {
            trabajo.getMedios().get(indice).setOrden(indice);
        }
    }

    private void sincronizarCamposHeredados(Trabajo trabajo) {
        trabajo.setImagenUrl(trabajo.getMedios().stream()
                .filter(TrabajoMedia::isImagen)
                .map(TrabajoMedia::getRutaPublica)
                .findFirst()
                .orElse(""));
        trabajo.setVideoUrl(trabajo.getMedios().stream()
                .filter(TrabajoMedia::isVideo)
                .map(TrabajoMedia::getRutaPublica)
                .findFirst()
                .orElse(null));
    }

    private void registrarLimpiezaTransaccional(List<TrabajoMedia> nuevos, List<TrabajoMedia> eliminados) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) eliminados.forEach(storage::eliminar);
                else nuevos.forEach(storage::eliminar);
            }
        });
    }

    private void registrarBorradoDespuesDelCommit(List<TrabajoMedia> medios) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                medios.forEach(storage::eliminar);
            }
        });
    }
}
