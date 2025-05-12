package org.example.screen;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    private HelloController controller; // Guardar referencia al controlador

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        controller = fxmlLoader.getController(); // Obtener el controlador

        stage.setTitle("Pantalla de Turnos del Banco");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        // Este método se llama cuando la aplicación JavaFX se cierra
        if (controller != null) {
            controller.stop(); // Llama al método stop de tu controlador
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}