package org.example.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.Ticket;
import org.example.shared.TicketTypes;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class ServicioClienteController implements Initializable {

    private Ticket ticketActual = null;

    // Miembros de red gestionados directamente
    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;

    private String ipServer;
    private Integer portServer;
    private final String LOG_FILE = "servicioClienteLogs.txt";
    private final String OPERATOR_NAME = "Servicio_Default"; // Nombre específico para servicio

    @FXML private Button bttnCrearCuenta;
    @FXML private Button bttnNextTurn;
    @FXML private Label lblStatus;
    @FXML private Label lblTurnoActual;
    @FXML private TextField txtfNombre;
    @FXML private TextField txtfApellidos;
    @FXML private TextField txtfDpi;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        bttnCrearCuenta.setDisable(true);
        bttnNextTurn.setDisable(true);
        lblTurnoActual.setText("N/A");

        loadConfigServer();
        connectToServer();
    }

    public void loadConfigServer() {
        // Usar directamente los valores por defecto
        ipServer = "25.53.112.39"; // IP de tu configuración previa
        portServer = 5000;
        System.out.println("[ServicioClienteController INF000] Usando configuración fija/por defecto.");
        System.out.println("[ServicioClienteController INF001] Configuración: IP=" + ipServer + ", Puerto=" + portServer + ", Operador=" + OPERATOR_NAME);
    }

    public void connectToServer() {
        System.out.println("[ServicioClienteController INF002] Conectando a servidor: IP=" + ipServer + ", Puerto=" + portServer);
        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("Conectando a " + ipServer + "...");
            if (bttnNextTurn != null) bttnNextTurn.setDisable(true);
            if (bttnCrearCuenta != null) bttnCrearCuenta.setDisable(true);
        });

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    socket = new Socket(ipServer, portServer);
                    outToServer = new ObjectOutputStream(socket.getOutputStream());
                    outToServer.flush(); // Importante antes de crear ObjectInputStream
                    inFromServer = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

                    InfoData registroServicio = new InfoData();
                    registroServicio.setType(ClientTypes.SERVICIO);
                    registroServicio.setName(OPERATOR_NAME);
                    try {
                        registroServicio.setIp(InetAddress.getLocalHost().getHostAddress());
                    } catch (Exception e) {
                        registroServicio.setIp("Unknown"); // Fallback si no se puede obtener IP
                    }
                    outToServer.writeObject(registroServicio);
                    outToServer.flush();
                    return true;
                } catch (IOException e) {
                    System.err.println("[ServicioClienteController ConnectTask] Error de conexión: " + e.getMessage());
                    shutdownNetworkResources(); // Limpiar si falla la conexión
                    throw e; // Para que se active onFailed
                }
            }
        };

        connectTask.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(connectTask.getValue())) {
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.setText("✅ Conectado como: " + OPERATOR_NAME);
                    if (bttnNextTurn != null) bttnNextTurn.setDisable(false);
                });
                System.out.println("[ServicioClienteController INF003] Conexión exitosa al servidor.");
            }
        });

        connectTask.setOnFailed(event -> {
            Throwable e = connectTask.getException();
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("⛔ Sin Conexión: " + (e != null ? e.getClass().getSimpleName() : "Error"));
            });
            System.err.println("[ServicioClienteController E002] Falla en Task de conexión: " + (e != null ? e.getMessage() : "Desconocido"));
            if (e != null) e.printStackTrace(); // Esto imprimirá el stack trace del error original
            shutdownNetworkResources(); // Asegurar limpieza en caso de fallo
        });
        new Thread(connectTask).start();
    }

    @FXML
    void realizarCreacionCuenta(ActionEvent event) {
        if (ticketActual == null || ticketActual.getValue() == null) {
            Platform.runLater(() -> { if (lblStatus != null) lblStatus.setText("Error: No hay ticket activo."); });
            return;
        }
        if (txtfNombre.getText().isEmpty() || txtfApellidos.getText().isEmpty() || txtfDpi.getText().isEmpty()) {
            Platform.runLater(() -> { if (lblStatus != null) lblStatus.setText("Error: Llenar todos los campos."); });
            return;
        }

        this.ticketActual.setState(true);
        this.ticketActual.setTimestamp(LocalDateTime.now());
        this.ticketActual.setOperator(this.OPERATOR_NAME); // Asegurar que el operador esté en el ticket

        final Ticket ticketACompletar = this.ticketActual;

        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("Procesando ticket: " + ticketACompletar.getValue());
            if (bttnCrearCuenta != null) bttnCrearCuenta.setDisable(true);
            if (bttnNextTurn != null) bttnNextTurn.setDisable(true);
        });

        Task<Void> taskCompletar = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (outToServer == null || socket == null || socket.isClosed()) {
                    throw new IOException("No conectado al servidor o conexión cerrada.");
                }
                InfoData dataToSend = new InfoData();
                dataToSend.setTickets(ticketACompletar);
                dataToSend.setType(ClientTypes.SERVICIO);
                dataToSend.setName(OPERATOR_NAME);
                try {
                    dataToSend.setIp(InetAddress.getLocalHost().getHostAddress());
                } catch (Exception e) { dataToSend.setIp("Unknown"); }

                outToServer.writeObject(dataToSend);
                outToServer.flush();
                return null;
            }
        };

        taskCompletar.setOnSucceeded(e -> {
            LocalDateTime timeLog = LocalDateTime.now();
            String nombre = txtfNombre.getText().trim();
            String apellidos = txtfApellidos.getText().trim();
            String dpi = txtfDpi.getText().trim();

            String logEntry = String.format("◈%s: Ticket=%s, Nombre=%s, Apellidos=%s, DPI=%s, Operador=%s",
                    timeLog.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    ticketACompletar.getValue(), nombre, apellidos, dpi, OPERATOR_NAME);
            System.out.println("Creación Cuenta (Log): " + logEntry);

            try (BufferedWriter save = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                save.write(logEntry);
                save.newLine();
            } catch (IOException ioex) {
                System.err.println("[ServicioClienteController E003] Error escribiendo log: " + ioex.getMessage());
                Platform.runLater(() -> { if (lblStatus != null) lblStatus.setText("⛔ Error guardando log: " + ioex.getMessage()); });
            }

            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("Ticket " + ticketACompletar.getValue() + " completado.");
                if (lblTurnoActual != null) lblTurnoActual.setText("N/A");
                if (txtfNombre != null) txtfNombre.clear();
                if (txtfApellidos != null) txtfApellidos.clear();
                if (txtfDpi != null) txtfDpi.clear();
                if (bttnNextTurn != null) bttnNextTurn.setDisable(false);
            });
            this.ticketActual = null; // Limpiar ticket actual
            System.out.println("[ServicioClienteController INF004] Ticket " + ticketACompletar.getValue() + " completado.");
        });

        taskCompletar.setOnFailed(e -> {
            Throwable ex = taskCompletar.getException();
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("⛔ Error al completar ticket " + ticketACompletar.getValue());
                if (bttnCrearCuenta != null) bttnCrearCuenta.setDisable(false); // Habilitar para reintentar
                if (bttnNextTurn != null) bttnNextTurn.setDisable(false);
            });
            System.err.println("[ServicioClienteController E005] Falló envío ticket completado: " + (ex != null ? ex.getMessage() : "Error"));
            if (ex != null) ex.printStackTrace();
            if (ex instanceof IOException) {
                handleDisconnectionError("Error de red al completar ticket.");
            }
        });
        new Thread(taskCompletar).start();
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("Solicitando siguiente ticket...");
            if (bttnNextTurn != null) bttnNextTurn.setDisable(true);
            if (bttnCrearCuenta != null) bttnCrearCuenta.setDisable(true); // Mientras se busca nuevo ticket
            if (lblTurnoActual != null) lblTurnoActual.setText("Buscando...");
        });

        Task<Ticket> taskPedirTicket = new Task<>() {
            @Override
            protected Ticket call() throws Exception {
                if (outToServer == null || inFromServer == null || socket == null || socket.isClosed()) {
                    throw new IOException("No conectado al servidor o conexión cerrada.");
                }

                Ticket ticketDeSolicitud = new Ticket(null, TicketTypes.SERVICIO); // Sin valor, solo tipo
                InfoData requestData = new InfoData();
                requestData.setTickets(ticketDeSolicitud);
                requestData.setType(ClientTypes.SERVICIO);
                requestData.setName(OPERATOR_NAME); // Para que el servidor sepa quién pide
                try {
                    requestData.setIp(InetAddress.getLocalHost().getHostAddress());
                } catch (Exception e) { requestData.setIp("Unknown"); }

                outToServer.writeObject(requestData);
                outToServer.flush();

                Object response = inFromServer.readObject(); // Espera la respuesta directa
                if (response instanceof InfoData) {
                    InfoData dataResponse = (InfoData) response;
                    if (dataResponse.getTickets() != null) {
                        Ticket receivedTicket = dataResponse.getTickets();
                        // Si el servidor no asigna el operador en el ticket, lo hacemos aquí
                        if (receivedTicket.getOperator() == null || receivedTicket.getOperator().isEmpty()) {
                            receivedTicket.setOperator(OPERATOR_NAME);
                        }
                        return receivedTicket;
                    } else {
                        if (dataResponse.getMessage() != null) {
                            System.out.println("[ServicioClienteController] Mensaje del servidor: " + dataResponse.getMessage());
                        }
                        return null; // No se asignó ticket
                    }
                } else {
                    throw new IOException("Respuesta inesperada del servidor: " + response.getClass().getName());
                }
            }
        };

        taskPedirTicket.setOnSucceeded(e -> {
            Ticket ticketAsignado = taskPedirTicket.getValue();
            this.ticketActual = ticketAsignado;

            if (this.ticketActual != null && this.ticketActual.getValue() != null) {
                Platform.runLater(() -> {
                    if (lblTurnoActual != null) lblTurnoActual.setText(this.ticketActual.getValue());
                    if (lblStatus != null) lblStatus.setText("Atendiendo: " + this.ticketActual.getValue() + " (Op: " + this.ticketActual.getOperator() + ")");
                    if (bttnCrearCuenta != null) bttnCrearCuenta.setDisable(false); // Habilitar para realizar la acción
                    if (txtfNombre != null) txtfNombre.clear();
                    if (txtfApellidos != null) txtfApellidos.clear();
                    if (txtfDpi != null) txtfDpi.clear();
                    if (txtfNombre != null) txtfNombre.requestFocus();
                });
                System.out.println("[ServicioClienteController INF005] Nuevo ticket asignado: " + this.ticketActual);
            } else {
                Platform.runLater(() -> {
                    if (lblTurnoActual != null) lblTurnoActual.setText("N/A");
                    if (lblStatus != null) lblStatus.setText("No hay tickets disponibles o error.");
                });
                System.out.println("[ServicioClienteController INF006] No se recibió nuevo ticket válido o no hay tickets.");
            }
            Platform.runLater(() -> { if (bttnNextTurn != null) bttnNextTurn.setDisable(false); }); // Siempre re-habilitar
        });

        taskPedirTicket.setOnFailed(e -> {
            this.ticketActual = null;
            Throwable ex = taskPedirTicket.getException();
            Platform.runLater(() -> {
                if (lblTurnoActual != null) lblTurnoActual.setText("N/A");
                if (lblStatus != null) lblStatus.setText("⛔ Error al obtener siguiente ticket.");
                if (bttnNextTurn != null) bttnNextTurn.setDisable(false); // Re-habilitar
            });
            System.err.println("[ServicioClienteController E006] Falló obtención siguiente ticket: " + (ex != null ? ex.getMessage() : "Error"));
            if (ex != null) ex.printStackTrace();
            if (ex instanceof IOException) {
                handleDisconnectionError("Error de red al solicitar ticket.");
            }
        });
        new Thread(taskPedirTicket).start();
    }

    private void handleDisconnectionError(String contextMessage) {
        System.err.println("[ServicioClienteController] Error de desconexión: " + contextMessage);
        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("⛔ Desconexión. Revise Servidor.");
            if (bttnNextTurn != null) bttnNextTurn.setDisable(true);
            if (bttnCrearCuenta != null) bttnCrearCuenta.setDisable(true);
            if (lblTurnoActual != null) lblTurnoActual.setText("N/A");
        });
        shutdownNetworkResources(); // Cierra recursos actuales
        // Aquí podrías implementar lógica para reintentar conexión o habilitar un botón de reconexión.
    }

    public void shutdown() { // Llamado al cerrar la aplicación
        System.out.println("[ServicioClienteController] Iniciando cierre (shutdown)...");
        shutdownNetworkResources();
        System.out.println("[ServicioClienteController] Aplicación terminada.");
    }

    private void shutdownNetworkResources() {
        System.out.println("[ServicioClienteController] Cerrando recursos de red...");
        try {
            if (outToServer != null) {
                try { outToServer.close(); } catch (IOException e) { /* ignorar */ }
            }
            if (inFromServer != null) {
                try { inFromServer.close(); } catch (IOException e) { /* ignorar */ }
            }
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (IOException e) { /* ignorar */ }
            }
        } finally {
            outToServer = null;
            inFromServer = null;
            socket = null;
            System.out.println("[ServicioClienteController] Recursos de red nominalmente liberados.");
        }
    }
}