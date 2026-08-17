package com.friopolar.config;

import com.friopolar.controller.HomeController;
import com.friopolar.controller.AdminAuthController;
import com.friopolar.controller.AdminTrabajoController;
import com.friopolar.controller.TrabajoController;
import com.friopolar.dto.CategoriaDto;
import com.friopolar.dto.MedioDto;
import com.friopolar.dto.TrabajoPaginaDto;
import com.friopolar.dto.TrabajoResumenDto;
import com.friopolar.model.Trabajo;
import com.friopolar.service.TrabajoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HomeController.class, AdminAuthController.class, TrabajoController.class, AdminTrabajoController.class})
@Import({SecurityConfig.class, MediaWebConfig.class})
@TestPropertySource(properties = {
        "climapolar.admin.username=admin-test",
        "climapolar.admin.password=clave-segura-de-prueba",
        "climapolar.media.upload-dir=./target/test-media",
        "climapolar.security.trusted-proxies=127.0.0.1,::1"
})
class SecurityConfigTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    LoginAttemptLimiter loginAttemptLimiter;

    @MockitoBean
    TrabajoService trabajoService;

    @BeforeEach
    void reiniciarLimiteDeAcceso() {
        loginAttemptLimiter.clearAll();
        when(trabajoService.listarPagina(any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new TrabajoPaginaDto(List.of(), 0, 0, 0));
        when(trabajoService.listarTodos()).thenReturn(List.of());
    }

    @Test
    void permiteElSitioPublicoYProtegeElPanel() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "es-AR"));

        mvc.perform(get("/admin/trabajos"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/login"));
    }

    @Test
    void permiteCacheCortaDeEstaticosSinAplicarlaAlHtml() throws Exception {
        mvc.perform(get("/css/styles.css?v=20260817"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void publicaSoloLosSubdirectoriosDeImagenesYVideos() throws Exception {
        Path raiz = Path.of("target/test-media").toAbsolutePath().normalize();
        Path archivoRaiz = raiz.resolve("no-publicar.txt");
        Path imagen = raiz.resolve("images/prueba-publica.txt");
        Path video = raiz.resolve("videos/prueba-publica.txt");
        Files.createDirectories(imagen.getParent());
        Files.createDirectories(video.getParent());
        Files.writeString(archivoRaiz, "privado");
        Files.writeString(imagen, "imagen");
        Files.writeString(video, "video");

        try {
            mvc.perform(get("/media/no-publicar.txt"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/media/images/prueba-publica.txt"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("imagen"));
            mvc.perform(get("/media/videos/prueba-publica.txt"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("video"));
        } finally {
            Files.deleteIfExists(archivoRaiz);
            Files.deleteIfExists(imagen);
            Files.deleteIfExists(video);
        }
    }

    @Test
    void rechazaCambiosAdministrativosSinCsrf() throws Exception {
        mvc.perform(post("/admin/trabajos/eliminar/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void autenticaAlAdministradorYRenuevaLaSesion() throws Exception {
        var sesionAnterior = new MockHttpSession(null, "session-conocida");

        var resultado = mvc.perform(post("/admin/login")
                        .session(sesionAnterior)
                        .with(csrf())
                        .param("username", "admin-test")
                        .param("password", "clave-segura-de-prueba"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/trabajos"))
                .andExpect(authenticated().withUsername("admin-test"))
                .andReturn();

        assertNotEquals("session-conocida", resultado.getRequest().getSession(false).getId());
    }

    @Test
    void limitaIntentosFallidosYDevuelveRetryAfter() throws Exception {
        String clientAddress = "198.51.100.41";
        for (int attempt = 0; attempt < LoginAttemptLimiter.COMBINATION_LIMIT; attempt++) {
            mvc.perform(post("/admin/login")
                            .with(csrf())
                            .with(remoteAddress(clientAddress))
                            .param("username", "admin-test")
                            .param("password", "incorrecta"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/admin/login?error"));
        }

        mvc.perform(post("/admin/login")
                        .with(csrf())
                        .with(remoteAddress(clientAddress))
                        .param("username", "admin-test")
                        .param("password", "incorrecta"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unAccesoCorrectoLimpiaLosIntentosPrevios() throws Exception {
        String clientAddress = "198.51.100.42";
        for (int attempt = 0; attempt < LoginAttemptLimiter.COMBINATION_LIMIT - 1; attempt++) {
            intentoFallido(clientAddress);
        }

        mvc.perform(post("/admin/login")
                        .with(csrf())
                        .with(remoteAddress(clientAddress))
                        .param("username", "admin-test")
                        .param("password", "clave-segura-de-prueba"))
                .andExpect(status().isFound())
                .andExpect(authenticated());

        // Si el contador anterior siguiera vigente, el segundo intento ya
        // quedaría bloqueado. Cinco fallos nuevos todavía se procesan.
        for (int attempt = 0; attempt < LoginAttemptLimiter.COMBINATION_LIMIT; attempt++) {
            intentoFallido(clientAddress);
        }
    }

    @Test
    void noConfiaEnForwardedForDeUnClienteDirecto() throws Exception {
        String directAddress = "198.51.100.43";
        for (int attempt = 0; attempt < LoginAttemptLimiter.COMBINATION_LIMIT; attempt++) {
            mvc.perform(post("/admin/login")
                            .with(csrf())
                            .with(remoteAddress(directAddress))
                            .header("X-Forwarded-For", "203.0.113." + (attempt + 1))
                            .param("username", "admin-test")
                            .param("password", "incorrecta"))
                    .andExpect(status().isFound());
        }

        mvc.perform(post("/admin/login")
                        .with(csrf())
                        .with(remoteAddress(directAddress))
                        .header("X-Forwarded-For", "203.0.113.99")
                        .param("username", "admin-test")
                        .param("password", "incorrecta"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void agregaHeadersDeSeguridadEHstsSoloConHttps() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", allOf(
                        containsString("script-src 'self'"),
                        containsString("style-src 'self'"),
                        containsString("frame-ancestors 'none'"))))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), geolocation=(), microphone=(), payment=(), usb=()"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mvc.perform(get("/").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", allOf(
                        containsString("max-age=31536000"),
                        containsString("includeSubDomains"),
                        containsString("preload"))));
    }

    @Test
    void devuelve404PublicoParaRutasDesconocidasPeroProtegeAdmin() throws Exception {
        mvc.perform(get("/ruta-publica-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));

        mvc.perform(head("/otra-ruta-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));

        mvc.perform(get("/admin/ruta-inexistente"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/login"));
    }

    @Test
    void noPermiteEscriturasEnApiOMediosNiSiquieraAlAdmin() throws Exception {
        mvc.perform(post("/api/trabajos")
                        .with(user("admin-test").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(put("/media/videos/archivo.mp4")
                        .with(user("admin-test").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void muestraAccesosAdministrativosSoloAlAdministradorAutenticado() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-admin-session-link"))));

        mvc.perform(get("/").with(user("admin-test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-admin-session-link")))
                .andExpect(content().string(containsString("+ Nuevo trabajo")));
    }

    @Test
    void mantieneElHomePublicoConTrabajosRenderizadosEnElServidor() throws Exception {
        var portada = new MedioDto(10L, "imagen", "/media/images/trabajo.jpg",
                "/media/images/trabajo-thumb.jpg", "image/jpeg");
        var trabajo = new TrabajoResumenDto(7L, "Trabajo renderizado", "Descripción breve",
                LocalDate.of(2026, 8, 17), new CategoriaDto("conductos", "Conductos"), portada, 1, 0);
        when(trabajoService.listarPagina(any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new TrabajoPaginaDto(List.of(trabajo), 0, 1, 1));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trabajo renderizado")))
                .andExpect(content().string(containsString("/media/images/trabajo-thumb.jpg")))
                .andExpect(content().string(containsString("/trabajos/7")));
    }

    @Test
    void renderizaLasVistasPublicasDePortfolioYDetalle() throws Exception {
        var trabajo = trabajoDePrueba();
        when(trabajoService.buscarPorId(7L)).thenReturn(trabajo);

        mvc.perform(get("/trabajos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nuestros trabajos")));

        mvc.perform(get("/trabajos/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trabajo renderizado")))
                .andExpect(content().string(containsString("Este trabajo todavía no tiene archivos publicados")));
    }

    @Test
    void renderizaLoginYVistasAdministrativasSoloConRolAdmin() throws Exception {
        mvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ACCESO PRIVADO")));

        mvc.perform(get("/admin/trabajos").with(user("admin-test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PANEL ADMINISTRATIVO")));

        mvc.perform(get("/admin/trabajos/nuevo").with(user("admin-test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nuevo trabajo")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    private void intentoFallido(String clientAddress) throws Exception {
        mvc.perform(post("/admin/login")
                        .with(csrf())
                        .with(remoteAddress(clientAddress))
                        .param("username", "admin-test")
                        .param("password", "incorrecta"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    private RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private Trabajo trabajoDePrueba() {
        var trabajo = new Trabajo();
        trabajo.setId(7L);
        trabajo.setTitulo("Trabajo renderizado");
        trabajo.setDescripcion("Descripción breve");
        trabajo.setTipoServicio("conductos");
        trabajo.setUbicacion("Buenos Aires");
        trabajo.setFecha(LocalDate.of(2026, 8, 17));
        return trabajo;
    }
}
