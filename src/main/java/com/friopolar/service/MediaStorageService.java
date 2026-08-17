package com.friopolar.service;

import com.friopolar.config.MediaStorageProperties;
import com.friopolar.model.TipoMedio;
import com.friopolar.model.TrabajoMedia;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaStorageService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaStorageService.class);
    public static final long MAX_IMAGEN_BYTES = 15L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 150L * 1024 * 1024;
    private static final long MAX_PIXELES_ORIGINALES = 50_000_000L;
    private static final int MAX_LADO_ORIGINAL = 12_000;
    private static final int MAX_LADO_DECODIFICADO = 3000;
    private static final int MAX_LADO_IMAGEN = 1920;
    private static final int MAX_LADO_MINIATURA = 900;
    private static final int MAX_BYTES_LECTURA_EXIF = 512 * 1024;
    private static final int MAX_CABECERA_WEBM = 1024 * 1024;
    private static final Set<String> MARCAS_MP4 = Set.of(
            "isom", "mp41", "mp42", "avc1", "M4V ", "M4A ", "MSNV", "dash"
    );
    private static final Pattern NOMBRE_GESTIONADO = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:-thumb)?\\.(?:jpg|mp4|webm)$"
    );

    private final Path raiz;
    private Path imagenesDir;
    private Path videosDir;

    public MediaStorageService(MediaStorageProperties properties) {
        this.raiz = properties.getUploadDir().toAbsolutePath().normalize();
    }

    @PostConstruct
    void inicializar() {
        try {
            imagenesDir = crearDirectorioSeguro("images");
            videosDir = crearDirectorioSeguro("videos");
        } catch (IOException ex) {
            throw new MediaStorageException("No se pudo preparar el almacenamiento de archivos.", ex);
        }
    }

    public TrabajoMedia guardarImagen(MultipartFile archivo, int orden) {
        validarArchivoPresente(archivo, "imagen");
        if (archivo.getSize() > MAX_IMAGEN_BYTES) {
            throw new MediaStorageException("Cada imagen puede pesar como máximo 15 MB.");
        }

        String base = UUID.randomUUID().toString();
        Path destino = resolverSeguro(imagenesDir, base + ".jpg");
        Path miniatura = resolverSeguro(imagenesDir, base + "-thumb.jpg");
        try {
            int orientacionExif = leerOrientacionExif(archivo);
            BufferedImage decodificada = leerImagenVerificada(archivo);
            BufferedImage orientada = aplicarOrientacion(decodificada, orientacionExif);
            if (orientada != decodificada) decodificada.flush();
            try {
                BufferedImage optimizada = escalar(orientada, MAX_LADO_IMAGEN);
                try {
                    escribirJpeg(optimizada, destino, .86f);
                } finally {
                    optimizada.flush();
                }

                BufferedImage vistaPrevia = escalar(orientada, MAX_LADO_MINIATURA);
                try {
                    escribirJpeg(vistaPrevia, miniatura, .8f);
                } finally {
                    vistaPrevia.flush();
                }
            } finally {
                orientada.flush();
            }

            TrabajoMedia medio = crearMedioBase(archivo, TipoMedio.IMAGEN, orden);
            medio.setRutaPublica(rutaPublica(destino));
            medio.setRutaMiniatura(rutaPublica(miniatura));
            medio.setContentType("image/jpeg");
            medio.setTamanoBytes(Files.size(destino));
            return medio;
        } catch (MediaStorageException ex) {
            borrarSilenciosamente(destino);
            borrarSilenciosamente(miniatura);
            throw ex;
        } catch (IOException ex) {
            borrarSilenciosamente(destino);
            borrarSilenciosamente(miniatura);
            throw new MediaStorageException("No se pudo procesar la imagen seleccionada.", ex);
        }
    }

    public TrabajoMedia guardarVideo(MultipartFile archivo, int orden) {
        validarArchivoPresente(archivo, "video");
        if (archivo.getSize() > MAX_VIDEO_BYTES) {
            throw new MediaStorageException("Cada video puede pesar como máximo 150 MB.");
        }

        Path temporal = null;
        Path destino = null;
        try {
            temporal = Files.createTempFile(videosDir, "upload-", ".tmp");
            try (InputStream input = archivo.getInputStream()) {
                Files.copy(input, temporal, StandardCopyOption.REPLACE_EXISTING);
            }
            long tamano = Files.size(temporal);
            if (tamano == 0 || tamano > MAX_VIDEO_BYTES) {
                throw new MediaStorageException("El video está vacío o supera el máximo de 150 MB.");
            }

            FormatoVideo formato = detectarFormatoVideo(temporal);
            destino = resolverSeguro(videosDir, UUID.randomUUID() + formato.extension());
            moverAtomico(temporal, destino);
            temporal = null;

            TrabajoMedia medio = crearMedioBase(archivo, TipoMedio.VIDEO, orden);
            medio.setRutaPublica(rutaPublica(destino));
            medio.setContentType(formato.contentType());
            medio.setTamanoBytes(tamano);
            return medio;
        } catch (MediaStorageException ex) {
            borrarSilenciosamente(temporal);
            borrarSilenciosamente(destino);
            throw ex;
        } catch (IOException ex) {
            borrarSilenciosamente(temporal);
            borrarSilenciosamente(destino);
            throw new MediaStorageException("No se pudo guardar el video seleccionado.", ex);
        }
    }

    public void eliminar(TrabajoMedia medio) {
        if (medio == null || !medio.isGestionado()) return;
        borrarRutaPublica(medio.getRutaPublica());
        if (medio.getRutaMiniatura() != null && !medio.getRutaMiniatura().equals(medio.getRutaPublica())) {
            borrarRutaPublica(medio.getRutaMiniatura());
        }
    }

    int reconciliarHuerfanos(Set<String> rutasReferenciadas, Duration antiguedadMinima) {
        Set<String> referencias = rutasReferenciadas == null ? Set.of() : Set.copyOf(rutasReferenciadas);
        Instant limite = Instant.now().minus(antiguedadMinima);
        return reconciliarDirectorio(imagenesDir, referencias, limite)
                + reconciliarDirectorio(videosDir, referencias, limite);
    }

    private int reconciliarDirectorio(Path directorio, Set<String> referencias, Instant limite) {
        int eliminados = 0;
        try (var archivos = Files.list(directorio)) {
            for (Path archivo : archivos.toList()) {
                if (!Files.isRegularFile(archivo, LinkOption.NOFOLLOW_LINKS)) continue;
                String nombre = archivo.getFileName().toString();
                boolean temporal = nombre.endsWith(".tmp")
                        && (nombre.startsWith("upload-") || nombre.startsWith("image-"));
                if (!temporal && !NOMBRE_GESTIONADO.matcher(nombre).matches()) continue;
                if (!temporal && referencias.contains(rutaPublica(archivo))) continue;
                if (!Files.getLastModifiedTime(archivo, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(limite)) continue;
                if (Files.deleteIfExists(archivo)) eliminados++;
            }
        } catch (IOException ex) {
            LOG.warn("No se pudo completar la reconciliación del directorio multimedia {}", directorio, ex);
        }
        if (eliminados > 0) {
            LOG.info("Se eliminaron {} archivos multimedia huérfanos o temporales de {}", eliminados, directorio);
        }
        return eliminados;
    }

    private TrabajoMedia crearMedioBase(MultipartFile archivo, TipoMedio tipo, int orden) {
        TrabajoMedia medio = new TrabajoMedia();
        medio.setTipo(tipo);
        medio.setOrden(orden);
        medio.setNombreOriginal(nombreSeguro(archivo.getOriginalFilename()));
        medio.setGestionado(true);
        return medio;
    }

    private int leerOrientacionExif(MultipartFile archivo) {
        try (InputStream input = archivo.getInputStream()) {
            return extraerOrientacionExif(input.readNBytes(MAX_BYTES_LECTURA_EXIF));
        } catch (IOException ex) {
            LOG.debug("No se pudo leer la orientación EXIF de {}", nombreSeguro(archivo.getOriginalFilename()), ex);
            return 1;
        }
    }

    private int extraerOrientacionExif(byte[] datos) {
        if (datos.length < 4 || unsigned(datos[0]) != 0xff || unsigned(datos[1]) != 0xd8) return 1;

        int posicion = 2;
        while (posicion + 4 <= datos.length) {
            if (unsigned(datos[posicion]) != 0xff) {
                posicion++;
                continue;
            }
            while (posicion < datos.length && unsigned(datos[posicion]) == 0xff) posicion++;
            if (posicion >= datos.length) break;

            int marcador = unsigned(datos[posicion++]);
            if (marcador == 0xd9 || marcador == 0xda) break;
            if (marcador == 0x01 || marcador >= 0xd0 && marcador <= 0xd7) continue;
            if (posicion + 2 > datos.length) break;

            int longitudSegmento = leerEntero16Grande(datos, posicion);
            if (longitudSegmento < 2) break;
            int inicioContenido = posicion + 2;
            long finDeclarado = (long) inicioContenido + longitudSegmento - 2;
            int finDisponible = (int) Math.min(finDeclarado, datos.length);

            if (marcador == 0xe1 && empiezaConExif(datos, inicioContenido, finDisponible)) {
                int orientacion = extraerOrientacionTiff(datos, inicioContenido + 6, finDisponible);
                if (orientacion >= 1 && orientacion <= 8) return orientacion;
            }

            if (finDeclarado > datos.length) break;
            posicion = (int) finDeclarado;
        }
        return 1;
    }

    private boolean empiezaConExif(byte[] datos, int inicio, int limite) {
        return limite - inicio >= 6
                && datos[inicio] == 'E'
                && datos[inicio + 1] == 'x'
                && datos[inicio + 2] == 'i'
                && datos[inicio + 3] == 'f'
                && datos[inicio + 4] == 0
                && datos[inicio + 5] == 0;
    }

    private int extraerOrientacionTiff(byte[] datos, int inicioTiff, int limite) {
        if (inicioTiff < 0 || limite - inicioTiff < 8) return 0;
        boolean littleEndian;
        if (datos[inicioTiff] == 'I' && datos[inicioTiff + 1] == 'I') littleEndian = true;
        else if (datos[inicioTiff] == 'M' && datos[inicioTiff + 1] == 'M') littleEndian = false;
        else return 0;

        if (leerEntero16(datos, inicioTiff + 2, littleEndian) != 42) return 0;
        long desplazamientoIfd = leerEntero32(datos, inicioTiff + 4, littleEndian);
        long posicionIfdLarga = (long) inicioTiff + desplazamientoIfd;
        if (posicionIfdLarga < inicioTiff || posicionIfdLarga + 2 > limite) return 0;

        int posicionEntrada = (int) posicionIfdLarga + 2;
        int cantidadEntradas = leerEntero16(datos, (int) posicionIfdLarga, littleEndian);
        for (int indice = 0; indice < cantidadEntradas; indice++, posicionEntrada += 12) {
            if ((long) posicionEntrada + 12 > limite) return 0;
            if (leerEntero16(datos, posicionEntrada, littleEndian) != 0x0112) continue;

            int tipo = leerEntero16(datos, posicionEntrada + 2, littleEndian);
            long cantidad = leerEntero32(datos, posicionEntrada + 4, littleEndian);
            if (tipo != 3 || cantidad < 1) return 0;
            return leerEntero16(datos, posicionEntrada + 8, littleEndian);
        }
        return 0;
    }

    private int leerEntero16Grande(byte[] datos, int posicion) {
        return unsigned(datos[posicion]) << 8 | unsigned(datos[posicion + 1]);
    }

    private int leerEntero16(byte[] datos, int posicion, boolean littleEndian) {
        int primero = unsigned(datos[posicion]);
        int segundo = unsigned(datos[posicion + 1]);
        return littleEndian ? primero | segundo << 8 : primero << 8 | segundo;
    }

    private long leerEntero32(byte[] datos, int posicion, boolean littleEndian) {
        if (littleEndian) {
            return (long) unsigned(datos[posicion])
                    | (long) unsigned(datos[posicion + 1]) << 8
                    | (long) unsigned(datos[posicion + 2]) << 16
                    | (long) unsigned(datos[posicion + 3]) << 24;
        }
        return (long) unsigned(datos[posicion]) << 24
                | (long) unsigned(datos[posicion + 1]) << 16
                | (long) unsigned(datos[posicion + 2]) << 8
                | unsigned(datos[posicion + 3]);
    }

    private int unsigned(byte valor) {
        return valor & 0xff;
    }

    private BufferedImage aplicarOrientacion(BufferedImage origen, int orientacion) {
        if (orientacion <= 1 || orientacion > 8) return origen;

        int ancho = origen.getWidth();
        int alto = origen.getHeight();
        boolean intercambiaLados = orientacion >= 5;
        BufferedImage salida = new BufferedImage(
                intercambiaLados ? alto : ancho,
                intercambiaLados ? ancho : alto,
                origen.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );

        AffineTransform transformacion = switch (orientacion) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, ancho, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, ancho, alto);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, alto);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, alto, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, alto, ancho);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, ancho);
            default -> new AffineTransform();
        };

        Graphics2D graficos = salida.createGraphics();
        try {
            graficos.drawImage(origen, transformacion, null);
        } finally {
            graficos.dispose();
        }
        return salida;
    }

    private BufferedImage leerImagenVerificada(MultipartFile archivo) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(archivo.getInputStream())) {
            if (input == null) throw new MediaStorageException("El archivo seleccionado no es una imagen válida.");
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(input);
            if (!lectores.hasNext()) throw new MediaStorageException("Solo se admiten imágenes JPEG o PNG válidas.");

            ImageReader lector = lectores.next();
            try {
                String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("jpeg") && !formato.equals("jpg") && !formato.equals("png")) {
                    throw new MediaStorageException("Solo se admiten imágenes JPEG o PNG.");
                }
                lector.setInput(input, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0
                        || ancho > MAX_LADO_ORIGINAL || alto > MAX_LADO_ORIGINAL
                        || (long) ancho * alto > MAX_PIXELES_ORIGINALES) {
                    throw new MediaStorageException("La imagen tiene dimensiones demasiado grandes.");
                }
                int ladoMayor = Math.max(ancho, alto);
                int submuestreo = Math.max(1, (int) Math.ceil((double) ladoMayor / MAX_LADO_DECODIFICADO));
                ImageReadParam parametros = lector.getDefaultReadParam();
                parametros.setSourceSubsampling(submuestreo, submuestreo, 0, 0);
                BufferedImage imagen = lector.read(0, parametros);
                if (imagen == null) throw new MediaStorageException("No se pudo leer la imagen seleccionada.");
                return imagen;
            } finally {
                lector.dispose();
            }
        }
    }

    private BufferedImage escalar(BufferedImage origen, int maximo) {
        double escala = Math.min(1d, Math.min((double) maximo / origen.getWidth(), (double) maximo / origen.getHeight()));
        int ancho = Math.max(1, (int) Math.round(origen.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(origen.getHeight() * escala));
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D graficos = salida.createGraphics();
        try {
            graficos.setColor(Color.WHITE);
            graficos.fillRect(0, 0, ancho, alto);
            graficos.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graficos.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graficos.drawImage(origen, 0, 0, ancho, alto, null);
        } finally {
            graficos.dispose();
        }
        return salida;
    }

    private void escribirJpeg(BufferedImage imagen, Path destino, float calidad) throws IOException {
        Path temporal = Files.createTempFile(destino.getParent(), "image-", ".tmp");
        try {
            Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpeg");
            if (!escritores.hasNext()) throw new IOException("No hay codificador JPEG disponible");
            ImageWriter escritor = escritores.next();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(temporal.toFile())) {
                escritor.setOutput(output);
                ImageWriteParam parametros = escritor.getDefaultWriteParam();
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality(calidad);
                escritor.write(null, new IIOImage(imagen, null, null), parametros);
            } finally {
                escritor.dispose();
            }
            moverAtomico(temporal, destino);
        } finally {
            borrarSilenciosamente(temporal);
        }
    }

    private FormatoVideo detectarFormatoVideo(Path archivo) throws IOException {
        if (esMp4Valido(archivo)) return new FormatoVideo(".mp4", "video/mp4");
        if (esWebmValido(archivo)) return new FormatoVideo(".webm", "video/webm");
        throw new MediaStorageException("El video debe estar en formato MP4 o WebM.");
    }

    private boolean esMp4Valido(Path archivo) throws IOException {
        long tamanoArchivo = Files.size(archivo);
        if (tamanoArchivo < 24) return false;

        boolean ftypValido = false;
        boolean contieneMoov = false;
        boolean contieneMdat = false;
        long posicion = 0;
        int cajasLeidas = 0;

        try (SeekableByteChannel canal = Files.newByteChannel(archivo, StandardOpenOption.READ)) {
            while (posicion + 8 <= tamanoArchivo && cajasLeidas++ < 10_000) {
                ByteBuffer cabecera = leerBytes(canal, posicion, 8);
                if (cabecera == null) return false;

                long tamanoCaja = Integer.toUnsignedLong(cabecera.getInt());
                byte[] tipoBytes = new byte[4];
                cabecera.get(tipoBytes);
                String tipo = new String(tipoBytes, StandardCharsets.ISO_8859_1);
                int tamanoCabecera = 8;

                if (tamanoCaja == 1) {
                    ByteBuffer extendida = leerBytes(canal, posicion + 8, 8);
                    if (extendida == null) return false;
                    tamanoCaja = extendida.getLong();
                    tamanoCabecera = 16;
                } else if (tamanoCaja == 0) {
                    tamanoCaja = tamanoArchivo - posicion;
                }

                if (tamanoCaja < tamanoCabecera || tamanoCaja > tamanoArchivo - posicion) return false;
                long tamanoContenido = tamanoCaja - tamanoCabecera;

                if ("ftyp".equals(tipo)) {
                    if (tamanoContenido < 8 || tamanoContenido > 64 * 1024) return false;
                    ByteBuffer marcas = leerBytes(canal, posicion + tamanoCabecera, (int) tamanoContenido);
                    if (marcas == null) return false;
                    ftypValido = contieneMarcaMp4(marcas);
                } else if ("moov".equals(tipo)) {
                    contieneMoov = tamanoContenido >= 8;
                } else if ("mdat".equals(tipo)) {
                    contieneMdat = tamanoContenido > 0;
                }

                posicion += tamanoCaja;
            }
        }

        return posicion == tamanoArchivo && ftypValido && contieneMoov && contieneMdat;
    }

    private boolean contieneMarcaMp4(ByteBuffer marcas) {
        byte[] marcaBytes = new byte[4];
        marcas.get(marcaBytes);
        boolean compatible = esMarcaMp4(new String(marcaBytes, StandardCharsets.ISO_8859_1));
        marcas.getInt(); // minor_version
        while (marcas.remaining() >= 4) {
            marcas.get(marcaBytes);
            compatible |= esMarcaMp4(new String(marcaBytes, StandardCharsets.ISO_8859_1));
        }
        return compatible;
    }

    private boolean esMarcaMp4(String marca) {
        return MARCAS_MP4.contains(marca) || marca.startsWith("iso");
    }

    private ByteBuffer leerBytes(SeekableByteChannel canal, long posicion, int cantidad) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(cantidad).order(ByteOrder.BIG_ENDIAN);
        canal.position(posicion);
        while (bytes.hasRemaining()) {
            if (canal.read(bytes) < 0) return null;
        }
        bytes.flip();
        return bytes;
    }

    private boolean esWebmValido(Path archivo) throws IOException {
        long tamanoArchivo = Files.size(archivo);
        if (tamanoArchivo < 16) return false;

        int cantidad = (int) Math.min(tamanoArchivo, MAX_CABECERA_WEBM);
        byte[] datos;
        try (InputStream input = Files.newInputStream(archivo)) {
            datos = input.readNBytes(cantidad);
        }

        EbmlValor ebml = leerIdEbml(datos, 0);
        if (ebml == null || ebml.valor() != 0x1A45DFA3L) return false;
        EbmlValor tamanoCabecera = leerTamanoEbml(datos, ebml.siguiente());
        if (tamanoCabecera == null || tamanoCabecera.desconocido()) return false;
        long finCabeceraLargo = (long) tamanoCabecera.siguiente() + tamanoCabecera.valor();
        if (finCabeceraLargo > datos.length) return false;
        int finCabecera = (int) finCabeceraLargo;

        boolean docTypeWebm = false;
        int posicion = tamanoCabecera.siguiente();
        while (posicion < finCabecera) {
            EbmlValor id = leerIdEbml(datos, posicion);
            if (id == null) return false;
            EbmlValor tamano = leerTamanoEbml(datos, id.siguiente());
            if (tamano == null || tamano.desconocido()) return false;
            long finElementoLargo = (long) tamano.siguiente() + tamano.valor();
            if (finElementoLargo > finCabecera) return false;
            int finElemento = (int) finElementoLargo;
            if (id.valor() == 0x4282L) {
                String docType = new String(
                        datos,
                        tamano.siguiente(),
                        finElemento - tamano.siguiente(),
                        StandardCharsets.US_ASCII
                );
                docTypeWebm = "webm".equals(docType);
            }
            posicion = finElemento;
        }
        if (!docTypeWebm || posicion != finCabecera) return false;

        posicion = finCabecera;
        while (posicion < datos.length) {
            EbmlValor id = leerIdEbml(datos, posicion);
            if (id == null) return false;
            EbmlValor tamano = leerTamanoEbml(datos, id.siguiente());
            if (tamano == null) return false;
            if (id.valor() == 0x18538067L) {
                long finSegmento = tamano.desconocido()
                        ? tamanoArchivo
                        : (long) tamano.siguiente() + tamano.valor();
                if (finSegmento <= tamano.siguiente() || finSegmento > tamanoArchivo) return false;
                return contieneEstructuraWebm(datos, tamano.siguiente(), finSegmento);
            }
            if (tamano.desconocido()) return false;
            long siguiente = (long) tamano.siguiente() + tamano.valor();
            if (siguiente > datos.length) return false;
            posicion = (int) siguiente;
        }
        return false;
    }

    private boolean contieneEstructuraWebm(byte[] datos, int inicio, long finSegmento) {
        boolean contienePistas = false;
        int posicion = inicio;
        int limiteDisponible = (int) Math.min(finSegmento, datos.length);
        while (posicion < limiteDisponible) {
            EbmlValor id = leerIdEbml(datos, posicion);
            if (id == null) return false;
            EbmlValor tamano = leerTamanoEbml(datos, id.siguiente());
            if (tamano == null) return false;
            if (id.valor() == 0x1654AE6BL) {
                contienePistas = !tamano.desconocido() && tamano.valor() > 0;
            }
            if (id.valor() == 0x1F43B675L) {
                return contienePistas && (tamano.desconocido() || tamano.valor() > 0);
            }
            if (tamano.desconocido()) return false;
            long siguiente = (long) tamano.siguiente() + tamano.valor();
            if (siguiente > limiteDisponible) return false;
            posicion = (int) siguiente;
        }
        return false;
    }

    private EbmlValor leerIdEbml(byte[] datos, int posicion) {
        if (posicion < 0 || posicion >= datos.length) return null;
        int primero = unsigned(datos[posicion]);
        int longitud = longitudVint(primero, 4);
        if (longitud == 0 || posicion + longitud > datos.length) return null;
        long valor = primero;
        for (int indice = 1; indice < longitud; indice++) {
            valor = valor << 8 | unsigned(datos[posicion + indice]);
        }
        return new EbmlValor(valor, posicion + longitud, false);
    }

    private EbmlValor leerTamanoEbml(byte[] datos, int posicion) {
        if (posicion < 0 || posicion >= datos.length) return null;
        int primero = unsigned(datos[posicion]);
        int longitud = longitudVint(primero, 8);
        if (longitud == 0 || posicion + longitud > datos.length) return null;
        long mascara = 0x80L >>> (longitud - 1);
        long valor = primero & (mascara - 1);
        for (int indice = 1; indice < longitud; indice++) {
            valor = valor << 8 | unsigned(datos[posicion + indice]);
        }
        long valorDesconocido = (1L << (longitud * 7)) - 1;
        return new EbmlValor(valor, posicion + longitud, valor == valorDesconocido);
    }

    private int longitudVint(int primerByte, int maximo) {
        for (int longitud = 1; longitud <= maximo; longitud++) {
            if ((primerByte & 0x80 >>> (longitud - 1)) != 0) return longitud;
        }
        return 0;
    }

    private Path crearDirectorioSeguro(String nombre) throws IOException {
        Path directorio = raiz.resolve(nombre).normalize();
        if (!directorio.startsWith(raiz)) throw new MediaStorageException("Ruta de almacenamiento inválida.");
        return Files.createDirectories(directorio);
    }

    private Path resolverSeguro(Path directorio, String nombre) {
        Path destino = directorio.resolve(nombre).normalize();
        if (!destino.startsWith(raiz)) throw new MediaStorageException("Ruta de archivo inválida.");
        return destino;
    }

    private String rutaPublica(Path archivo) {
        return "/media/" + raiz.relativize(archivo).toString().replace('\\', '/');
    }

    private void borrarRutaPublica(String rutaPublica) {
        if (rutaPublica == null || !rutaPublica.startsWith("/media/")) return;
        Path archivo = raiz.resolve(rutaPublica.substring("/media/".length())).normalize();
        if (!archivo.startsWith(raiz)) return;
        borrarSilenciosamente(archivo);
    }

    private void moverAtomico(Path origen, Path destino) throws IOException {
        try {
            Files.move(origen, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validarArchivoPresente(MultipartFile archivo, String tipo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new MediaStorageException("El archivo de " + tipo + " está vacío.");
        }
    }

    private String nombreSeguro(String original) {
        if (original == null || original.isBlank()) return "archivo";
        String normalizado = original.replace('\\', '/');
        String nombre = normalizado.substring(normalizado.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (nombre.isBlank()) return "archivo";
        return nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre;
    }

    private void borrarSilenciosamente(Path archivo) {
        if (archivo == null) return;
        try {
            if (archivo.normalize().startsWith(raiz)) Files.deleteIfExists(archivo);
        } catch (IOException ex) {
            LOG.warn("No se pudo eliminar el archivo multimedia residual {}", archivo, ex);
        }
    }

    private record FormatoVideo(String extension, String contentType) { }
    private record EbmlValor(long valor, int siguiente, boolean desconocido) { }
}
