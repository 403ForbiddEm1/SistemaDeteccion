package com.deteccion.models;

/** Sensor de flama digital: 1 = flama detectada, 0 = sin flama. */
public class SensorFlama extends Sensor {

    public SensorFlama() {
        super("Flama");
    }

    @Override
    public Nivel evaluar(double valor) {
        return valor >= 1 ? Nivel.CRITICA : Nivel.NORMAL;
    }

    @Override
    public String getValorTexto() {
        return getValor() >= 1 ? "Detectada" : "No detectada";
    }

    @Override
    public String getDetalle() {
        return "Estado: " + (getValor() >= 1 ? "Flama activa" : "Sin flama");
    }

    @Override
    public String getDescripcion(Nivel nivel) {
        return "Flama detectada";
    }
}
