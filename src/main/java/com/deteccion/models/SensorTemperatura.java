package com.deteccion.models;

import java.util.Locale;

public class SensorTemperatura extends Sensor {

    public static final double LIMITE_ADVERTENCIA = 40.0; // °C
    public static final double LIMITE_CRITICO = 55.0;     // °C

    public SensorTemperatura() {
        super("Temperatura");
    }

    @Override
    public Nivel evaluar(double valor) {
        if (valor >= LIMITE_CRITICO) return Nivel.CRITICA;
        if (valor >= LIMITE_ADVERTENCIA) return Nivel.ADVERTENCIA;
        return Nivel.NORMAL;
    }

    @Override
    public String getValorTexto() {
        return String.format(Locale.US, "%.1f °C", getValor());
    }

    @Override
    public String getDetalle() {
        return "Rango seguro: 0 – " + (int) LIMITE_ADVERTENCIA + " °C";
    }

    @Override
    public String getDescripcion(Nivel nivel) {
        String base = nivel == Nivel.CRITICA ? "Temperatura alta" : "Temperatura elevada";
        return base + " (" + getValorTexto() + ")";
    }
}
