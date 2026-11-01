package com.deteccion;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Punto de entrada: crea la ventana y carga el dashboard. */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        DashboardController dashboard = new DashboardController();

        Scene escena = new Scene(dashboard.getRoot(), 1366, 820);
        escena.getStylesheets().add(
                getClass().getResource("/com/deteccion/styles.css").toExternalForm());

        stage.setTitle("Sistema de Detección Térmico-Gaseosa");
        stage.setMinWidth(1180);
        stage.setMinHeight(720);
        stage.setScene(escena);
        stage.setOnCloseRequest(e -> dashboard.detener());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
