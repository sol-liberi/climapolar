package com.friopolar.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPropertiesValidationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void impideIniciarConPasswordAdministrativaCorta() {
        contextRunner.withPropertyValues(
                        "climapolar.admin.username=admin",
                        "climapolar.admin.password=corta")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("ADMIN_PASSWORD debe tener al menos 12 caracteres");
                });
    }

    @Test
    void aceptaCredencialesAdministrativasValidas() {
        contextRunner.withPropertyValues(
                        "climapolar.admin.username=admin",
                        "climapolar.admin.password=clave-larga-de-prueba")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdminProperties.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminProperties.class)
    static class PropertiesConfiguration {
    }
}
