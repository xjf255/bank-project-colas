package org.example.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class HelloApplication extends Application {
    private HelloController controller; // Para llamar a stop()

    @Override
    public void start(Stage primaryStage) {
        try {
            // Asumiendo que hello-view.fxml está en src/main/resources/org/example/client/
            URL fxmlUrl = getClass().getResource("hello-view.fxml");
            if (fxmlUrl == null) {
                // Intenta desde la raíz de resources si no se encuentra en el paquete
                System.err.println("Advertencia: FXML 'hello-view.fxml' no encontrado en el paquete. Intentando desde la raíz de resources...");
                fxmlUrl = getClass().getResource("/hello-view.fxml");
            }

            if (fxmlUrl == null) {
                System.err.println("Error crítico: No se pudo encontrar el archivo FXML 'hello-view.fxml'.");
                System.err.println("Verifica la ubicación del archivo y que el proyecto haya sido limpiado y reconstruido.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            controller = loader.getController(); // Obtener instancia del controlador

            // Puedes ajustar este tamaño base si es necesario
            Scene scene = new Scene(root, 600, 750);
            primaryStage.setTitle("Generador de Tickets de Cliente");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true); // Iniciar maximizado

            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Cerrando aplicación cliente...");
                if (controller != null) {
                    controller.stop(); // Llamar al método stop del controlador
                }
            });

            primaryStage.show();

        } catch (IOException e) {
            System.err.println("Error al cargar la interfaz de cliente (FXML):");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error inesperado en la aplicación cliente:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}