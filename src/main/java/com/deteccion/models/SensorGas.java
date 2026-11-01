package com.deteccion.models;

/**
 * Sensor de gas analógico (por ejemplo MQ-2). Arduino entrega un valor ADC de 0 a 1023,
 * no ppm exactos. Los umbrales deben ajustarse calibrando el sensor real.
 */
public class SensorGas extends Sensor {

    public static final double ESCALA_MAXIMA = 1023.0;
    public static final double LIMITE_ADVERTENCIA = 350;
    public static final double LIMITE_CRITICO = 600;

    public SensorGas() {
        super("Gas");
    }

    @Override
    public Nivel evaluar(double valor) {
        if (valor >= LIMITE_CRITICO) return Nivel.CRITICA;
        if (valor >= LIMITE_ADVERTENCIA) return Nivel.ADVERTENCIA;
        return Nivel.NORMAL;
    }

    /** Valor como porcentaje de la escala, usado en la gráfica principal. */
    public double getPorcentaje() {
        return Math.min(100.0, getValor() / ESCALA_MAXIMA * 100.0);
    }

    @Override
    public String getValorTexto() {
        return Math.round(getValor()) + " ADC";
    }

    @Override
    public String getDetalle() {
        return "Rango seguro: 0 – " + (int) LIMITE_ADVERTENCIA + " (ADC)";
    }

    @Override
    public String getDescripcion(Nivel nivel) {
        String base = nivel == Nivel.CRITICA ? "Gas peligroso" : "Gas elevado";
        return base + " (" + getValorTexto() + ")";
    }
}
