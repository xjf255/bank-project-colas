package org.example.service;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.util.Objects;

public class ServicioClienteApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
            // Cargar el FXML para Servicio al Cliente usando el nombre directo.
            // "servicioCliente.fxml" debe estar en la misma ruta de paquete dentro de resources
            // que esta clase dentro de java.
            // Es decir: /org/example/servicioCliente/servicioCliente.fxml
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("servicioCliente.fxml"));
            AnchorPane load = fxmlLoader.load();
            Scene scene = new Scene(load);
            stage.setTitle("Servicio al Cliente");
            stage.setScene(scene);

            Object controller = fxmlLoader.getController();
            stage.setOnCloseRequest(event -> {
                // Opcional: Limpiar recursos al cerrar, como la conexión del socket
                if (controller instanceof ServicioClienteController) {
                    ((ServicioClienteController) controller).shutdown();
                }
            });
            stage.show();
        }

    public static void main(String[] args) {
        launch();
    }
}