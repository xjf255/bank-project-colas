package org.example.client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.PauseTransition;

public class HelloController {
    private int contadorCaja = 1;
    private int contadorServicio = 1;

    @FXML private Label welcomeLabel;
    @FXML private Button cajaButton;
    @FXML private Button servicioButton;
    @FXML private TextArea ticketArea;
    @FXML private VBox ticketContainer;

    @FXML
    public void initialize() {
        welcomeLabel.setText("SISTEMA DE GESTIÓN DE TICKETS");
        cajaButton.setText("Generar Ticket de Caja");
        servicioButton.setText("Generar Ticket de Servicio");

        String buttonStyle = "-fx-font-size: 14px; -fx-pref-width: 250px; -fx-pref-height: 40px;";
        cajaButton.setStyle(buttonStyle + "-fx-background-color: #3498db; -fx-text-fill: white;");
        servicioButton.setStyle(buttonStyle + "-fx-background-color: #2ecc71; -fx-text-fill: white;");

        ticketArea.setEditable(false);
        ticketArea.setWrapText(true);
    }

    @FXML
    private void handleCaja() {
        String ticket = generarTicketCaja();
        mostrarTicket(ticket, "CAJA");
        guardarTicketEnArchivo(ticket, "caja");
    }

    @FXML
    private void handleServicio() {
        String ticket = generarTicketServicio();
        mostrarTicket(ticket, "SERVICIO");
        guardarTicketEnArchivo(ticket, "servicio");
    }

    private String generarTicketCaja() {
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        return String.format(
                "╔══════════════════════════════╗\n" +
                        "║       TICKET DE CAJA        ║\n" +
                        "╠══════════════════════════════╣\n" +
                        "║ Número: C-%03d               ║\n" +
                        "║ Fecha: %-20s ║\n" +
                        "╠══════════════════════════════╣\n" +
                        "║                              ║\n" +
                        "║  Por favor acérquese a       ║\n" +
                        "║  la caja número %d           ║\n" +
                        "║                              ║\n" +
                        "║  Gracias por su preferencia  ║\n" +
                        "║                              ║\n" +
                        "╚══════════════════════════════╝\n",
                contadorCaja++,
                ahora.format(formatter),
                (int)(Math.random() * 5) + 1
        );
    }

    private String generarTicketServicio() {
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        return String.format(
                "╔══════════════════════════════╗\n" +
                        "║   TICKET SERVICIO CLIENTE   ║\n" +
                        "╠══════════════════════════════╣\n" +
                        "║ Número: S-%03d               ║\n" +
                        "║ Fecha: %-20s ║\n" +
                        "╠══════════════════════════════╣\n" +
                        "║                              ║\n" +
                        "║  Espere su turno en la       ║\n" +
                        "║  zona de espera              ║\n" +
                        "║                              ║\n" +
                        "║  Tiempo estimado: %d min     ║\n" +
                        "║                              ║\n" +
                        "╚══════════════════════════════╝\n",
                contadorServicio++,
                ahora.format(formatter),
                (int)(Math.random() * 15) + 5
        );
    }

    private void mostrarTicket(String ticket, String tipo) {
        ticketArea.setText(ticket);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ticket Generado - " + tipo);
        alert.setHeaderText("Su ticket ha sido generado con éxito");

        TextArea ticketPreview = new TextArea(ticket);
        ticketPreview.setEditable(false);
        ticketPreview.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        alert.getDialogPane().setContent(ticketPreview);

        // Mostrar la alerta por 10 segundos
        alert.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(10));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void guardarTicketEnArchivo(String ticket, String tipo) {
        String nombreArchivo = "ticket_" + tipo + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(nombreArchivo))) {
            writer.write(ticket);
            System.out.println("Ticket guardado en: " + nombreArchivo);
        } catch (IOException e) {
            System.err.println("Error al guardar el ticket en archivo:");
            e.printStackTrace();
        }
    }
}