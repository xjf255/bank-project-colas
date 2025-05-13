package org.example.client;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.PropertiesInfo;
import org.example.shared.Ticket;
import org.example.shared.TicketTypes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class HelloController {
    private int contadorCaja = 1;
    private int contadorServicio = 1;

    @FXML private Label welcomeLabel;
    @FXML private Button cajaButton;
    @FXML private Button servicioButton;
    @FXML private Label statusLabel;

    // Variables de Red
    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;
    private String serverIp;
    private int serverPort;
    private volatile boolean isConnected = false;
    private String clientName = "TicketGenerator_01";

    @FXML
    public void initialize() {
        welcomeLabel.setText("BIENVENIDO");
        statusLabel.setText("Estado: Desconectado");
        loadServerConfigAndConnect();
    }

    private void loadServerConfigAndConnect() {
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties properties = propertiesInfo.getProperties();
            serverIp = properties.getProperty("server.ip", "25.53.112.39");
            serverPort = Integer.parseInt(properties.getProperty("server.port", "12345"));
            connectToServer();
        } catch (Exception e) {
            System.err.println("CLIENT_GENERATOR: Error al cargar propiedades del servidor: " + e.getMessage());
            updateStatus("Error de configuración", true);
            serverIp = "25.53.112.39"; // Fallback
            serverPort = 12345;      // Fallback
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverIp, serverPort);
            outToServer = new ObjectOutputStream(socket.getOutputStream());

            InfoData registrationMsg = new InfoData();
            registrationMsg.setType(ClientTypes.GENERATOR);
            registrationMsg.setName(clientName);
            try {
                registrationMsg.setIp(InetAddress.getLocalHost().getHostAddress());
            } catch (Exception e) {
                registrationMsg.setIp("UnknownGeneratorIP");
            }
            outToServer.writeObject(registrationMsg);
            outToServer.flush();

            isConnected = true;
            System.out.println("CLIENT_GENERATOR: Conectado al servidor y registrado como GENERATOR.");
            updateStatus("Conectado", false);

        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al conectar con el servidor: " + e.getMessage());
            isConnected = false;
            updateStatus("Error de conexión", true);
        }
    }

    @FXML
    private void handleCaja() {
        String ticketId = String.format("C%03d", contadorCaja);
        Ticket nuevoTicket = new Ticket(ticketId, TicketTypes.CAJA);
        // numeroCaja is 1-5, always 1 digit.
        String ticketFormateado = generarFormatoTicket(nuevoTicket, "CAJA", (int)(Math.random() * 5) + 1, -1);
        mostrarTicket(ticketFormateado, "CAJA");
        guardarTicketEnArchivo(ticketFormateado, "caja_" + ticketId);
        contadorCaja++;
        enviarTicketAlServidor(nuevoTicket);
    }

    @FXML
    private void handleServicio() {
        String ticketId = String.format("S%03d", contadorServicio);
        Ticket nuevoTicket = new Ticket(ticketId, TicketTypes.SERVICIO);
        // tiempoEstimado is 5-19, can be 1 or 2 digits. %2d handles this.
        String ticketFormateado = generarFormatoTicket(nuevoTicket, "SERVICIO", -1, (int)(Math.random() * 15) + 5);
        mostrarTicket(ticketFormateado, "SERVICIO");
        guardarTicketEnArchivo(ticketFormateado, "servicio_" + ticketId);
        contadorServicio++;
        enviarTicketAlServidor(nuevoTicket);
    }

    private void mostrarTicket(String ticketTexto, String tipo) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ticket Generado - " + tipo);
        alert.setHeaderText("Su ticket ha sido generado con éxito");

        TextArea ticketPreview = new TextArea(ticketTexto);
        ticketPreview.setEditable(false);
        ticketPreview.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        // --- MODIFICATION START ---
        // Set preferred row and column count for the TextArea
        // Ticket has 13 lines. Max width is ~33 characters.
        ticketPreview.setPrefRowCount(14);  // 13 lines of text + 1 buffer row
        ticketPreview.setPrefColumnCount(35); // Max 33 characters + 2 buffer columns

        alert.getDialogPane().setContent(ticketPreview);

        // Remove the fixed size for the dialog pane to allow auto-sizing.
        // alert.getDialogPane().setPrefSize(400,300); // This line is removed/commented
        // --- MODIFICATION END ---

        // The dialog will now attempt to size itself to fit the TextArea's preferred size.
        alert.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(10));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void enviarTicketAlServidor(Ticket ticket) {
        if (!isConnected || outToServer == null) {
            System.err.println("CLIENT_GENERATOR: No conectado al servidor. No se puede enviar el ticket.");
            mostrarError("No conectado al servidor. Intente reiniciar la aplicación o verifique la conexión.");
            updateStatus("Desconectado - No se pudo enviar", true);
            return;
        }

        try {
            InfoData dataParaServidor = new InfoData();
            dataParaServidor.setType(ClientTypes.GENERATOR);
            dataParaServidor.setName(clientName);
            dataParaServidor.setTickets(ticket);

            outToServer.writeObject(dataParaServidor);
            outToServer.flush();
            System.out.println("CLIENT_GENERATOR: Ticket " + ticket.getValue() + " enviado al servidor.");
            updateStatus("Ticket " + ticket.getValue() + " enviado", false);

        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al enviar ticket al servidor: " + e.getMessage());
            isConnected = false;
            updateStatus("Error de envío", true);
            mostrarError("Error al enviar ticket: " + e.getMessage());
            closeNetworkResources();
        }
    }

    private String generarFormatoTicket(Ticket ticket, String tipoDisplay, int numeroCaja, int tiempoEstimado) {
        LocalDateTime ahora = ticket.getTimestamp();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        // Max width of these lines is 33 characters.
        // Example: "║  la caja número 5             ║"
        // Example: "║  Tiempo estimado: 15 min      ║"
        if (tipoDisplay.equals("CAJA")) {
            return String.format(
                    "╔══════════════════════════════╗\n" + // Length 30
                            "║         TICKET DE CAJA       ║\n" + // Length 30
                            "╠══════════════════════════════╣\n" + // Length 30
                            "║ Número:%20s  ║\n" + // Formats to 31 chars
                            "║ Fecha: %-20s  ║\n" + // Formats to 31 chars
                            "╠══════════════════════════════╣\n" +
                            "║                              ║\n" +
                            "║  Por favor acérquese a       ║\n" +
                            "║  la caja número %d            ║\n" + // Formats to 33 chars if %d is 1 digit
                            "║                              ║\n" +
                            "║  Gracias por su preferencia  ║\n" +
                            "║                              ║\n" +
                            "╚══════════════════════════════╝\n",
                    ticket.getValue(),
                    ahora.format(formatter),
                    numeroCaja
            );
        } else { // SERVICIO
            return String.format(
                    "╔══════════════════════════════╗\n" + // Length 30
                            "║    TICKET SERVICIO CLIENTE   ║\n" + // Length 30
                            "╠══════════════════════════════╣\n" + // Length 30
                            "║ Número: %20s ║\n" + // Formats to 31 chars
                            "║ Fecha: %-20s  ║\n" + // Formats to 31 chars
                            "╠══════════════════════════════╣\n" +
                            "║                              ║\n" +
                            "║  Espere su turno en la       ║\n" +
                            "║  zona de espera              ║\n" +
                            "║                              ║\n" +
                            "║  Tiempo estimado: %2d min     ║\n" + // Formats to 33 chars
                            "║                              ║\n" +
                            "╚══════════════════════════════╝\n",
                    ticket.getValue(),
                    ahora.format(formatter),
                    tiempoEstimado
            );
        }
    }

    private void mostrarError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error de Red");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void guardarTicketEnArchivo(String ticketTexto, String nombreBase) {
        String nombreArchivo = "ticket_" + nombreBase + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(nombreArchivo))) {
            writer.write(ticketTexto);
            System.out.println("CLIENT_GENERATOR: Ticket guardado en: " + nombreArchivo);
        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al guardar el ticket en archivo:");
            e.printStackTrace();
        }
    }

    private void updateStatus(String message, boolean isError) {
        statusLabel.setText("Estado: " + message);
        if (isError) {
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else {
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        }
    }

    public void stop() {
        System.out.println("CLIENT_GENERATOR: Deteniendo cliente generador...");
        isConnected = false;
        closeNetworkResources();
    }

    private void closeNetworkResources() {
        try {
            if (outToServer != null) outToServer.close();
            if (inFromServer != null) inFromServer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("CLIENT_GENERATOR: Recursos de red cerrados.");
        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al cerrar recursos de red: " + e.getMessage());
        }
    }
}