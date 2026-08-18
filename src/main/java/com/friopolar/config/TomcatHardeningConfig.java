package com.friopolar.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatHardeningConfig {
    @Bean
    TomcatProtocolHandlerCustomizer<AbstractHttp11Protocol<?>> continueResponseOnRead() {
        // Permite que los filtros inspeccionen metodo, ruta y Content-Type
        // antes de que Tomcat invite al cliente a enviar un cuerpo grande.
        return protocol -> protocol.setContinueResponseTiming("onRead");
    }
}
