package org.example.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/client/hello-view.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 500, 400);
            primaryStage.setTitle("Sistema de Tickets - Intelilly");
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("Error al iniciar la aplicación:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}