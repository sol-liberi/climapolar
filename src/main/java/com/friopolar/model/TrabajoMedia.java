package com.friopolar.model;

import jakarta.persistence.*;

@Entity
@Table(name = "trabajo_medios")
public class TrabajoMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajo_id", nullable = false)
    private Trabajo trabajo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TipoMedio tipo;

    @Column(name = "ruta_publica", nullable = false, length = 1000)
    private String rutaPublica;

    @Column(name = "ruta_miniatura", length = 1000)
    private String rutaMiniatura;

    @Column(name = "nombre_original", length = 255)
    private String nombreOriginal;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamano_bytes")
    private long tamanoBytes;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private boolean gestionado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Trabajo getTrabajo() { return trabajo; }
    public void setTrabajo(Trabajo trabajo) { this.trabajo = trabajo; }
    public TipoMedio getTipo() { return tipo; }
    public void setTipo(TipoMedio tipo) { this.tipo = tipo; }
    public String getRutaPublica() { return rutaPublica; }
    public void setRutaPublica(String rutaPublica) { this.rutaPublica = rutaPublica; }
    public String getRutaMiniatura() { return rutaMiniatura; }
    public void setRutaMiniatura(String rutaMiniatura) { this.rutaMiniatura = rutaMiniatura; }
    public String getNombreOriginal() { return nombreOriginal; }
    public void setNombreOriginal(String nombreOriginal) { this.nombreOriginal = nombreOriginal; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public boolean isGestionado() { return gestionado; }
    public void setGestionado(boolean gestionado) { this.gestionado = gestionado; }
    public boolean isImagen() { return tipo == TipoMedio.IMAGEN; }
    public boolean isVideo() { return tipo == TipoMedio.VIDEO; }
    public String getMiniaturaOOriginal() {
        return rutaMiniatura == null || rutaMiniatura.isBlank() ? rutaPublica : rutaMiniatura;
    }
}
