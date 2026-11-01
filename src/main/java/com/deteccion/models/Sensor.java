package com.deteccion.models;

/**
 * Clase base de todos los sensores (herencia y polimorfismo).
 * Cada sensor decide cómo evaluar su valor y cómo mostrarlo.
 */
public abstract class Sensor {

    /** Si pasan más de estos milisegundos sin datos, el sensor se considera sin señal. */
    private static final long TIEMPO_SIN_SENAL_MS = 5000;

    private final String tipo;
    private double valor = Double.NaN;
    private Nivel nivel = Nivel.NORMAL;
    private long ultimaLectura = 0;

    protected Sensor(String tipo) {
        this.tipo = tipo;
    }

    /** Devuelve el nivel de riesgo para un valor dado. */
    public abstract Nivel evaluar(double valor);

    /** Valor listo para mostrar, por ejemplo "28.4 °C". */
    public abstract String getValorTexto();

    /** Texto secundario de la tarjeta, por ejemplo "Rango seguro: 0 – 40 °C". */
    public abstract String getDetalle();

    /** Descripción de la alerta para un nivel, por ejemplo "Temperatura alta (55.1 °C)". */
    public abstract String getDescripcion(Nivel nivel);

    public void actualizar(double nuevoValor) {
        this.valor = nuevoValor;
        this.nivel = evaluar(nuevoValor);
        this.ultimaLectura = System.currentTimeMillis();
    }

    public void reiniciar() {
        valor = Double.NaN;
        nivel = Nivel.NORMAL;
        ultimaLectura = 0;
    }

    public boolean estaConectado() {
        return ultimaLectura > 0 && System.currentTimeMillis() - ultimaLectura < TIEMPO_SIN_SENAL_MS;
    }

    public String getTipo() {
        return tipo;
    }

    public double getValor() {
        return valor;
    }

    public Nivel getNivel() {
        return nivel;
    }
}
