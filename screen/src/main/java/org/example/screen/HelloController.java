package org.example.screen;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.PropertiesInfo;
import org.example.shared.Ticket;

public class HelloController {
    @FXML
    private GridPane mainContainer;
    private VBox ticketsContainer;
    private ScrollPane scrollPane;

    private final ObservableList<Ticket> ultimosLlamados = FXCollections.observableArrayList();

    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;
    private volatile boolean isNetworkClientRunning = true;

    private String serverIp;
    private int serverPort;

    @FXML
    public void initialize() {
        System.out.println("[INIT] Inicializando controlador de pantalla...");
        setupUI();
        loadServerConfig();
        connectToServer();
    }

    private void loadServerConfig() {
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties properties = propertiesInfo.getProperties();
            serverIp = properties.getProperty("server.ip", "localhost");
            serverPort = Integer.parseInt(properties.getProperty("server.port", "5000"));
            System.out.println("[CONFIG] Servidor configurado - IP: " + serverIp + ", Puerto: " + serverPort);
        } catch (Exception e) {
            System.err.println("[ERROR] Configuración: " + e.getMessage());
            serverIp = "localhost";
            serverPort = 5000;
            Platform.runLater(() -> mostrarError("ERR: CONFIG"));
        }
    }

    private void setupUI() {
        System.out.println("[UI] Configurando interfaz...");
        String backgroundColor = "#2c3e50";
        mainContainer.setStyle("-fx-background-color: " + backgroundColor + "; -fx-padding: 20;");

        Label titulo = new Label("SISTEMA DE GESTIÓN DE TURNOS");
        titulo.setFont(Font.font("Arial", 36));
        titulo.setStyle("-fx-text-fill: #ecf0f1; -fx-alignment: center;");
        mainContainer.add(titulo, 0, 0, 2, 1);

        Label tituloTickets = new Label("ÚLTIMOS TURNOS LLAMADOS");
        tituloTickets.setFont(Font.font("Arial", 24));
        tituloTickets.setStyle("-fx-text-fill: #ecf0f1;");
        GridPane.setColumnSpan(tituloTickets, 2);
        mainContainer.add(tituloTickets, 0, 1);

        ticketsContainer = new VBox();
        scrollPane = new ScrollPane(ticketsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + backgroundColor + "; -fx-background-color: " + backgroundColor + ";");
        GridPane.setColumnSpan(scrollPane, 2);
        mainContainer.add(scrollPane, 0, 2);

        mostrarEstadoInicial();
    }

    private void mostrarEstadoInicial() {
        if (ticketsContainer != null) {
            ticketsContainer.getChildren().clear();
        }
        System.out.println("[UI] Estado inicial mostrado (contenedor de tickets vacío).");
    }

    private void crearLabel(VBox container, String texto, String color, double fontSize) {
        Label label = new Label(texto);
        label.setFont(Font.font("Arial", fontSize));
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        container.getChildren().add(label);
    }

    private void connectToServer() {
        System.out.println("[NET] Conectando al servidor " + serverIp + ":" + serverPort);
        try {
            socket = new Socket(serverIp, serverPort);
            outToServer = new ObjectOutputStream(socket.getOutputStream());
            outToServer.flush();
            inFromServer = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            InfoData registro = new InfoData();
            registro.setType(ClientTypes.PANTALLA);
            registro.setName("Pantalla_Principal");
            registro.setIp(InetAddress.getLocalHost().getHostAddress());
            outToServer.writeObject(registro);
            outToServer.flush();

            System.out.println("[NET] Conexión establecida. Escuchando actualizaciones...");
            Thread listenerThread = new Thread(this::serverListener);
            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("[ERROR] Conexión: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> mostrarError("ERR: CONEXION"));
        }
    }

    private void serverListener() {
        try {
            while (isNetworkClientRunning && socket != null && !socket.isClosed()) {
                Object mensaje = inFromServer.readObject();
                if (mensaje instanceof InfoData) {
                    InfoData datos = (InfoData) mensaje;
                    logDatosRecibidos(datos);
                    Platform.runLater(() -> procesarActualizacion(datos));
                } else {
                    System.out.println("[NET] Mensaje de tipo desconocido recibido: " + (mensaje != null ? mensaje.getClass().getName() : "null"));
                }
            }
        } catch (java.io.EOFException e) {
            System.out.println("[NET] Conexión cerrada por el servidor (EOF).");
            Platform.runLater(() -> mostrarError("ERR: DESCONECTADO"));
        } catch (java.net.SocketException e) {
            if (isNetworkClientRunning) {
                System.err.println("[ERROR] Socket: " + e.getMessage());
                Platform.runLater(() -> mostrarError("ERR: SOCKET"));
            }
        } catch (Exception e) {
            if (isNetworkClientRunning) {
                System.err.println("[ERROR] Comunicación: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> mostrarError("ERR: COMUNICACION"));
            }
        } finally {
            System.out.println("[NET] Hilo listener terminado.");
        }
    }

    private void logDatosRecibidos(InfoData datos) {
        System.out.println("[DATA] Actualización recibida del servidor:");
        if (datos.getAllTellerTickets() != null) {
            System.out.println("  Tickets Caja (" + datos.getAllTellerTickets().size() + "):");
            datos.getAllTellerTickets().forEach(t ->
                    System.out.println("    " + formatTicketLog(t)));
        }
        if (datos.getAllServiceTickets() != null) {
            System.out.println("  Tickets Servicio (" + datos.getAllServiceTickets().size() + "):");
            datos.getAllServiceTickets().forEach(t ->
                    System.out.println("    " + formatTicketLog(t)));
        }
    }

    private String formatTicketLog(Ticket ticket) {
        return String.format("Ticket ID: %s | Operador: %s | Timestamp: %s | Estado (boolean): %b",
                ticket.getValue(),
                (ticket.getOperator() != null && !ticket.getOperator().trim().isEmpty()) ? ticket.getOperator().trim() : "N/A (nulo o vacío)",
                ticket.getTimestamp() != null ? ticket.getTimestamp().toString() : "N/A",
                ticket.isState()
        );
    }

    private void procesarActualizacion(InfoData datos) {
        List<Ticket> todosLosTicketsRecibidos = new ArrayList<>();
        if (datos.getAllTellerTickets() != null) {
            todosLosTicketsRecibidos.addAll(datos.getAllTellerTickets());
        }
        if (datos.getAllServiceTickets() != null) {
            todosLosTicketsRecibidos.addAll(datos.getAllServiceTickets());
        }

        List<Ticket> ticketsParaMostrar = todosLosTicketsRecibidos.stream()
                .sorted(Comparator.comparing(Ticket::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        ultimosLlamados.setAll(ticketsParaMostrar);
        actualizarVista();
    }

    private String formatTicketForUltimosLlamados(Ticket ticket) {
        if (ticket == null || ticket.getValue() == null) {
            return "Dato inválido";
        }
        String ticketValue = ticket.getValue();
        String operatorInfo = ticket.getOperator();

        // Línea de depuración para verificar qué información llega:
        System.out.println("[HelloController DEBUG] Formateando ticket: " + ticketValue +
                ", Operator (ticket.getOperator()): '" + operatorInfo +
                "', State (ticket.isState()): " + ticket.isState());

        if (operatorInfo != null && !operatorInfo.trim().isEmpty()) {
            return ticketValue + " - Atendiendo en " + operatorInfo.trim();
        } else {
            return ticketValue + " - En espera";
        }
    }

    private void actualizarVista() {
        ticketsContainer.getChildren().clear();

        if (ultimosLlamados.isEmpty()) {
            System.out.println("[UI] Vista actualizada: No hay tickets para mostrar.");
        } else {
            ultimosLlamados.forEach(ticket ->
                    crearLabel(ticketsContainer, formatTicketForUltimosLlamados(ticket), "#3498db", 32));
            System.out.println("[UI] Vista actualizada con " + ultimosLlamados.size() + " tickets.");
        }
    }

    private void mostrarError(String mensaje) {
        if (ticketsContainer == null) {
            System.err.println("[ERROR UI] ticketsContainer es nulo al intentar mostrar error: " + mensaje);
            return;
        }
        ticketsContainer.getChildren().clear();
        crearLabel(ticketsContainer, "ERROR DEL SISTEMA", "#e74c3c", 36);
        crearLabel(ticketsContainer, mensaje, "#e74c3c", 28);
        System.out.println("[UI] Mostrando error: " + mensaje);
    }

    public void stop() {
        System.out.println("[SHUTDOWN] Deteniendo controlador...");
        isNetworkClientRunning = false;
        try {
            if (outToServer != null) try { outToServer.close(); } catch (IOException e) { /* ignorable */ }
            if (inFromServer != null) try { inFromServer.close(); } catch (IOException e) { /* ignorable */ }
            if (socket != null && !socket.isClosed()) try { socket.close(); } catch (IOException e) { /* ignorable */ }
        } catch (Exception e) {
            System.err.println("[ERROR] Al cerrar conexiones: " + e.getMessage());
        }
        System.out.println("[SHUTDOWN] Recursos de red liberados.");
    }
}