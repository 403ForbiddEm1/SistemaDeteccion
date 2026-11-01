package com.deteccion.models;

import java.time.LocalDateTime;

/** Evento registrado cuando un sensor pasa a un nivel de riesgo. */
public class Incidencia {

    private final LocalDateTime fecha = LocalDateTime.now();
    private final String sensor;       // "Temperatura", "Gas" o "Flama"
    private final String descripcion;  // texto de la alerta
    private final String valor;        // valor al momento de la alerta
    private final Nivel nivel;
    private boolean resuelta = false;

    public Incidencia(Sensor sensor, Nivel nivel) {
        this.sensor = sensor.getTipo();
        this.descripcion = sensor.getDescripcion(nivel);
        this.valor = sensor.getValorTexto();
        this.nivel = nivel;
    }

    public LocalDateTime getFecha() { return fecha; }
    public String getSensor() { return sensor; }
    public String getDescripcion() { return descripcion; }
    public String getValor() { return valor; }
    public Nivel getNivel() { return nivel; }
    public boolean isResuelta() { return resuelta; }
    public void resolver() { this.resuelta = true; }
}
