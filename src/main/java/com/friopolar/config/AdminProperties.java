package com.friopolar.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "climapolar.admin")
public record AdminProperties(
    @NotBlank(message = "Definí ADMIN_USERNAME para habilitar el panel")
    String username,

    @NotBlank(message = "Definí ADMIN_PASSWORD para habilitar el panel")
    @Size(min = 12, message = "ADMIN_PASSWORD debe tener al menos 12 caracteres")
    String password) {
}
