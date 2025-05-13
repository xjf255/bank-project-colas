package org.example.teller;

import javafx.application.Application;
import javafx.application.Platform; // Importante para Platform.exit()
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
// import javafx.scene.layout.AnchorPane; // Ya no se usa directamente para 'load'
import javafx.scene.layout.VBox;     // <-- AÑADIR IMPORTACIÓN
import javafx.stage.Stage;
import utilities.Paths; // Asumo que esta clase existe y Paths.SERVICIO_CAJA es correcto

import java.io.IOException; // Es más preciso usar IOException aquí

public class CajaApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException { // Cambiado Exception a IOException

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(Paths.SERVICIO_CAJA));

        // Corrige el tipo de la variable 'load' a VBox
        VBox root = fxmlLoader.load(); // Usamos 'root' como nombre, y 'load' es el método

        // Obtener el controlador después de cargar el FXML
        CajaController controller = fxmlLoader.getController();

        Scene scene = new Scene(root); // Puedes definir dimensiones si lo deseas: new Scene(root, ancho, alto);
        stage.setTitle("Servicio Caja");
        stage.setScene(scene);

        // Configurar el comportamiento al cerrar la ventana
        stage.setOnCloseRequest(event -> {
            System.out.println("Cerrando aplicación desde CajaApplication...");
            if (controller != null) {
                controller.shutdown(); // Llama al método shutdown de tu CajaController
            }
            Platform.exit(); // Cierra la plataforma JavaFX de forma limpia
            System.exit(0);  // Asegura que la JVM termine
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(args); // 'launch()' sin argumentos está bien si no pasas ninguno desde la línea de comandos
    }
}