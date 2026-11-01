package com.deteccion.models;

/** Nivel de riesgo de una lectura. */
public enum Nivel {
    NORMAL("Normal"),
    ADVERTENCIA("Advertencia"),
    CRITICA("Crítica");

    private final String etiqueta;

    Nivel(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean esPeligro() {
        return this != NORMAL;
    }
}
