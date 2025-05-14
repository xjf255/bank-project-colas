package org.example.client;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.PropertiesInfo;
import org.example.shared.Ticket; // Asegúrate que esta es tu clase Ticket
import org.example.shared.TicketTypes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime; // Sigue siendo útil para el formato de archivo y logs
import java.time.format.DateTimeFormatter;
import java.util.Date; // Para el formato visual local del ticket
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;

public class HelloController {
    private int contadorCaja = 1;
    private int contadorServicio = 1;

    @FXML private Label welcomeLabel;
    @FXML private Button cajaButton;
    @FXML private Button servicioButton;
    @FXML private Label ticketDisplayLabel;
    @FXML private Label statusLabel;

    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;
    private String serverIp;
    private int serverPort;
    private volatile boolean isConnected = false;
    private String clientName = "TicketGenerator_Estacion_01";

    private final Random random = new Random();
    private final SimpleDateFormat sdfLocal = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        if (welcomeLabel != null) {
            // El texto se define en el FXML
        }
        if (ticketDisplayLabel != null) {
            // El texto inicial se define en el FXML
        }
        if (statusLabel != null) {
            statusLabel.setText("Estado: Inicializando...");
        } else {
            System.err.println("ADVERTENCIA: statusLabel no fue inyectado desde FXML. Creando uno nuevo programáticamente.");
            statusLabel = new Label("Estado: Inicializando (Fallback)");
            if (welcomeLabel != null && welcomeLabel.getParent() instanceof VBox) {
                ((VBox) welcomeLabel.getParent()).getChildren().add(statusLabel);
            }
        }
        loadServerConfigAndConnect();
    }

    private void loadServerConfigAndConnect() {
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties properties = propertiesInfo.getProperties();
            serverIp = properties.getProperty("server.ip", "127.0.0.1");
            serverPort = Integer.parseInt(properties.getProperty("server.port", "12345"));
            new Thread(this::connectToServer).start();
        } catch (Exception e) {
            System.err.println("CLIENT_GENERATOR: Error al cargar propiedades del servidor: " + e.getMessage());
            updateStatus("Error de configuración", true);
            serverIp = "25.53.36.80";
            serverPort = 5000;
        }
    }

    private void connectToServer() {
        updateStatus("Conectando...", false);
        try {
            closeNetworkResourcesSilently();
            socket = new Socket(serverIp, serverPort);
            outToServer = new ObjectOutputStream(socket.getOutputStream());
            outToServer.flush();
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
            updateStatus("Conectado al Servidor", false);
        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al conectar con el servidor: " + e.getMessage());
            isConnected = false;
            updateStatus("Error de conexión con servidor", true);
        }
    }

    @FXML
    private void handleCaja() {
        String ticketId = String.format("C-%03d", contadorCaja);

        Ticket nuevoTicket = new Ticket(ticketId, TicketTypes.CAJA);

        String ticketFormateado = generarFormatoVisualTicketLocal(ticketId, "CAJA", sdfLocal.format(new Date()), 5 + random.nextInt(11), "Operación Caja", "Gracias por su espera.");

        mostrarTicketLocalmente(ticketFormateado, "CAJA");
        guardarTicketEnArchivo(ticketFormateado, "caja_" + ticketId);
        contadorCaja++;
        enviarTicketAlServidor(nuevoTicket);
    }

    @FXML
    private void handleServicio() {
        String ticketId = String.format("S-%03d", contadorServicio);
        // --- CORRECCIÓN AQUÍ ---
        Ticket nuevoTicket = new Ticket(ticketId, TicketTypes.SERVICIO);

        String ticketFormateado = generarFormatoVisualTicketLocal(ticketId, "SERVICIO", sdfLocal.format(new Date()), 10 + random.nextInt(16), "Atención Cliente", "Un asesor lo atenderá.");

        mostrarTicketLocalmente(ticketFormateado, "SERVICIO");
        guardarTicketEnArchivo(ticketFormateado, "servicio_" + ticketId);
        contadorServicio++;
        enviarTicketAlServidor(nuevoTicket);
    }

    private void enviarTicketAlServidor(Ticket ticket) {
        if (!isConnected || outToServer == null || socket == null || socket.isClosed()) {
            System.err.println("CLIENT_GENERATOR: No conectado. No se puede enviar ticket " + ticket.getValue());
            mostrarError("No conectado al servidor. El ticket " + ticket.getValue() + " no pudo ser enviado.");
            updateStatus("Desconectado - Ticket NO enviado", true);
            new Thread(this::connectToServer).start();
            return;
        }
        try {
            InfoData dataParaServidor = new InfoData();
            dataParaServidor.setType(ClientTypes.GENERATOR);
            dataParaServidor.setName(clientName);
            dataParaServidor.setTickets(ticket); // Aquí se envía el objeto Ticket
            outToServer.writeObject(dataParaServidor);
            outToServer.flush();
            System.out.println("CLIENT_GENERATOR: Ticket " + ticket.getValue() + " enviado al servidor. Timestamp del ticket: " + ticket.getTimestamp());
            updateStatus("Ticket " + ticket.getValue() + " enviado", false);
        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al enviar ticket " + ticket.getValue() + ": " + e.getMessage());
            isConnected = false;
            updateStatus("Error de envío (" + ticket.getValue() + ")", true);
            mostrarError("Error al enviar ticket: " + e.getMessage());
            closeNetworkResourcesSilently();
            new Thread(this::connectToServer).start();
        }
    }

    // Formato visual para el ticket mostrado localmente en el Label y Alert
    private String generarFormatoVisualTicketLocal(String numeroTicket, String tipoTicket, String fechaHora, int esperaEstimada, String tipoOperacion, String mensajeDespedida) {
        String separadorAsteriscos = "******************************";
        String tipoDisplay = tipoTicket.equals("CAJA") ? "CAJA" : "SERVICIO";
        return String.format(
                "%s\n" +
                        "    TICKET DE %S    \n" +
                        "%s\n" +
                        " No.:     %s\n" +
                        " Fecha:   %s\n" +
                        "------------------------------\n" +
                        " Tipo:    %s\n" +
                        " Espera:  Aprox. %d min\n" +
                        "------------------------------\n" +
                        " %s\n" +
                        "%s",
                separadorAsteriscos, tipoDisplay, separadorAsteriscos,
                numeroTicket, fechaHora, tipoOperacion, esperaEstimada, mensajeDespedida, separadorAsteriscos
        );
    }

    private void mostrarTicketLocalmente(String ticketTexto, String tipo) {
        if (ticketDisplayLabel != null) {
            ticketDisplayLabel.setText(ticketTexto);
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ticket Generado - " + tipo);
        alert.setHeaderText("Su ticket ha sido generado con éxito:");
        TextArea ticketPreviewEnAlert = new TextArea(ticketTexto);
        ticketPreviewEnAlert.setEditable(false);
        ticketPreviewEnAlert.setWrapText(false);
        ticketPreviewEnAlert.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(ticketPreviewEnAlert);
        alert.getDialogPane().setPrefSize(420, 320);
        alert.show();
        PauseTransition delay = new PauseTransition(Duration.seconds(10));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void mostrarError(String mensaje) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(mensaje);
            alert.showAndWait();
        });
    }

    private void guardarTicketEnArchivo(String ticketTexto, String nombreBase) {
        String nombreArchivo = "ticket_" + nombreBase + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(nombreArchivo))) {
            writer.write(ticketTexto);
            System.out.println("CLIENT_GENERATOR: Ticket guardado en: " + nombreArchivo);
        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al guardar el ticket en archivo:");
            e.printStackTrace();
        }
    }

    private void updateStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("Estado: " + message);
                if (isError) {
                    statusLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-weight: bold; -fx-font-size: 16px;");
                } else {
                    statusLabel.setStyle("-fx-text-fill: #90EE90; -fx-font-weight: normal; -fx-font-size: 16px;");
                }
            }
        });
    }

    public void stop() {
        System.out.println("CLIENT_GENERATOR: Deteniendo cliente generador (llamado desde Application.stop)...");
        isConnected = false;
        closeNetworkResourcesSilently();
        System.out.println("CLIENT_GENERATOR: Cliente generador detenido.");
    }

    private void closeNetworkResourcesSilently() {
        try {
            if (outToServer != null) outToServer.close();
        } catch (IOException e) { /* ignorar */ }
        try {
            if (inFromServer != null) inFromServer.close();
        } catch (IOException e) { /* ignorar */ }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { /* ignorar */ }
        outToServer = null;
        inFromServer = null;
        socket = null;
        System.out.println("CLIENT_GENERATOR: Recursos de red cerrados (silenciosamente).");
    }
}