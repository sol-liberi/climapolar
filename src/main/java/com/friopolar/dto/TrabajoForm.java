package com.friopolar.dto;

import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.model.Trabajo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TrabajoForm {
    private Long id;
    private Long version;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 160, message = "El título no puede superar los 160 caracteres")
    private String titulo;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
    private String descripcion;

    @NotBlank(message = "La categoría es obligatoria")
    @Pattern(
            regexp = "preinstalaciones|split|sistemas|conductos|montajes|otros",
            message = "La categoría seleccionada no es válida"
    )
    private String categoria = CategoriaTrabajo.OTROS.getCodigo();

    @Size(max = 120, message = "La ubicación no puede superar los 120 caracteres")
    private String ubicacion;

    @NotNull(message = "La fecha es obligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha = LocalDate.now();

    private boolean destacado;
    private List<MultipartFile> imagenes = new ArrayList<>();
    private List<MultipartFile> videos = new ArrayList<>();
    private List<Long> mediosAEliminar = new ArrayList<>();

    public static TrabajoForm desde(Trabajo trabajo) {
        TrabajoForm form = new TrabajoForm();
        form.setId(trabajo.getId());
        form.setVersion(trabajo.getVersion());
        form.setTitulo(trabajo.getTitulo());
        form.setDescripcion(trabajo.getDescripcion());
        form.setCategoria(trabajo.getCategoria().getCodigo());
        form.setUbicacion(trabajo.getUbicacion());
        form.setFecha(trabajo.getFecha());
        form.setDestacado(trabajo.isDestacado());
        return form;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public boolean isDestacado() { return destacado; }
    public void setDestacado(boolean destacado) { this.destacado = destacado; }
    public List<MultipartFile> getImagenes() { return imagenes; }
    public void setImagenes(List<MultipartFile> imagenes) { this.imagenes = imagenes == null ? new ArrayList<>() : imagenes; }
    public List<MultipartFile> getVideos() { return videos; }
    public void setVideos(List<MultipartFile> videos) { this.videos = videos == null ? new ArrayList<>() : videos; }
    public List<Long> getMediosAEliminar() { return mediosAEliminar; }
    public void setMediosAEliminar(List<Long> mediosAEliminar) { this.mediosAEliminar = mediosAEliminar == null ? new ArrayList<>() : mediosAEliminar; }
}
