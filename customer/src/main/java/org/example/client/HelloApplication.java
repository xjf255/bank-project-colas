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

            // Adjusted scene size to better fit large square buttons
            Scene scene = new Scene(root, 720, 550);
            primaryStage.setTitle("Sistema de Tickets - Intelilly");
            primaryStage.setScene(scene);
            primaryStage.show();

            // Handle application stop to gracefully close network resources
            primaryStage.setOnCloseRequest(event -> {
                HelloController controller = loader.getController();
                if (controller != null) {
                    controller.stop();
                }
            });

        } catch (Exception e) {
            System.err.println("Error al iniciar la aplicación:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}