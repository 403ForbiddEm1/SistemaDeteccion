package com.deteccion.services;

import com.deteccion.models.Incidencia;
import com.deteccion.models.Nivel;
import com.deteccion.models.Sensor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Evalúa los cambios de nivel de cada sensor, crea y resuelve incidencias
 * y controla el estado de la alarma sonora.
 * Todos sus métodos deben llamarse desde el hilo de JavaFX.
 */
public class GestorAlertas {


    private final ObservableList<Incidencia> historial = FXCollections.observableArrayList();
    private final ObservableList<Incidencia> activas = FXCollections.observableArrayList();
    private final Map<Sensor, Incidencia> abiertas = new HashMap<>();

    private final Consumer<String> registro;
    private final Consumer<Boolean> alarmaFisica;
    private boolean silenciada = false;

    public GestorAlertas(Consumer<String> registro, Consumer<Boolean> alarmaFisica) {
        this.registro = registro;
        this.alarmaFisica = alarmaFisica;
    }

    /** Se llama después de actualizar un sensor, con el nivel que tenía antes. */
    public void procesar(Sensor sensor, Nivel anterior) {
        Nivel actual = sensor.getNivel();
        if (actual == anterior) return;

        Incidencia previa = abiertas.remove(sensor);
        if (previa != null) {
            previa.resolver();
            activas.remove(previa);
            registro.accept(sensor.getTipo() + ": condición resuelta");
        }

        if (actual.esPeligro()) {
            Incidencia nueva = new Incidencia(sensor, actual);
            abiertas.put(sensor, nueva);
            historial.add(0, nueva);
            activas.add(nueva);
            registro.accept("ALERTA " + actual.getEtiqueta().toUpperCase() + ": " + nueva.getDescripcion());
            if (silenciada) { // una alerta nueva vuelve a activar la alarma
                silenciada = false;
                alarmaFisica.accept(false);
            }
        }

        if (activas.isEmpty() && silenciada) {
            silenciada = false;
            alarmaFisica.accept(false);
        }
    }

    public void alternarSilencio() {
        if (activas.isEmpty()) return;
        silenciada = !silenciada;
        alarmaFisica.accept(silenciada);
        registro.accept(silenciada ? "Alarma sonora silenciada" : "Alarma sonora reactivada");
    }

    /** Cierra todas las alertas abiertas (se usa al reiniciar el sistema). */
    public void limpiar() {
        abiertas.values().forEach(Incidencia::resolver);
        abiertas.clear();
        activas.clear();
        if (silenciada) {
            silenciada = false;
            alarmaFisica.accept(false);
        }
    }

    public boolean isSilenciada() { return silenciada; }
    public ObservableList<Incidencia> getHistorial() { return historial; }
    public ObservableList<Incidencia> getActivas() { return activas; }
}
