package com.friopolar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class MediaWebConfig implements WebMvcConfigurer {
    private final MediaStorageProperties properties;

    public MediaWebConfig(MediaStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path raiz = properties.getUploadDir().toAbsolutePath().normalize();
        registrarDirectorio(registry, "/media/images/**", raiz.resolve("images"));
        registrarDirectorio(registry, "/media/videos/**", raiz.resolve("videos"));
    }

    private void registrarDirectorio(ResourceHandlerRegistry registry, String patron, Path directorio) {
        String ubicacion = directorio.normalize().toUri().toString();
        if (!ubicacion.endsWith("/")) ubicacion += "/";
        registry.addResourceHandler(patron)
                .addResourceLocations(ubicacion)
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
    }
}
