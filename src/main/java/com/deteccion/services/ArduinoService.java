package com.deteccion.services;

import com.fazecast.jSerialComm.SerialPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Comunicación con Arduino por puerto serial (jSerialComm) o modo simulación.
 *
 * Formato esperado de cada línea:  T:28.4;G:312;F:0
 *   T = temperatura en °C, G = lectura analógica del gas (0-1023), F = flama (1 detectada, 0 no).
 * Cualquier campo puede faltar: ese sensor queda "sin señal" en el dashboard.
 *
 * Los callbacks se ejecutan en hilos de fondo; la interfaz debe usar Platform.runLater.
 */
public class ArduinoService {

    /** Una lectura. Un valor null significa que ese sensor no envió datos. */
    public record Lectura(Double temperatura, Double gas, Boolean flama) { }

    public static final String SIMULACION = "Simulación";
    private static final int BAUDIOS = 9600;
    private static final long DURACION_FORZADO_MS = 12_000;

    private Consumer<Lectura> alLeer = l -> { };
    private Consumer<String> alRegistrar = m -> { };
    private Consumer<Boolean> alCambiarConexion = c -> { };

    private SerialPort puerto;
    private Thread lector;
    private ScheduledExecutorService simulador;
    private volatile boolean activo = false;
    private String nombrePuerto = "--";
    private boolean simulacion = false;

    // Estado del simulador
    private final Random azar = new Random();
    private double tempSimulada = 27.5;
    private volatile long calorHasta, gasHasta, flamaHasta;

    public void setOnLectura(Consumer<Lectura> c) { this.alLeer = c; }
    public void setOnRegistro(Consumer<String> c) { this.alRegistrar = c; }
    public void setOnConexion(Consumer<Boolean> c) { this.alCambiarConexion = c; }

    public String getNombrePuerto() { return nombrePuerto; }
    public int getBaudios() { return BAUDIOS; }
    public boolean isSimulacion() { return simulacion; }

    public static List<String> listarPuertos() {
        List<String> nombres = new ArrayList<>();
        for (SerialPort p : SerialPort.getCommPorts()) {
            nombres.add(p.getSystemPortName());
        }
        return nombres;
    }

    /** Conecta con un puerto real o inicia la simulación. */
    public void conectar(String nombre) {
        detener();
        nombrePuerto = nombre;
        simulacion = SIMULACION.equals(nombre);

        if (simulacion) {
            iniciarSimulacion();
            return;
        }

        SerialPort p = SerialPort.getCommPort(nombre);
        p.setComPortParameters(BAUDIOS, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        p.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
        if (!p.openPort()) {
            alRegistrar.accept("No se pudo abrir el puerto " + nombre);
            alCambiarConexion.accept(false);
            return;
        }
        puerto = p;
        activo = true;
        alCambiarConexion.accept(true);

        lector = new Thread(() -> leer(p), "lector-serial");
        lector.setDaemon(true);
        lector.start();
    }

    private void leer(SerialPort p) {
        try (Scanner sc = new Scanner(p.getInputStream())) {
            while (activo && sc.hasNextLine()) {
                procesarLinea(sc.nextLine().trim());
            }
        } catch (Exception e) {
            alRegistrar.accept("Error de lectura: " + e.getMessage());
        }
        if (activo) { // el puerto se cerró solo (cable desconectado)
            activo = false;
            alCambiarConexion.accept(false);
        }
    }

    private void procesarLinea(String linea) {
        if (linea.isEmpty()) return;
        Double t = null, g = null;
        Boolean f = null;
        for (String parte : linea.split(";")) {
            String[] kv = parte.split(":");
            if (kv.length != 2) continue;
            try {
                switch (kv[0].trim().toUpperCase()) {
                    case "T" -> t = Double.parseDouble(kv[1].trim());
                    case "G" -> g = Double.parseDouble(kv[1].trim());
                    case "F" -> f = kv[1].trim().equals("1");
                    default -> { }
                }
            } catch (NumberFormatException e) {
                // campo inválido: se ignora
            }
        }
        if (t == null && g == null && f == null) {
            alRegistrar.accept("Dato inválido recibido: " + linea);
            return;
        }
        alLeer.accept(new Lectura(t, g, f));
    }

    /** Envía 'M' (silenciar buzzer) o 'U' (reactivar) al Arduino. */
    public void silenciarAlarma(boolean silenciar) {
        if (puerto != null && puerto.isOpen()) {
            byte[] cmd = { (byte) (silenciar ? 'M' : 'U') };
            puerto.writeBytes(cmd, 1);
        }
    }

    // ---------------------------------------------------------------- simulación

    private void iniciarSimulacion() {
        activo = true;
        simulador = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulador");
            t.setDaemon(true);
            return t;
        });
        simulador.scheduleAtFixedRate(this::generarLectura, 0, 1, TimeUnit.SECONDS);
        alCambiarConexion.accept(true);
    }

    private void generarLectura() {
        long ahora = System.currentTimeMillis();
        double objetivo = ahora < calorHasta ? 62.0 : 28.0;
        tempSimulada += (objetivo - tempSimulada) * 0.25 + azar.nextGaussian() * 0.15;
        double gas = ahora < gasHasta ? 650 + azar.nextInt(60) : 110 + azar.nextInt(40);
        boolean flama = ahora < flamaHasta;
        alLeer.accept(new Lectura(Math.round(tempSimulada * 10) / 10.0, Math.floor(gas), flama));
    }

    /** Fuerza una condición de riesgo simulada ("flama", "gas" o "calor") por unos segundos. */
    public void forzar(String tipo) {
        long hasta = System.currentTimeMillis() + DURACION_FORZADO_MS;
        switch (tipo) {
            case "flama" -> flamaHasta = hasta;
            case "gas" -> gasHasta = hasta;
            case "calor" -> calorHasta = hasta;
            default -> { }
        }
    }

    public void detener() {
        activo = false;
        if (simulador != null) {
            simulador.shutdownNow();
            simulador = null;
        }
        if (puerto != null) {
            puerto.closePort();
            puerto = null;
        }
    }
}
