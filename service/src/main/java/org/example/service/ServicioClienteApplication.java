package org.example.service;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class ServicioClienteApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
        AnchorPane load = fxmlLoader.load();
        Scene scene = new Scene(load);
        stage.setTitle("Servicio al Cliente");
        stage.setScene(scene);

        Object controller = fxmlLoader.getController();
        stage.setOnCloseRequest(event -> {
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