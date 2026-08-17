package com.friopolar.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "climapolar.admin")
public class AdminProperties {
    @NotBlank(message = "Definí ADMIN_USERNAME para habilitar el panel")
    private String username;

    @NotBlank(message = "Definí ADMIN_PASSWORD para habilitar el panel")
    @Size(min = 12, message = "ADMIN_PASSWORD debe tener al menos 12 caracteres")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
