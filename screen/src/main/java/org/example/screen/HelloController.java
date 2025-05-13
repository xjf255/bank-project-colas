package org.example.screen;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
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
    @FXML
    private VBox ticketsContainer;
    @FXML
    private VBox ventanillasContainer;

    private static final int MAX_ULTIMOS_LLAMADOS = 6;
    private final ObservableList<Ticket> ultimosLlamados = FXCollections.observableArrayList();
    private final Ticket[] estadoVentanillas = new Ticket[3]; // 0: Vent1, 1: Vent2, 2: Servicios

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
            serverIp = properties.getProperty("server.ip", "25.53.112.39"); // IP por defecto si no se encuentra
            serverPort = Integer.parseInt(properties.getProperty("server.port", "12345")); // Puerto por defecto
            System.out.println("[CONFIG] Servidor configurado - IP: " + serverIp + ", Puerto: " + serverPort);
        } catch (Exception e) {
            System.err.println("[ERROR] Configuración: " + e.getMessage());
            // Usar valores de fallback si la carga falla
            serverIp = "localhost"; // O una IP específica si 'localhost' no es adecuado
            serverPort = 12345;
            Platform.runLater(() -> mostrarError("ERR: CONFIG"));
        }
    }

    private void setupUI() {
        System.out.println("[UI] Configurando interfaz...");
        mainContainer.setStyle("-fx-background-color: #2c3e50; -fx-padding: 20;");

        // Cabeceras
        Label titulo = new Label("SISTEMA DE GESTIÓN DE TURNOS");
        titulo.setFont(Font.font("Arial", 36));
        titulo.setStyle("-fx-text-fill: #ecf0f1; -fx-alignment: center;");
        mainContainer.add(titulo, 0, 0, 2, 1);

        Label tituloTickets = new Label("ÚLTIMOS TURNOS LLAMADOS"); // Cambiado para reflejar mejor
        tituloTickets.setFont(Font.font("Arial", 24));
        tituloTickets.setStyle("-fx-text-fill: #ecf0f1;");
        mainContainer.add(tituloTickets, 0, 1);

        Label tituloVentanillas = new Label("ESTADO DE VENTANILLAS");
        tituloVentanillas.setFont(Font.font("Arial", 24));
        tituloVentanillas.setStyle("-fx-text-fill: #ecf0f1;");
        mainContainer.add(tituloVentanillas, 1, 1);

        mostrarEstadoInicial();
    }

    private void mostrarEstadoInicial() {
        ticketsContainer.getChildren().clear();
        ventanillasContainer.getChildren().clear();

        // Tickets
        for (int i = 0; i < MAX_ULTIMOS_LLAMADOS; i++) {
            crearLabel(ticketsContainer, "---", "#bdc3c7", 32);
        }

        // Ventanillas
        String[] nombresVentanillas = {"VENTANILLA 1", "VENTANILLA 2", "MÓDULO SERVICIOS"};
        for (String nombre : nombresVentanillas) {
            crearLabelVentanilla(nombre, "LIBRE", "#2ecc71");
        }
    }

    private void crearLabel(VBox container, String texto, String color, double fontSize) {
        Label label = new Label(texto);
        label.setFont(Font.font("Arial", fontSize));
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        container.getChildren().add(label);
    }

    private void crearLabelVentanilla(String titulo, String estado, String color) {
        Label label = new Label(titulo + ": " + estado);
        label.setFont(Font.font("Arial", 28)); // Ligeramente más pequeño para que quepa mejor
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        ventanillasContainer.getChildren().add(label);
    }

    private void connectToServer() {
        System.out.println("[NET] Conectando al servidor " + serverIp + ":" + serverPort);
        try {
            socket = new Socket(serverIp, serverPort);
            outToServer = new ObjectOutputStream(socket.getOutputStream());
            // Es importante hacer flush después de crear el ObjectOutputStream y ANTES de crear el ObjectInputStream
            // para evitar deadlocks en la negociación del stream header.
            outToServer.flush();
            inFromServer = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));


            InfoData registro = new InfoData();
            registro.setType(ClientTypes.PANTALLA);
            registro.setName("Pantalla_Principal"); // Puedes hacerlo más único si tienes múltiples pantallas
            registro.setIp(InetAddress.getLocalHost().getHostAddress());
            outToServer.writeObject(registro);
            outToServer.flush();

            System.out.println("[NET] Conexión establecida. Escuchando actualizaciones...");
            Thread listenerThread = new Thread(this::serverListener);
            listenerThread.setDaemon(true); // Para que el hilo no impida que la app se cierre
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("[ERROR] Conexión: " + e.getMessage());
            e.printStackTrace(); // Imprime el stack trace para más detalles
            Platform.runLater(() -> mostrarError("ERR: CONEXION"));
        }
    }

    private void serverListener() {
        try {
            while (isNetworkClientRunning && socket != null && !socket.isClosed()) {
                Object mensaje = inFromServer.readObject(); // Bloquea hasta que llegue un objeto
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
            if (isNetworkClientRunning) { // Solo muestra error si no estamos cerrando intencionalmente
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
        System.out.println("[DATA] Actualización recibida:");
        if (datos.getAllTellerTickets() != null) {
            System.out.println("  Tickets Caja: " + datos.getAllTellerTickets().size());
            datos.getAllTellerTickets().forEach(t ->
                    System.out.println("    " + formatTicketLog(t)));
        }
        if (datos.getAllServiceTickets() != null) {
            System.out.println("  Tickets Servicio: " + datos.getAllServiceTickets().size());
            datos.getAllServiceTickets().forEach(t ->
                    System.out.println("    " + formatTicketLog(t)));
        }
    }

    private String formatTicketLog(Ticket ticket) {
        return String.format("%s | Operador: %s | Hora: %s",
                ticket.getValue(),
                ticket.getOperator() != null ? ticket.getOperator() : "N/A",
                ticket.getTimestamp() != null ? ticket.getTimestamp().toString() : "N/A"); // Usar toString() para el timestamp
    }

    private void procesarActualizacion(InfoData datos) {
        List<Ticket> todosLosTicketsRecibidos = new ArrayList<>();
        if (datos.getAllTellerTickets() != null) {
            todosLosTicketsRecibidos.addAll(datos.getAllTellerTickets());
        }
        if (datos.getAllServiceTickets() != null) {
            todosLosTicketsRecibidos.addAll(datos.getAllServiceTickets());
        }

        // Ordenar todos los tickets recibidos por timestamp (más recientes primero)
        // Ya no filtramos por operador aquí para la lista principal de "últimos llamados"
        List<Ticket> ticketsParaMostrar = todosLosTicketsRecibidos.stream()
                // .filter(t -> t.getOperator() != null && !t.getOperator().isEmpty()) // <--- FILTRO ELIMINADO
                .sorted(Comparator.comparing(Ticket::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))) // Más recientes primero, nulos al final
                .collect(Collectors.toList());

        // Actualizar la lista observable 'ultimosLlamados' con los tickets para mostrar
        // (limitado por MAX_ULTIMOS_LLAMADOS)
        ultimosLlamados.setAll(ticketsParaMostrar.stream()
                .limit(MAX_ULTIMOS_LLAMADOS)
                .collect(Collectors.toList()));

        // Para el estado de las ventanillas, SÍ necesitamos filtrar por los que tienen operador
        // y están siendo atendidos activamente (o el último atendido por esa ventanilla).
        // Esta lógica asume que el 'operador' identifica la ventanilla.
        List<Ticket> ticketsConOperador = todosLosTicketsRecibidos.stream()
                .filter(t -> t.getOperator() != null && !t.getOperator().isEmpty())
                .sorted(Comparator.comparing(Ticket::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        actualizarEstadoVentanillas(ticketsConOperador);

        actualizarVista();
    }


    private void actualizarEstadoVentanillas(List<Ticket> ticketsAtendidos) {
        // Resetear estado
        for (int i = 0; i < estadoVentanillas.length; i++) {
            estadoVentanillas[i] = null;
        }

        // Asignar tickets a ventanillas (el más reciente por ventanilla)
        // Esta lógica asume que el campo 'operator' del ticket contiene algo como "ventanilla 1" o "ventanilla 2"
        // si está siendo atendido.
        for (Ticket ticket : ticketsAtendidos) { // Usamos la lista ya filtrada y ordenada
            if (ticket.getOperator() != null) { // Doble chequeo, aunque la lista ya debería estar filtrada
                int ventanillaIndex = detectarVentanilla(ticket.getOperator());
                if (ventanillaIndex >= 0 && ventanillaIndex < estadoVentanillas.length) {
                    if (estadoVentanillas[ventanillaIndex] == null) { // Solo asigna si la ventanilla está "libre" en esta iteración
                        estadoVentanillas[ventanillaIndex] = ticket;
                    }
                }
            }
        }
    }

    private int detectarVentanilla(String operador) {
        if (operador == null) return -1; // Añadido chequeo de nulo
        String opLowerCase = operador.toLowerCase();
        if (opLowerCase.contains("ventanilla 1")) return 0; // Coincide con índice 0
        if (opLowerCase.contains("ventanilla 2")) return 1; // Coincide con índice 1
        // Asumimos que "Módulo Servicios" podría ser asignado por un operador de tipo SERVICIO
        // y que el servidor lo marca como "ventanilla 2" o algo distinguible.
        // Si el servidor usa un nombre diferente para el operador de servicios, ajusta aquí.
        // O si "Módulo Servicios" muestra tickets de servicio en general, la lógica podría ser diferente.
        // Por ahora, si no es ventanilla 1 o 2, no lo asignamos a una específica aquí.
        // Podrías tener una tercera categoría si el servidor lo soporta.
        // Para el ejemplo de 3 ventanillas:
        // if (opLowerCase.contains("servicio") || opLowerCase.contains("modulo")) return 2; // Coincide con índice 2
        return -1; // No coincide con una ventanilla conocida por este método
    }

    private void actualizarVista() {
        ticketsContainer.getChildren().clear();
        ventanillasContainer.getChildren().clear();

        // Mostrar tickets en la lista de "Últimos Llamados"
        if (ultimosLlamados.isEmpty()) {
            for (int i = 0; i < MAX_ULTIMOS_LLAMADOS; i++) {
                crearLabel(ticketsContainer, "---", "#bdc3c7", 32);
            }
        } else {
            ultimosLlamados.forEach(ticket ->
                    // formatTicketDisplay no muestra el operador, solo tipo y número
                    crearLabel(ticketsContainer, formatTicketDisplay(ticket), "#3498db", 32));

            for (int i = ultimosLlamados.size(); i < MAX_ULTIMOS_LLAMADOS; i++) {
                crearLabel(ticketsContainer, "---", "#bdc3c7", 32);
            }
        }

        // Mostrar ventanillas
        String[] nombresVentanillas = {"VENTANILLA 1", "VENTANILLA 2", "MÓDULO SERVICIOS"};
        for (int i = 0; i < nombresVentanillas.length; i++) { // Cambiado para iterar hasta nombresVentanillas.length
            if (i < estadoVentanillas.length && estadoVentanillas[i] != null) {
                crearLabelVentanilla(nombresVentanillas[i],
                        formatTicketDisplay(estadoVentanillas[i]), "#e74c3c"); // Ocupado
            } else {
                crearLabelVentanilla(nombresVentanillas[i], "LIBRE", "#2ecc71"); // Libre
            }
        }
    }

    private String formatTicketDisplay(Ticket ticket) {
        if (ticket == null || ticket.getValue() == null) return "---";
        // Asumimos que el valor del ticket es algo como "C-001" o "S-001"
        // String[] partes = ticket.getValue().split("-");
        // String numero = partes.length > 1 ? partes[partes.length - 1] : ticket.getValue();
        // return ticket.getType() + "-" + numero;
        return ticket.getValue(); // Simplemente mostrar el valor completo del ticket
    }

    private void mostrarError(String mensaje) {
        ticketsContainer.getChildren().clear();
        ventanillasContainer.getChildren().clear();

        crearLabel(ticketsContainer, "ERROR DEL SISTEMA", "#e74c3c", 36);
        crearLabel(ticketsContainer, mensaje, "#e74c3c", 28);

        String[] nombresVentanillas = {"VENTANILLA 1", "VENTANILLA 2", "MÓDULO SERVICIOS"};
        for (String nombre : nombresVentanillas) {
            crearLabelVentanilla(nombre, "OFFLINE", "#e74c3c");
        }
    }

    public void stop() {
        System.out.println("[SHUTDOWN] Deteniendo controlador...");
        isNetworkClientRunning = false; // Señal para que el hilo listener termine
        try {
            // Cerrar streams primero, luego socket
            if (outToServer != null) try {
                outToServer.close();
            } catch (IOException e) { /* ignorable */ }
            if (inFromServer != null) try {
                inFromServer.close();
            } catch (IOException e) { /* ignorable */ }
            if (socket != null && !socket.isClosed()) try {
                socket.close();
            } catch (IOException e) { /* ignorable */ }
        } catch (Exception e) { // Un catch más genérico por si acaso
            System.err.println("[ERROR] Al cerrar conexiones: " + e.getMessage());
        }
        System.out.println("[SHUTDOWN] Recursos de red liberados.");
    }
}