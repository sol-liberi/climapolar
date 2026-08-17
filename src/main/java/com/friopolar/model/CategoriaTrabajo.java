package com.friopolar.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum CategoriaTrabajo {
    PREINSTALACIONES("preinstalaciones", "Preinstalaciones en obra", "Preinstalaciones"),
    SPLIT("split", "Instalación de equipos Split", "Split"),
    SISTEMAS("sistemas", "Sistemas por conductos", "Sistemas"),
    CONDUCTOS("conductos", "Conductos galvanizados y distribución de aire", "Conductos"),
    MONTAJES("montajes", "Montaje y puesta en marcha", "Montajes"),
    OTROS("otros", "Otros trabajos de climatización", "Otros");

    private final String codigo;
    private final String etiqueta;
    private final String etiquetaCorta;

    CategoriaTrabajo(String codigo, String etiqueta, String etiquetaCorta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.etiquetaCorta = etiquetaCorta;
    }

    public String getCodigo() { return codigo; }
    public String getEtiqueta() { return etiqueta; }
    public String getEtiquetaCorta() { return etiquetaCorta; }

    public static Optional<CategoriaTrabajo> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(categoria -> categoria.codigo.equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    public static CategoriaTrabajo desdeValorGuardado(String valor) {
        var porCodigo = buscarPorCodigo(valor);
        if (porCodigo.isPresent()) return porCodigo.get();

        String normalizado = normalizar(valor);
        if (normalizado.contains("preinstal")) return PREINSTALACIONES;
        if (normalizado.contains("split")) return SPLIT;
        if (normalizado.contains("galvaniz") || normalizado.startsWith("conductos y")) return CONDUCTOS;
        if (normalizado.contains("sistema") && normalizado.contains("conduct")) return SISTEMAS;
        if (normalizado.contains("climatizacion por conduct")) return SISTEMAS;
        if (normalizado.contains("montaje") || normalizado.contains("puesta en marcha")) return MONTAJES;
        return OTROS;
    }

    private static String normalizar(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
