package com.deteccion;

import com.deteccion.models.Incidencia;
import com.deteccion.models.Nivel;
import com.deteccion.models.Sensor;
import com.deteccion.models.SensorFlama;
import com.deteccion.models.SensorGas;
import com.deteccion.models.SensorTemperatura;
import com.deteccion.services.ArduinoService;
import com.deteccion.services.ArduinoService.Lectura;
import com.deteccion.services.GestorAlertas;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Construye (solo con código, sin FXML ni Scene Builder) y actualiza el dashboard.
 * Une las tres partes del sistema: sensores (models), Arduino y alertas (services).
 */
public class DashboardController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int VENTANA_SEGUNDOS = 300;      // la gráfica muestra los últimos 5 minutos
    private static final long LIMITE_SIN_DATOS_MS = 5000;
    private static final String CLASE_OSCURO = "oscuro";  // clase CSS que activa el tema oscuro

    // ---- Modelo y servicios
    private final SensorTemperatura temperatura = new SensorTemperatura();
    private final SensorGas gas = new SensorGas();
    private final SensorFlama flama = new SensorFlama();
    private final List<Sensor> sensores = List.of(temperatura, gas, flama);

    private final ObservableList<String> registro = FXCollections.observableArrayList();
    private final ArduinoService arduino = new ArduinoService();
    private final GestorAlertas gestor = new GestorAlertas(this::log, arduino::silenciarAlarma);

    // ---- Estado
    private boolean conectado = false;
    private boolean oscuro = false;                        // arranca en modo claro
    private long ultimaLecturaMs = 0;
    private int contadorLecturas = 0;

    // ---- Componentes de la interfaz
    private final BorderPane raiz = new BorderPane();
    private TarjetaSensor tarjetaTemp, tarjetaGas, tarjetaFlama;
    private XYChart.Series<Number, Number> serieTemp, serieGas;
    private NumberAxis ejeTiempo;

    private Circle puntoConexion;
    private Label lblConexion, lblPuertoCab, lblFecha, lblHora;
    private Button btnTema;

    private Label lblEstArduino, lblEstPuerto, lblEstVel, lblEstUltima, lblEstSensores, lblComunicacion, anilloTxt;
    private Circle anillo;

    private Label contadorAlertas;
    private VBox cajaAlertas, listaUltimas;
    private TableView<Incidencia> tabla;
    private FilteredList<Incidencia> filtradas;
    private Label lblRegistros;

    private Button btnSilenciar;
    private ComboBox<String> cmbPuertos;
    private HBox filaSimulacion;

    private Label pieSistema, pieArduino, pieSensores, pieComunicacion;
    private Timeline reloj;

    public DashboardController() {
        raiz.getStyleClass().add("dashboard");
        raiz.setTop(crearEncabezado());
        raiz.setCenter(crearCentro());
        raiz.setBottom(crearPie());

        configurarArduino();
        gestor.getActivas().addListener((ListChangeListener<Incidencia>) c -> refrescarAlertas());
        // Si el historial cambia sin que cambien las activas, "Últimas alertas" también debe actualizarse
        gestor.getHistorial().addListener((ListChangeListener<Incidencia>) c -> Platform.runLater(this::refrescarAlertas));
        refrescarAlertas();

        reloj = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        reloj.setCycleCount(Timeline.INDEFINITE);
        reloj.play();
        tick();

        conectar(ArduinoService.SIMULACION); // arranca en modo simulación
    }

    public Parent getRoot() {
        return raiz;
    }

    public void detener() {
        reloj.stop();
        arduino.detener();
    }

    // =====================================================================
    //  Construcción de la interfaz
    // =====================================================================

    private Node crearEncabezado() {
        Label escudo = new Label("🛡");
        escudo.getStyleClass().add("logo");
        Label titulo = new Label("Sistema de Detección Térmico-Gaseosa");
        titulo.getStyleClass().add("titulo-app");
        Label sub = new Label("Monitoreo en tiempo real  •  Seguridad y prevención");
        sub.getStyleClass().add("subtitulo-app");
        HBox izquierda = new HBox(14, escudo, new VBox(2, titulo, sub));
        izquierda.setAlignment(Pos.CENTER_LEFT);

        puntoConexion = new Circle(6);
        lblConexion = new Label();
        lblConexion.getStyleClass().add("conexion-titulo");
        lblPuertoCab = new Label();
        lblPuertoCab.getStyleClass().add("texto-suave");
        lblFecha = new Label();
        lblFecha.getStyleClass().add("fecha");
        lblHora = new Label();
        lblHora.getStyleClass().add("hora");

        HBox caja = new HBox(14, puntoConexion, new VBox(2, lblConexion, lblPuertoCab),
                new Separator(Orientation.VERTICAL), new VBox(2, lblFecha, lblHora));
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.getStyleClass().add("caja-conexion");

        // Botón para alternar entre modo claro y modo oscuro
        btnTema = new Button();
        btnTema.getStyleClass().addAll("btn", "btn-secundario");
        btnTema.setOnAction(e -> alternarTema());
        actualizarTextoTema();

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox encabezado = new HBox(14, izquierda, espacio, btnTema, caja);
        encabezado.setAlignment(Pos.CENTER_LEFT);
        encabezado.getStyleClass().add("encabezado");
        return encabezado;
    }

    private Node crearCentro() {
        HBox centro = new HBox(14, crearColumnaIzquierda(), crearColumnaDerecha());
        centro.getStyleClass().add("centro");
        return centro;
    }

    private Node crearColumnaIzquierda() {
        tarjetaTemp = new TarjetaSensor("🌡", "Temperatura", temperatura, false);
        tarjetaGas = new TarjetaSensor("☁", "Gas", gas, false);
        tarjetaFlama = new TarjetaSensor("🔥", "Flama", flama, true);

        HBox tarjetas = new HBox(14, tarjetaTemp.getNodo(), tarjetaGas.getNodo(), tarjetaFlama.getNodo());
        for (Node n : tarjetas.getChildren()) {
            ((Region) n).setPrefWidth(100);
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        HBox filaMedia = new HBox(14, crearPanelGrafica(), crearPanelEstado());
        HBox filaBaja = new HBox(14, crearPanelHistorial(), crearPanelRegistro());

        VBox izquierda = new VBox(14, tarjetas, filaMedia, filaBaja);
        VBox.setVgrow(filaMedia, Priority.ALWAYS);
        VBox.setVgrow(filaBaja, Priority.ALWAYS);
        filaMedia.setPrefHeight(290);
        filaBaja.setPrefHeight(250);
        HBox.setHgrow(izquierda, Priority.ALWAYS);
        return izquierda;
    }

    private Node crearColumnaDerecha() {
        VBox derecha = new VBox(14, crearPanelAlertas(), crearPanelControles());
        derecha.setPrefWidth(350);
        derecha.setMinWidth(350);
        derecha.setMaxWidth(350);
        return derecha;
    }

    // ---------------------------------------------------------------- gráfica

    private Node crearPanelGrafica() {
        ejeTiempo = new NumberAxis();
        ejeTiempo.setAutoRanging(false);
        ejeTiempo.setTickUnit(60);
        ejeTiempo.setMinorTickVisible(false);
        ejeTiempo.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number n) {
                return Instant.ofEpochSecond(n.longValue()).atZone(ZoneId.systemDefault())
                        .toLocalTime().format(HORA);
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });
        double ahora = System.currentTimeMillis() / 1000.0;
        ejeTiempo.setLowerBound(ahora - VENTANA_SEGUNDOS);
        ejeTiempo.setUpperBound(ahora);

        NumberAxis ejeY = new NumberAxis(0, 100, 20);
        ejeY.setAutoRanging(false);
        ejeY.setMinorTickVisible(false);

        serieTemp = new XYChart.Series<>();
        serieTemp.setName("Temperatura (°C)");
        serieGas = new XYChart.Series<>();
        serieGas.setName("Gas (% de escala)");

        LineChart<Number, Number> grafica = new LineChart<>(ejeTiempo, ejeY);
        grafica.getData().add(serieTemp);
        grafica.getData().add(serieGas);
        grafica.setCreateSymbols(false);
        grafica.setAnimated(false);
        grafica.setLegendSide(Side.TOP);
        grafica.getStyleClass().add("grafica-principal");
        VBox.setVgrow(grafica, Priority.ALWAYS);

        VBox panel = panel("📈", "Lecturas en tiempo real", grafica);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Node crearPanelEstado() {
        lblEstArduino = valorFila();
        lblEstPuerto = valorFila();
        lblEstVel = valorFila();
        lblEstUltima = valorFila();
        lblEstSensores = valorFila();

        GridPane filas = new GridPane();
        filas.setHgap(12);
        filas.setVgap(9);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(42);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(58);
        filas.getColumnConstraints().addAll(c1, c2);
        String[] nombres = {"Arduino", "Puerto", "Velocidad", "Última lectura", "Sensores"};
        Label[] valores = {lblEstArduino, lblEstPuerto, lblEstVel, lblEstUltima, lblEstSensores};
        for (int i = 0; i < nombres.length; i++) {
            Label n = new Label(nombres[i]);
            n.getStyleClass().add("texto-suave");
            filas.add(n, 0, i);
            filas.add(valores[i], 1, i);
        }

        anillo = new Circle(24);
        anillo.getStyleClass().add("anillo");
        anilloTxt = new Label("✔");
        anilloTxt.getStyleClass().add("anillo-texto");
        lblComunicacion = new Label();
        lblComunicacion.getStyleClass().add("fila-valor");
        HBox comunicacion = new HBox(14, new StackPane(anillo, anilloTxt), lblComunicacion);
        comunicacion.setAlignment(Pos.CENTER_LEFT);

        VBox panel = panel("🖥", "Estado del sistema", filas, comunicacion);
        panel.setPrefWidth(330);
        panel.setMinWidth(330);
        return panel;
    }

    private Label valorFila() {
        Label l = new Label("--");
        l.getStyleClass().add("fila-valor");
        return l;
    }

    // ---------------------------------------------------------------- historial y registro

    private Node crearPanelHistorial() {
        filtradas = new FilteredList<>(gestor.getHistorial(), i -> true);
        tabla = new TableView<>(filtradas);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabla.setPlaceholder(new Label("Sin incidencias registradas"));

        TableColumn<Incidencia, String> cFecha = columna("Fecha y hora", i -> FECHA_HORA.format(i.getFecha()));
        TableColumn<Incidencia, String> cSensor = columna("Sensor", Incidencia::getSensor);
        TableColumn<Incidencia, String> cValor = columna("Valor", Incidencia::getValor);
        TableColumn<Incidencia, String> cNivel = columna("Nivel", i -> i.getNivel().getEtiqueta());
        TableColumn<Incidencia, String> cEstado = columna("Estado", i -> i.isResuelta() ? "Resuelta" : "Activa");
        cNivel.setCellFactory(c -> celdaInsignia(true));
        cEstado.setCellFactory(c -> celdaInsignia(false));
        cFecha.setMinWidth(150);
        tabla.getColumns().addAll(List.of(cFecha, cSensor, cValor, cNivel, cEstado));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        lblRegistros = new Label();
        lblRegistros.getStyleClass().add("texto-suave");
        lblRegistros.textProperty().bind(Bindings.size(filtradas).asString("Mostrando %d registros"));

        VBox panel = panel("🕘", "Historial de incidencias", tabla, lblRegistros);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private TableColumn<Incidencia, String> columna(String titulo, Function<Incidencia, String> valor) {
        TableColumn<Incidencia, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    /** Celda que muestra el texto como una insignia de color (nivel o estado). */
    private TableCell<Incidencia, String> celdaInsignia(boolean esNivel) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                Incidencia inc = vacio ? null : getTableRow() == null ? null : getTableRow().getItem();
                if (inc == null || texto == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label b = new Label(texto);
                b.getStyleClass().add("badge");
                b.getStyleClass().add(esNivel ? claseNivel(inc.getNivel())
                        : inc.isResuelta() ? "badge-resuelta" : "badge-activa");
                setGraphic(b);
                setText(null);
            }
        };
    }

    private Node crearPanelRegistro() {
        ListView<String> lista = new ListView<>(registro);
        lista.getStyleClass().add("registro");
        lista.setPlaceholder(new Label("Sin mensajes"));
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox panel = panel("📄", "Registro del sistema", lista);
        panel.setPrefWidth(380);
        panel.setMinWidth(380);
        return panel;
    }

    // ---------------------------------------------------------------- columna derecha

    private Node crearPanelAlertas() {
        Label ic = new Label("🔔");
        ic.getStyleClass().add("panel-icono");
        Label t = new Label("Alertas Activas");
        t.getStyleClass().add("panel-titulo");
        contadorAlertas = new Label("0");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cabecera = new HBox(8, ic, t, espacio, contadorAlertas);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        cajaAlertas = new VBox(8);
        cajaAlertas.getStyleClass().add("caja-alertas");
        cajaAlertas.setMinHeight(130);

        Label sub = new Label("Últimas alertas");
        sub.getStyleClass().add("panel-subtitulo");
        listaUltimas = new VBox(8);

        VBox panel = new VBox(10, cabecera, cajaAlertas, sub, listaUltimas);
        panel.getStyleClass().add("panel");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Node crearPanelControles() {
        btnSilenciar = new Button("🔕  Silenciar alarmas");
        btnSilenciar.getStyleClass().addAll("btn", "btn-primario");
        btnSilenciar.setMaxWidth(Double.MAX_VALUE);
        btnSilenciar.disableProperty().bind(Bindings.isEmpty(gestor.getActivas()));
        btnSilenciar.setOnAction(e -> {
            gestor.alternarSilencio();
            refrescarAlertas();
        });

        Button btnReiniciar = new Button("↻  Reiniciar sistema");
        btnReiniciar.getStyleClass().addAll("btn", "btn-secundario");
        btnReiniciar.setMaxWidth(Double.MAX_VALUE);
        btnReiniciar.setOnAction(e -> reiniciar());

        cmbPuertos = new ComboBox<>();
        cmbPuertos.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cmbPuertos, Priority.ALWAYS);
        cmbPuertos.setOnShowing(e -> refrescarPuertos());
        refrescarPuertos();
        Button btnConectar = new Button("Conectar");
        btnConectar.getStyleClass().addAll("btn", "btn-secundario");
        btnConectar.setOnAction(e -> {
            if (cmbPuertos.getValue() != null) conectar(cmbPuertos.getValue());
        });
        HBox filaPuerto = new HBox(8, cmbPuertos, btnConectar);

        filaSimulacion = new HBox(6, botonSimulacion("Flama", "flama"),
                botonSimulacion("Gas", "gas"), botonSimulacion("Calor", "calor"));
        Label etiquetaSim = new Label("Probar en simulación:");
        etiquetaSim.getStyleClass().add("texto-suave");

        // Filtros del historial
        ToggleGroup grupo = new ToggleGroup();
        HBox chips = new HBox(8);
        for (String nombre : new String[]{"Todos", "Temperatura", "Gas", "Flama"}) {
            ToggleButton chip = new ToggleButton(nombre);
            chip.getStyleClass().add("chip");
            chip.setToggleGroup(grupo);
            chip.setUserData(nombre);
            chips.getChildren().add(chip);
        }
        ((ToggleButton) chips.getChildren().get(0)).setSelected(true);
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) { // siempre debe haber un filtro activo
                anterior.setSelected(true);
                return;
            }
            String tipo = (String) nuevo.getUserData();
            filtradas.setPredicate(i -> tipo.equals("Todos") || i.getSensor().equals(tipo));
        });
        Label tituloFiltros = new Label("Filtros rápidos del historial");
        tituloFiltros.getStyleClass().add("panel-subtitulo");

        VBox panel = panel("⚙", "Controles", btnSilenciar, btnReiniciar, filaPuerto,
                etiquetaSim, filaSimulacion, tituloFiltros, chips);
        return panel;
    }

    private Button botonSimulacion(String texto, String tipo) {
        Button b = new Button(texto);
        b.getStyleClass().addAll("btn", "btn-chico");
        b.setOnAction(e -> {
            arduino.forzar(tipo);
            log("Simulación: se fuerza " + tipo);
        });
        return b;
    }

    private Node crearPie() {
        pieSistema = new Label();
        pieArduino = new Label();
        pieSensores = new Label();
        pieComunicacion = new Label();
        Label fx = new Label("JavaFX " + System.getProperty("javafx.version", ""));
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(28, pieSistema, espacio, pieArduino, pieSensores, pieComunicacion, fx);
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.getStyleClass().add("pie");
        return pie;
    }

    /** Panel genérico: cabecera con icono y título, y el contenido debajo. */
    private VBox panel(String icono, String titulo, Node... contenido) {
        Label ic = new Label(icono);
        ic.getStyleClass().add("panel-icono");
        Label t = new Label(titulo);
        t.getStyleClass().add("panel-titulo");
        HBox cabecera = new HBox(8, ic, t);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        VBox p = new VBox(10, cabecera);
        p.getStyleClass().add("panel");
        p.getChildren().addAll(contenido);
        return p;
    }

    // =====================================================================
    //  Tema claro / oscuro
    // =====================================================================

    /** Cambia entre modo claro (por defecto) y modo oscuro agregando o quitando la clase CSS "oscuro". */
    private void alternarTema() {
        oscuro = !oscuro;
        if (oscuro) {
            if (!raiz.getStyleClass().contains(CLASE_OSCURO)) raiz.getStyleClass().add(CLASE_OSCURO);
        } else {
            raiz.getStyleClass().remove(CLASE_OSCURO);
        }
        actualizarTextoTema();
        log(oscuro ? "Modo oscuro activado" : "Modo claro activado");
    }

    private void actualizarTextoTema() {
        btnTema.setText(oscuro ? "☀  Modo claro" : "🌙  Modo oscuro");
    }

    // =====================================================================
    //  Lógica: conexión, lecturas y refresco
    // =====================================================================

    private void configurarArduino() {
        arduino.setOnLectura(l -> Platform.runLater(() -> procesarLectura(l)));
        arduino.setOnRegistro(m -> Platform.runLater(() -> log(m)));
        arduino.setOnConexion(c -> Platform.runLater(() -> {
            conectado = c;
            if (c) {
                log(arduino.isSimulacion() ? "Modo simulación iniciado"
                        : "Conexión con Arduino establecida");
                log("Puerto " + arduino.getNombrePuerto() + " • " + arduino.getBaudios() + " bps");
            } else {
                log("Conexión con Arduino perdida");
            }
            refrescarEstado();
        }));
    }

    private void conectar(String nombre) {
        log("Conectando con " + nombre + "...");
        sensores.forEach(Sensor::reiniciar);
        ultimaLecturaMs = 0;
        arduino.conectar(nombre);
        boolean sim = arduino.isSimulacion();
        filaSimulacion.setVisible(sim);
        filaSimulacion.setManaged(sim);
        refrescarEstado();
    }

    private void reiniciar() {
        gestor.limpiar();
        String puerto = cmbPuertos.getValue() != null ? cmbPuertos.getValue() : ArduinoService.SIMULACION;
        log("Reiniciando sistema...");
        conectar(puerto);
    }

    private void refrescarPuertos() {
        String seleccion = cmbPuertos.getValue();
        List<String> opciones = new ArrayList<>();
        opciones.add(ArduinoService.SIMULACION);
        opciones.addAll(ArduinoService.listarPuertos());
        cmbPuertos.getItems().setAll(opciones);
        cmbPuertos.setValue(seleccion != null && opciones.contains(seleccion)
                ? seleccion : ArduinoService.SIMULACION);
    }

    private void procesarLectura(Lectura l) {
        ultimaLecturaMs = System.currentTimeMillis();
        if (l.temperatura() != null) aplicar(temperatura, l.temperatura());
        if (l.gas() != null) aplicar(gas, l.gas());
        if (l.flama() != null) aplicar(flama, l.flama() ? 1 : 0);

        actualizarGrafica(l);
        tarjetaTemp.actualizar(temperatura, l.temperatura() != null);
        tarjetaGas.actualizar(gas, l.gas() != null);
        tarjetaFlama.actualizar(flama, l.flama() != null);

        contadorLecturas++;
        if (contadorLecturas % 10 == 1) {
            log("Temperatura: " + texto(temperatura) + "  |  Gas: " + texto(gas)
                    + "  |  Flama: " + texto(flama));
        }
        refrescarEstado();
    }

    private String texto(Sensor s) {
        return s.estaConectado() ? s.getValorTexto() : "--";
    }

    private void aplicar(Sensor sensor, double valor) {
        Nivel anterior = sensor.getNivel();
        sensor.actualizar(valor);
        gestor.procesar(sensor, anterior);
    }

    private void actualizarGrafica(Lectura l) {
        double t = System.currentTimeMillis() / 1000.0;
        if (l.temperatura() != null) serieTemp.getData().add(new XYChart.Data<>(t, l.temperatura()));
        if (l.gas() != null) serieGas.getData().add(new XYChart.Data<>(t, gas.getPorcentaje()));
        podar(serieTemp, t - VENTANA_SEGUNDOS);
        podar(serieGas, t - VENTANA_SEGUNDOS);
        ejeTiempo.setLowerBound(t - VENTANA_SEGUNDOS);
        ejeTiempo.setUpperBound(t);
    }

    private void podar(XYChart.Series<Number, Number> serie, double limite) {
        var datos = serie.getData();
        while (!datos.isEmpty() && datos.get(0).getXValue().doubleValue() < limite) {
            datos.remove(0);
        }
    }

    /** Se ejecuta cada segundo: reloj, detección de pérdida de señal y refresco general. */
    private void tick() {
        LocalDateTime ahora = LocalDateTime.now();
        lblFecha.setText(FECHA.format(ahora));
        lblHora.setText(HORA.format(ahora));
        if (tarjetaTemp != null) {
            tarjetaTemp.actualizar(temperatura, false);
            tarjetaGas.actualizar(gas, false);
            tarjetaFlama.actualizar(flama, false);
        }
        refrescarEstado();
    }

    private void refrescarEstado() {
        boolean datosAlDia = conectado && ultimaLecturaMs > 0
                && System.currentTimeMillis() - ultimaLecturaMs < LIMITE_SIN_DATOS_MS;
        boolean sim = arduino.isSimulacion();
        long sensoresActivos = sensores.stream().filter(Sensor::estaConectado).count();

        String estado = !conectado ? "Arduino desconectado" : sim ? "Modo simulación" : "Arduino conectado";
        lblConexion.setText(estado);
        lblPuertoCab.setText(sim ? "Datos simulados" : arduino.getNombrePuerto() + "  •  " + arduino.getBaudios() + " bps");
        puntoConexion.getStyleClass().setAll(conectado ? "punto-ok" : "punto-error");

        lblEstArduino.setText(conectado ? "● Conectado" : "● Desconectado");
        lblEstArduino.getStyleClass().removeAll("texto-ok", "texto-error");
        lblEstArduino.getStyleClass().add(conectado ? "texto-ok" : "texto-error");
        lblEstPuerto.setText(sim ? "Simulación" : arduino.getNombrePuerto());
        lblEstVel.setText(sim ? "--" : arduino.getBaudios() + " bps");
        lblEstUltima.setText(ultimaLecturaMs == 0 ? "--" : FECHA_HORA.format(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(ultimaLecturaMs), ZoneId.systemDefault())));
        lblEstSensores.setText(sensoresActivos + " de " + sensores.size() + " con señal");

        lblComunicacion.setText(datosAlDia ? "Comunicación estable" : "Sin comunicación");
        anilloTxt.setText(datosAlDia ? "✔" : "!");
        anillo.getStyleClass().setAll("anillo", datosAlDia ? "anillo-ok" : "anillo-error");

        boolean enAlerta = !gestor.getActivas().isEmpty();
        pieSistema.setText(enAlerta ? "⚠  Sistema en alerta" : "🌿  Sistema en funcionamiento");
        pieSistema.getStyleClass().removeAll("texto-ok", "texto-error");
        pieSistema.getStyleClass().add(enAlerta ? "texto-error" : "texto-ok");
        pieArduino.setText("Arduino: " + (sim ? "Simulación" : arduino.getNombrePuerto()));
        pieSensores.setText("Sensores: " + sensoresActivos + "/" + sensores.size() + " conectados");
        pieComunicacion.setText("Comunicación: " + (datosAlDia ? "Estable" : "Sin datos"));
    }

    /** Actualiza el panel de alertas activas, las últimas alertas y el botón de silencio. */
    private void refrescarAlertas() {
        var activas = gestor.getActivas();
        contadorAlertas.setText(String.valueOf(activas.size()));
        contadorAlertas.getStyleClass().setAll("label", "contador", activas.isEmpty() ? "contador-ok" : "contador-peligro");

        cajaAlertas.getChildren().clear();
        if (activas.isEmpty()) {
            Label check = new Label("✔");
            check.getStyleClass().add("check-grande");
            Label t = new Label("No hay alertas activas");
            t.getStyleClass().add("fila-valor");
            Label s = new Label("El sistema se encuentra estable");
            s.getStyleClass().add("texto-suave");
            VBox vacio = new VBox(4, check, t, s);
            vacio.setAlignment(Pos.CENTER);
            vacio.setMaxWidth(Double.MAX_VALUE);
            cajaAlertas.getChildren().add(vacio);
            cajaAlertas.setAlignment(Pos.CENTER);
        } else {
            cajaAlertas.setAlignment(Pos.TOP_LEFT);
            activas.forEach(i -> cajaAlertas.getChildren().add(filaAlerta(i)));
        }

        listaUltimas.getChildren().clear();
        gestor.getHistorial().stream().limit(4).forEach(i -> listaUltimas.getChildren().add(filaAlerta(i)));

        btnSilenciar.setText(gestor.isSilenciada() ? "🔔  Reactivar alarmas" : "🔕  Silenciar alarmas");
        if (tabla != null) tabla.refresh();
        refrescarEstado();
    }

    private Node filaAlerta(Incidencia i) {
        Label icono = new Label(emoji(i.getSensor()));
        icono.getStyleClass().addAll("icono-mini", i.getNivel() == Nivel.CRITICA ? "icono-mini-crit" : "icono-mini-adv");
        Label desc = new Label(i.getDescripcion());
        desc.getStyleClass().add("fila-valor");
        Label hora = new Label(FECHA_HORA.format(i.getFecha()));
        hora.getStyleClass().add("texto-suave");
        Label badge = new Label(i.getNivel().getEtiqueta());
        badge.getStyleClass().addAll("badge", claseNivel(i.getNivel()));
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox fila = new HBox(10, icono, new VBox(2, desc, hora), espacio, badge);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    private void log(String mensaje) {
        registro.add(0, "[" + HORA.format(LocalTime.now()) + "] " + mensaje);
        if (registro.size() > 150) registro.remove(registro.size() - 1);
    }

    // =====================================================================
    //  Utilidades
    // =====================================================================

    private static String emoji(String tipoSensor) {
        return switch (tipoSensor) {
            case "Temperatura" -> "🌡";
            case "Gas" -> "☁";
            default -> "🔥";
        };
    }

    static String claseNivel(Nivel n) {
        return switch (n) {
            case NORMAL -> "badge-normal";
            case ADVERTENCIA -> "badge-adv";
            case CRITICA -> "badge-crit";
        };
    }

    // =====================================================================
    //  Tarjeta de sensor: gris cuando todo está normal, de color cuando detecta
    // =====================================================================

    private static class TarjetaSensor {
        private static final int PUNTOS = 30;

        private final HBox raiz;
        private final Label valor = new Label("--");
        private final Label estado = new Label("Sin señal");
        private final Label detalle = new Label();
        private final XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        private final NumberAxis ejeX = new NumberAxis();
        private final NumberAxis ejeY = new NumberAxis();
        private int contador = 0;
        private boolean enDeteccion = false;

        TarjetaSensor(String emoji, String nombre, Sensor sensor, boolean escalaFija) {
            Label icono = new Label(emoji);
            icono.getStyleClass().add("icono-sensor");
            Label titulo = new Label(nombre);
            titulo.getStyleClass().add("sensor-titulo");
            valor.getStyleClass().add("sensor-valor");
            detalle.getStyleClass().add("texto-suave");
            VBox textos = new VBox(6, titulo, valor, estado, detalle);
            textos.setAlignment(Pos.CENTER_LEFT);

            ejeX.setAutoRanging(false);
            ejeX.setLowerBound(0);
            ejeX.setUpperBound(PUNTOS);
            if (escalaFija) {
                ejeY.setAutoRanging(false);
                ejeY.setLowerBound(0);
                ejeY.setUpperBound(1.2);
            }
            for (NumberAxis eje : List.of(ejeX, ejeY)) {
                eje.setTickLabelsVisible(false);
                eje.setTickMarkVisible(false);
                eje.setMinorTickVisible(false);
                eje.setOpacity(0);
            }
            LineChart<Number, Number> mini = new LineChart<>(ejeX, ejeY);
            mini.getData().add(serie);
            mini.setAnimated(false);
            mini.setCreateSymbols(false);
            mini.setLegendVisible(false);
            mini.setHorizontalGridLinesVisible(false);
            mini.setVerticalGridLinesVisible(false);
            mini.setHorizontalZeroLineVisible(false);
            mini.setVerticalZeroLineVisible(false);
            mini.setPrefSize(150, 90);
            mini.setMinWidth(60);
            mini.getStyleClass().add("mini-chart");
            HBox.setHgrow(mini, Priority.ALWAYS);

            raiz = new HBox(14, icono, textos, mini);
            raiz.setAlignment(Pos.CENTER_LEFT);
            actualizar(sensor, false);
        }

        Node getNodo() {
            return raiz;
        }

        /** lecturaNueva = true cuando acaba de llegar un dato de este sensor. */
        void actualizar(Sensor s, boolean lecturaNueva) {
            boolean conectado = s.estaConectado();
            Nivel nivel = conectado ? s.getNivel() : Nivel.NORMAL;

            valor.setText(conectado ? s.getValorTexto() : "--");
            detalle.setText(conectado ? s.getDetalle() : "Sin datos del sensor");
            estado.setText(conectado ? nivel.getEtiqueta() : "Sin señal");
            estado.getStyleClass().setAll("label", "badge", conectado ? claseNivel(nivel) : "badge-gris");
            raiz.getStyleClass().setAll("panel", "sensor-card",
                    nivel == Nivel.NORMAL ? "card-normal" : nivel == Nivel.ADVERTENCIA ? "card-adv" : "card-crit");

            boolean peligro = conectado && nivel.esPeligro();
            if (peligro && !enDeteccion) { // empieza una detección: el minigráfico arranca de cero
                serie.getData().clear();
                contador = 0;
            }
            enDeteccion = peligro;
            if (peligro && lecturaNueva) agregarPunto(s.getValor());
        }

        private void agregarPunto(double v) {
            serie.getData().add(new XYChart.Data<>(contador++, v));
            if (serie.getData().size() > PUNTOS) serie.getData().remove(0);
            ejeX.setLowerBound(Math.max(0, contador - PUNTOS));
            ejeX.setUpperBound(Math.max(PUNTOS, contador));
        }
    }
}