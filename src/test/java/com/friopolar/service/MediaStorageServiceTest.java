package com.friopolar.service;

import com.friopolar.config.MediaStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageServiceTest {
    @TempDir
    Path temporal;

    private MediaStorageService storage;

    @BeforeEach
    void preparar() {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setUploadDir(temporal);
        storage = new MediaStorageService(properties);
        storage.inicializar();
    }

    @Test
    void optimizaImagenYGeneraMiniaturaConNombreSeguro() throws Exception {
        BufferedImage original = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = original.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillRect(0, 0, original.getWidth(), original.getHeight());
        graphics.dispose();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(original, "png", bytes);
        var archivo = new MockMultipartFile("imagenes", "foto de obra.png", "image/png", bytes.toByteArray());

        var medio = storage.guardarImagen(archivo, 0);
        Path imagen = rutaFisica(medio.getRutaPublica());
        Path miniatura = rutaFisica(medio.getRutaMiniatura());

        assertThat(medio.isGestionado()).isTrue();
        assertThat(medio.getContentType()).isEqualTo("image/jpeg");
        assertThat(Files.exists(imagen)).isTrue();
        assertThat(Files.exists(miniatura)).isTrue();
        assertThat(ImageIO.read(imagen.toFile()).getWidth()).isEqualTo(1920);
        assertThat(ImageIO.read(miniatura.toFile()).getWidth()).isEqualTo(900);

        storage.eliminar(medio);
        assertThat(Files.exists(imagen)).isFalse();
        assertThat(Files.exists(miniatura)).isFalse();
    }

    @Test
    void rechazaUnArchivoQueNoEsUnVideoReal() {
        var falsoVideo = new MockMultipartFile("videos", "archivo.mp4", "video/mp4", "contenido falso".getBytes());

        assertThatThrownBy(() -> storage.guardarVideo(falsoVideo, 0))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("MP4 o WebM");
    }

    @Test
    void aceptaMp4ConEstructuraBasicaCompleta() {
        var archivo = new MockMultipartFile("videos", "obra.mp4", "video/mp4", mp4Valido());

        var medio = storage.guardarVideo(archivo, 0);

        assertThat(medio.getContentType()).isEqualTo("video/mp4");
        assertThat(medio.getRutaPublica()).endsWith(".mp4");
        assertThat(Files.exists(rutaFisica(medio.getRutaPublica()))).isTrue();
        storage.eliminar(medio);
    }

    @Test
    void rechazaUnArchivoQueSoloFalsificaLaCabeceraFtyp() {
        byte[] falso = cajaMp4("ftyp", ByteBuffer.allocate(8)
                .put("isom".getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .array());
        var archivo = new MockMultipartFile("videos", "falso.mp4", "video/mp4", falso);

        assertThatThrownBy(() -> storage.guardarVideo(archivo, 0))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("MP4 o WebM");
    }

    @Test
    void aceptaWebmConDocTypePistasYCluster() {
        var archivo = new MockMultipartFile("videos", "obra.webm", "video/webm", webmValido());

        var medio = storage.guardarVideo(archivo, 0);

        assertThat(medio.getContentType()).isEqualTo("video/webm");
        assertThat(medio.getRutaPublica()).endsWith(".webm");
        storage.eliminar(medio);
    }

    @Test
    void rechazaWebmConClusterTruncado() {
        var archivo = new MockMultipartFile(
                "videos",
                "truncado.webm",
                "video/webm",
                webmConClusterTruncado()
        );

        assertThatThrownBy(() -> storage.guardarVideo(archivo, 0))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("MP4 o WebM");
    }

    @Test
    void reconciliaSoloArchivosGestionadosHuerfanosYAntiguos() throws Exception {
        Path imagenes = temporal.resolve("images");
        Path huerfana = imagenes.resolve("00000000-0000-0000-0000-000000000001.jpg");
        Path referenciada = imagenes.resolve("00000000-0000-0000-0000-000000000002.jpg");
        Path reciente = imagenes.resolve("00000000-0000-0000-0000-000000000003.jpg");
        Path ajena = imagenes.resolve("archivo-manual.jpg");
        Files.write(huerfana, new byte[]{1});
        Files.write(referenciada, new byte[]{1});
        Files.write(reciente, new byte[]{1});
        Files.write(ajena, new byte[]{1});
        FileTime antigua = FileTime.from(Instant.now().minus(Duration.ofHours(25)));
        Files.setLastModifiedTime(huerfana, antigua);
        Files.setLastModifiedTime(referenciada, antigua);
        Files.setLastModifiedTime(ajena, antigua);

        int eliminados = storage.reconciliarHuerfanos(
                Set.of("/media/images/" + referenciada.getFileName()),
                Duration.ofHours(24)
        );

        assertThat(eliminados).isEqualTo(1);
        assertThat(huerfana).doesNotExist();
        assertThat(referenciada).exists();
        assertThat(reciente).exists();
        assertThat(ajena).exists();
    }

    @ParameterizedTest(name = "corrige orientación EXIF {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void corrigeTodasLasOrientacionesExifDeFotosJpeg(int orientacion) throws Exception {
        BufferedImage original = imagenDeCuadrantes();
        byte[] jpeg = jpegConOrientacionExif(original, orientacion);
        var archivo = new MockMultipartFile("imagenes", "foto-celular.jpg", "image/jpeg", jpeg);

        var medio = storage.guardarImagen(archivo, 0);
        BufferedImage procesada = ImageIO.read(rutaFisica(medio.getRutaPublica()).toFile());

        boolean intercambiaLados = orientacion >= 5;
        assertThat(procesada.getWidth()).isEqualTo(intercambiaLados ? original.getHeight() : original.getWidth());
        assertThat(procesada.getHeight()).isEqualTo(intercambiaLados ? original.getWidth() : original.getHeight());

        Color[] esperados = coloresEsperados(orientacion);
        int cuartoAncho = procesada.getWidth() / 4;
        int cuartoAlto = procesada.getHeight() / 4;
        assertColorCercano(procesada, cuartoAncho, cuartoAlto, esperados[0]);
        assertColorCercano(procesada, cuartoAncho * 3, cuartoAlto, esperados[1]);
        assertColorCercano(procesada, cuartoAncho, cuartoAlto * 3, esperados[2]);
        assertColorCercano(procesada, cuartoAncho * 3, cuartoAlto * 3, esperados[3]);

        storage.eliminar(medio);
    }

    private BufferedImage imagenDeCuadrantes() {
        BufferedImage imagen = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = imagen.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 60, 40);
            graphics.setColor(Color.GREEN);
            graphics.fillRect(60, 0, 60, 40);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 40, 60, 40);
            graphics.setColor(Color.YELLOW);
            graphics.fillRect(60, 40, 60, 40);
        } finally {
            graphics.dispose();
        }
        return imagen;
    }

    private byte[] jpegConOrientacionExif(BufferedImage imagen, int orientacion) throws Exception {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpeg", jpeg);
        byte[] original = jpeg.toByteArray();
        byte[] exif = new byte[]{
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0,
                0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientacion, 0, 0, 0,
                0, 0, 0, 0
        };
        int longitudSegmento = exif.length + 2;

        ByteArrayOutputStream resultado = new ByteArrayOutputStream(original.length + exif.length + 4);
        resultado.write(original, 0, 2);
        resultado.write(0xff);
        resultado.write(0xe1);
        resultado.write(longitudSegmento >>> 8);
        resultado.write(longitudSegmento);
        resultado.write(exif);
        resultado.write(original, 2, original.length - 2);
        return resultado.toByteArray();
    }

    private Color[] coloresEsperados(int orientacion) {
        return switch (orientacion) {
            case 2 -> new Color[]{Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE};
            case 3 -> new Color[]{Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED};
            case 4 -> new Color[]{Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN};
            case 5 -> new Color[]{Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW};
            case 6 -> new Color[]{Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN};
            case 7 -> new Color[]{Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED};
            case 8 -> new Color[]{Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE};
            default -> new Color[]{Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
        };
    }

    private byte[] mp4Valido() {
        byte[] ftyp = ByteBuffer.allocate(16)
                .put("isom".getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put("isom".getBytes(StandardCharsets.US_ASCII))
                .put("mp42".getBytes(StandardCharsets.US_ASCII))
                .array();
        ByteArrayOutputStream video = new ByteArrayOutputStream();
        video.writeBytes(cajaMp4("ftyp", ftyp));
        video.writeBytes(cajaMp4("moov", new byte[8]));
        video.writeBytes(cajaMp4("mdat", new byte[]{1, 2, 3, 4}));
        return video.toByteArray();
    }

    private byte[] cajaMp4(String tipo, byte[] contenido) {
        return ByteBuffer.allocate(8 + contenido.length)
                .putInt(8 + contenido.length)
                .put(tipo.getBytes(StandardCharsets.ISO_8859_1))
                .put(contenido)
                .array();
    }

    private byte[] webmValido() {
        return new byte[]{
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x87,
                0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm',
                0x18, 0x53, (byte) 0x80, 0x67, (byte) 0x8C,
                0x16, 0x54, (byte) 0xAE, 0x6B, (byte) 0x81, 0x01,
                0x1F, 0x43, (byte) 0xB6, 0x75, (byte) 0x81, 0x01
        };
    }

    private byte[] webmConClusterTruncado() {
        return new byte[]{
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x87,
                0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm',
                0x18, 0x53, (byte) 0x80, 0x67, (byte) 0xFF,
                0x16, 0x54, (byte) 0xAE, 0x6B, (byte) 0x81, 0x01,
                0x1F, 0x43, (byte) 0xB6, 0x75, (byte) 0x81
        };
    }

    private void assertColorCercano(BufferedImage imagen, int x, int y, Color esperado) {
        Color actual = new Color(imagen.getRGB(x, y));
        assertThat(Math.abs(actual.getRed() - esperado.getRed())).isLessThanOrEqualTo(60);
        assertThat(Math.abs(actual.getGreen() - esperado.getGreen())).isLessThanOrEqualTo(60);
        assertThat(Math.abs(actual.getBlue() - esperado.getBlue())).isLessThanOrEqualTo(60);
    }

    private Path rutaFisica(String rutaPublica) {
        return temporal.resolve(rutaPublica.substring("/media/".length())).normalize();
    }
}
