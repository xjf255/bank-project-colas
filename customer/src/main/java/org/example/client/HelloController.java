package org.example.client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import org.example.shared.Ticket;
import org.example.shared.TicketTypes;

public class HelloController {
    private int contadorCaja = 1;
    private int contadorServicio = 1;

    @FXML private Label welcomeLabel;
    @FXML private Button cajaButton;
    @FXML private Button servicioButton;

    @FXML
    public void initialize() {
        welcomeLabel.setText("BIENVENIDO");
        cajaButton.setText("Caja");
        servicioButton.setText("Servicio al cliente");

        String buttonStyle = "-fx-font-size: 18px; -fx-font-weight: bold; -fx-pref-width: 300px; -fx-pref-height: 100px;";
        cajaButton.setStyle(buttonStyle + "-fx-background-color: #3498db; -fx-text-fill: white;");
        servicioButton.setStyle(buttonStyle + "-fx-background-color: #2ecc71; -fx-text-fill: white;");
    }

    @FXML
    private void handleCaja() {
        String ticketStr = generarTicketCaja();
        mostrarTicket(ticketStr, "CAJA");

        // Crear objeto Ticket y enviarlo al servidor
        Ticket ticketObj = new Ticket("C-" + String.format("%03d", contadorCaja), TicketTypes.CAJA);
        enviarTicketAlServidor(ticketObj);
    }

    @FXML
    private void handleServicio() {
        String ticketStr = generarTicketServicio();
        mostrarTicket(ticketStr, "SERVICIO");

        // Crear objeto Ticket y enviarlo al servidor
        Ticket ticketObj = new Ticket("S-" + String.format("%03d", contadorServicio), TicketTypes.SERVICIO);
        enviarTicketAlServidor(ticketObj);
    }

    private String generarTicketCaja() {
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        String ticketStr = String.format(
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

        return ticketStr;
    }

    private String generarTicketServicio() {
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        String ticketStr = String.format(
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

        return ticketStr;
    }

    private void enviarTicketAlServidor(Ticket ticket) {
        new Thread(() -> {
            try (Socket socket = new Socket("25.53.36.80", 5000);
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

                oos.writeObject(ticket);
                System.out.println("Ticket enviado al servidor: " + ticket.getValue());

            } catch (IOException e) {
                System.err.println("Error al enviar el ticket al servidor: " + e.getMessage());
                // Mostrar alerta al usuario si falla el envío
                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error de conexión");
                    alert.setHeaderText("No se pudo conectar al servidor");
                    alert.setContentText("El ticket se generó pero no se pudo enviar al servidor.");
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private void mostrarTicket(String ticket, String tipo) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("TICKET DE " + tipo);
        alert.setHeaderText(null);

        alert.getDialogPane().setMinSize(650, 500);
        alert.getDialogPane().setPrefSize(650, 500);

        TextArea ticketPreview = new TextArea(ticket);
        ticketPreview.setEditable(false);
        ticketPreview.setWrapText(true);
        ticketPreview.setStyle("-fx-font-family: monospace; -fx-font-size: 16px;");
        ticketPreview.setPrefSize(600, 400);

        alert.getDialogPane().setContent(ticketPreview);
        alert.getDialogPane().setPadding(new Insets(10));

        alert.show();
        PauseTransition delay = new PauseTransition(Duration.seconds(10));
        delay.setOnFinished(e -> alert.close());
        delay.play();
    }
}