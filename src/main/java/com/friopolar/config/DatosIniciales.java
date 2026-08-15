package com.friopolar.config;

import com.friopolar.model.Trabajo;
import com.friopolar.repository.TrabajoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDate;

@Configuration
public class DatosIniciales {
    @Bean CommandLineRunner cargarDatos(TrabajoRepository repository) {
        return args -> {
            if (repository.count() > 0) return;
            crear(repository, "Instalación Split 4500 frigorías", "Instalación completa con terminaciones prolijas y prueba de funcionamiento.", "Instalación", "Villa Devoto", "https://images.unsplash.com/photo-1631545806609-8a83254468c6?auto=format&fit=crop&w=1200&q=80", true, 8);
            crear(repository, "Limpieza completa de equipo", "Limpieza profunda de filtros, evaporador y desagote para recuperar el rendimiento.", "Limpieza", "Villa del Parque", "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=1200&q=80", true, 19);
            crear(repository, "Instalación residencial", "Equipo instalado y nivelado, con cañería protegida y puesta en marcha.", "Instalación", "Palermo", "https://images.unsplash.com/photo-1590756254933-2873d72a83b6?auto=format&fit=crop&w=1200&q=80", true, 31);
            crear(repository, "Reparación de unidad exterior", "Diagnóstico y reparación de unidad exterior con verificación eléctrica.", "Reparación", "Belgrano", "https://images.unsplash.com/photo-1581094794329-c8112a89af12?auto=format&fit=crop&w=1200&q=80", false, 46);
            crear(repository, "Reubicación de aire acondicionado", "Desinstalación segura y reinstalación del equipo en su nueva ubicación.", "Reinstalación", "Caballito", "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1200&q=80", false, 62);
        };
    }
    private void crear(TrabajoRepository repository, String titulo, String descripcion, String tipo, String ubicacion, String imagen, boolean destacado, int dias) {
        Trabajo t = new Trabajo(); t.setTitulo(titulo); t.setDescripcion(descripcion); t.setTipoServicio(tipo); t.setUbicacion(ubicacion); t.setImagenUrl(imagen); t.setDestacado(destacado); t.setFecha(LocalDate.now().minusDays(dias)); repository.save(t);
    }
}
