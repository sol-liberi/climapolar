package com.friopolar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trabajos")
public class Trabajo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 255, message = "El título no puede superar los 255 caracteres")
    @Column(length = 255)
    private String titulo;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
    @Column(length = 2000)
    private String descripcion;

    @Column(nullable = false)
    private String tipoServicio = CategoriaTrabajo.OTROS.getCodigo();

    @Column(nullable = false)
    private String ubicacion = "";

    @NotNull(message = "La fecha es obligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha;

    // Se mantienen para migrar bases existentes cuya columna imagen_url es NOT NULL.
    @Column(length = 1000)
    private String imagenUrl = "";

    @Column(length = 1000)
    private String videoUrl;

    private boolean destacado;

    @OneToMany(mappedBy = "trabajo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    private List<TrabajoMedia> medios = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(String tipoServicio) { this.tipoServicio = tipoServicio; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public boolean isDestacado() { return destacado; }
    public void setDestacado(boolean destacado) { this.destacado = destacado; }
    public List<TrabajoMedia> getMedios() { return medios; }

    public void agregarMedio(TrabajoMedia medio) {
        medio.setTrabajo(this);
        medios.add(medio);
    }

    public void quitarMedio(TrabajoMedia medio) {
        medios.remove(medio);
        medio.setTrabajo(null);
    }

    @Transient
    public CategoriaTrabajo getCategoria() {
        return CategoriaTrabajo.desdeValorGuardado(tipoServicio);
    }

    @Transient
    public TrabajoMedia getPortada() {
        return medios.isEmpty() ? null : medios.get(0);
    }

    @Transient
    public long getCantidadImagenes() {
        return medios.stream().filter(TrabajoMedia::isImagen).count();
    }

    @Transient
    public long getCantidadVideos() {
        return medios.stream().filter(TrabajoMedia::isVideo).count();
    }
}
