package com.friopolar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class MediaWebConfig implements WebMvcConfigurer {
    private final MediaStorageProperties properties;

    public MediaWebConfig(MediaStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = properties.getUploadDir().toAbsolutePath().normalize().toUri().toString();
        if (!ubicacion.endsWith("/")) ubicacion += "/";
        registry.addResourceHandler("/media/**")
                .addResourceLocations(ubicacion)
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
    }
}
