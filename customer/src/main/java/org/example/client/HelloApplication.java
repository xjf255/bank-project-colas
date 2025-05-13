package org.example.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    private HelloController controller; // Guardar referencia al controlador

    @Override
    public void start(Stage primaryStage) {
        try {
            // Asegúrate que la ruta al FXML sea correcta. Si está en resources/org/example/client/
            FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
            // Si está directamente en resources: FXMLLoader loader = new FXMLLoader(getClass().getResource("/hello-view.fxml"));

            Parent root = loader.load();
            controller = loader.getController(); // Obtener el controlador

            Scene scene = new Scene(root, 500, 450); // Un poco más de alto para el label de estado
            primaryStage.setTitle("Sistema de Tickets - Generador Intelilly");
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("Error al iniciar la aplicación cliente generador:");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        // Este método se llama cuando la aplicación JavaFX se cierra
        if (controller != null) {
            controller.stop(); // Llama al método stop de tu controlador
        }
        super.stop();
        System.out.println("Aplicación cliente generador detenida.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}