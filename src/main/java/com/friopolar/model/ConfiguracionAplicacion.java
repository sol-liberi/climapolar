package com.friopolar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "configuracion_aplicacion")
public class ConfiguracionAplicacion {
    @Id
    @Column(length = 100)
    private String clave;

    @Column(nullable = false, length = 255)
    private String valor;

    protected ConfiguracionAplicacion() { }

    public ConfiguracionAplicacion(String clave, String valor) {
        this.clave = clave;
        this.valor = valor;
    }

    public String getClave() { return clave; }
    public String getValor() { return valor; }
}
