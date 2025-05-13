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
    @FXML private TextArea ticketArea; // Para mostrar el ticket generado localmente
    @FXML private Label statusLabel; // NUEVO: Para mostrar el estado de la conexión

    // Variables de Red
    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer; // Opcional para este cliente, pero bueno para futuras respuestas del servidor
    private String serverIp;
    private int serverPort;
    private volatile boolean isConnected = false;
    private String clientName = "TicketGenerator_01"; // Nombre para este cliente generador

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

        // Crear el statusLabel si no está en el FXML (mejor añadirlo al FXML)
        if (statusLabel == null) {
            statusLabel = new Label("Estado: Desconectado");
            // Si ticketContainer existe y es un VBox, puedes añadirlo ahí.
            // O necesitas un placeholder en tu FXML.
            // Por ahora, solo lo inicializamos.
        } else {
            statusLabel.setText("Estado: Desconectado");
        }


        loadServerConfigAndConnect();
    }

    private void loadServerConfigAndConnect() {
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties properties = propertiesInfo.getProperties();
            serverIp = properties.getProperty("server.ip", "127.0.0.1"); // IP del servidor, localhost por defecto
            serverPort = Integer.parseInt(properties.getProperty("server.port", "12345")); // Puerto del servidor

            connectToServer();

        } catch (Exception e) {
            System.err.println("CLIENT_GENERATOR: Error al cargar propiedades del servidor: " + e.getMessage());
            updateStatus("Error de configuración", true);
            // Usar valores por defecto o mostrar error en UI
            serverIp = "127.0.0.1"; // IP de respaldo
            serverPort = 12345;    // Puerto de respaldo
            // Podrías mostrar un mensaje más prominente en la UI aquí
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverIp, serverPort);
            outToServer = new ObjectOutputStream(socket.getOutputStream());
            // inFromServer = new ObjectInputStream(socket.getInputStream()); // Descomentar si esperas respuestas

            // Enviar mensaje de registro como GENERATOR
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

            // (Opcional) Iniciar un hilo para escuchar respuestas del servidor si es necesario
            // Thread serverListenerThread = new Thread(this::listenToServer);
            // serverListenerThread.setDaemon(true);
            // serverListenerThread.start();

        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al conectar con el servidor: " + e.getMessage());
            isConnected = false;
            updateStatus("Error de conexión", true);
            // Aquí podrías intentar reconectar o mostrar un error persistente en la UI
        }
    }


    @FXML
    private void handleCaja() {
        String ticketId = String.format("C-%03d", contadorCaja);
        Ticket nuevoTicket = new Ticket(ticketId, TicketTypes.CAJA);
        // El timestamp se establece automáticamente en el constructor de Ticket

        String ticketFormateado = generarFormatoTicket(nuevoTicket, "CAJA", (int)(Math.random() * 5) + 1, -1);
        mostrarTicketLocalmente(ticketFormateado, "CAJA");
        guardarTicketEnArchivo(ticketFormateado, "caja_" + ticketId);

        // Incrementar después de usar el valor actual para el ID
        contadorCaja++;

        enviarTicketAlServidor(nuevoTicket);
    }

    @FXML
    private void handleServicio() {
        String ticketId = String.format("S-%03d", contadorServicio);
        Ticket nuevoTicket = new Ticket(ticketId, TicketTypes.SERVICIO);

        String ticketFormateado = generarFormatoTicket(nuevoTicket, "SERVICIO", -1, (int)(Math.random() * 15) + 5);
        mostrarTicketLocalmente(ticketFormateado, "SERVICIO");
        guardarTicketEnArchivo(ticketFormateado, "servicio_" + ticketId);

        // Incrementar después de usar el valor actual para el ID
        contadorServicio++;

        enviarTicketAlServidor(nuevoTicket);
    }

    private void enviarTicketAlServidor(Ticket ticket) {
        if (!isConnected || outToServer == null) {
            System.err.println("CLIENT_GENERATOR: No conectado al servidor. No se puede enviar el ticket.");
            mostrarError("No conectado al servidor. Intente reiniciar la aplicación o verifique la conexión.");
            updateStatus("Desconectado - No se pudo enviar", true);
            // Podrías intentar reconectar aquí: connectToServer();
            return;
        }

        try {
            InfoData dataParaServidor = new InfoData();
            dataParaServidor.setType(ClientTypes.GENERATOR); // El tipo de este cliente
            dataParaServidor.setName(clientName);
            dataParaServidor.setTickets(ticket); // Enviamos el objeto Ticket

            outToServer.writeObject(dataParaServidor);
            outToServer.flush();
            System.out.println("CLIENT_GENERATOR: Ticket " + ticket.getValue() + " enviado al servidor.");
            updateStatus("Ticket " + ticket.getValue() + " enviado", false);

        } catch (IOException e) {
            System.err.println("CLIENT_GENERATOR: Error al enviar ticket al servidor: " + e.getMessage());
            isConnected = false; // Marcar como desconectado en caso de error de envío
            updateStatus("Error de envío", true);
            mostrarError("Error al enviar ticket: " + e.getMessage());
            // Considerar cerrar y reabrir conexión o manejar el error de forma más robusta
            closeNetworkResources(); // Cierra para un posible intento de reconexión futuro
        }
    }


    // Modificado para tomar un objeto Ticket y detalles adicionales
    private String generarFormatoTicket(Ticket ticket, String tipoDisplay, int numeroCaja, int tiempoEstimado) {
        LocalDateTime ahora = ticket.getTimestamp(); // Usar el timestamp del objeto Ticket
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        if (tipoDisplay.equals("CAJA")) {
            return String.format(
                    "╔══════════════════════════════╗\n" +
                            "║       TICKET DE CAJA        ║\n" +
                            "╠══════════════════════════════╣\n" +
                            "║ Número: %-20s ║\n" + // Ajustado para el ID
                            "║ Fecha: %-20s ║\n" +
                            "╠══════════════════════════════╣\n" +
                            "║                              ║\n" +
                            "║  Por favor acérquese a       ║\n" +
                            "║  la caja número %d           ║\n" +
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
                    "╔══════════════════════════════╗\n" +
                            "║   TICKET SERVICIO CLIENTE   ║\n" +
                            "╠══════════════════════════════╣\n" +
                            "║ Número: %-20s ║\n" + // Ajustado para el ID
                            "║ Fecha: %-20s ║\n" +
                            "╠══════════════════════════════╣\n" +
                            "║                              ║\n" +
                            "║  Espere su turno en la       ║\n" +
                            "║  zona de espera              ║\n" +
                            "║                              ║\n" +
                            "║  Tiempo estimado: %2d min    ║\n" + // Ajustado para tiempo
                            "║                              ║\n" +
                            "╚══════════════════════════════╝\n",
                    ticket.getValue(),
                    ahora.format(formatter),
                    tiempoEstimado
            );
        }
    }

    // Renombrado para claridad
    private void mostrarTicketLocalmente(String ticketTexto, String tipo) {
        ticketArea.setText(ticketTexto);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ticket Generado - " + tipo);
        alert.setHeaderText("Su ticket ha sido generado con éxito (localmente)");

        TextArea ticketPreview = new TextArea(ticketTexto);
        ticketPreview.setEditable(false);
        ticketPreview.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        //ticketPreview.setPrefSize(380, 250); // Ajustar tamaño si es necesario

        alert.getDialogPane().setContent(ticketPreview);
        alert.getDialogPane().setPrefSize(400,300); // Para que quepa mejor

        alert.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(10));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void mostrarError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error de Red");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void guardarTicketEnArchivo(String ticketTexto, String nombreBase) {
        // El nombre del archivo ahora es más específico con el ID del ticket
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
        if (statusLabel != null) {
            statusLabel.setText("Estado: " + message);
            if (isError) {
                statusLabel.setStyle("-fx-text-fill: red;");
            } else {
                statusLabel.setStyle("-fx-text-fill: green;");
            }
        }
    }




    // Método para cerrar recursos de red (importante)
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