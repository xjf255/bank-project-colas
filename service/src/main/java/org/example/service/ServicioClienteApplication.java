package org.example.service;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;

public class ServicioClienteApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        AnchorPane root = FXMLLoader.load(getClass().getResource("servicioCliente.fxml"));
        Scene scene = new Scene(root);
        stage.setTitle("Servicio al Cliente");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}