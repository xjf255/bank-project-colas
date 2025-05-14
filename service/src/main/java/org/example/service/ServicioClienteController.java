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
    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;

    private String ipServer;
    private Integer portServer;
    private final String LOG_FILE = "servicioClienteLogs.txt";
    private final String OPERATOR_NAME = "Servicio A"; // Ejemplo: Esta instancia es "Servicio A"

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
        ipServer = "25.53.36.80"; // Cambia si tu servidor está en otra IP
        portServer = 5000;
        System.out.println("[ServicioClienteController INF001] Configuración: IP=" + ipServer + ", Puerto=" + portServer + ", Operador=" + OPERATOR_NAME);
    }

    public void connectToServer() {
        System.out.println("[ServicioClienteController INF002] Conectando a servidor: IP=" + ipServer + ", Puerto=" + portServer);
        Platform.runLater(() -> {
            lblStatus.setText("Conectando a " + ipServer + "...");
            bttnNextTurn.setDisable(true);
            bttnCrearCuenta.setDisable(true);
        });

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    socket = new Socket(ipServer, portServer);
                    outToServer = new ObjectOutputStream(socket.getOutputStream());
                    outToServer.flush();
                    inFromServer = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

                    InfoData registroServicio = new InfoData();
                    registroServicio.setType(ClientTypes.SERVICIO);
                    registroServicio.setName(OPERATOR_NAME);
                    registroServicio.setIp(InetAddress.getLocalHost().getHostAddress());
                    outToServer.writeObject(registroServicio);
                    outToServer.flush();

                    // Leer la respuesta de registro del servidor
                    Object serverResponse = inFromServer.readObject();
                    if (serverResponse instanceof InfoData) {
                        InfoData ack = (InfoData) serverResponse;
                        System.out.println("[ServicioClienteController] Respuesta de registro del servidor: " + ack.getMessage());
                    }
                    return true;
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("[ServicioClienteController ConnectTask] Error de conexión o lectura: " + e.getMessage());
                    shutdownNetworkResources();
                    throw e;
                }
            }
        };

        connectTask.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(connectTask.getValue())) {
                Platform.runLater(() -> {
                    lblStatus.setText("✅ Conectado como: " + OPERATOR_NAME);
                    bttnNextTurn.setDisable(false);
                    bttnCrearCuenta.setDisable(true);
                });
                System.out.println("[ServicioClienteController INF003] Conexión exitosa al servidor.");
            }
        });

        connectTask.setOnFailed(event -> {
            Throwable e = connectTask.getException();
            Platform.runLater(() -> lblStatus.setText("⛔ Sin Conexión: " + (e != null ? e.getClass().getSimpleName() : "Error")));
            System.err.println("[ServicioClienteController E002] Falla en Task de conexión: " + (e != null ? e.getMessage() : "Desconocido"));
            if (e != null) e.printStackTrace();
            shutdownNetworkResources();
        });
        new Thread(connectTask).start();
    }

    @FXML
    void realizarCreacionCuenta(ActionEvent event) {
        if (ticketActual == null || ticketActual.getValue() == null) {
            Platform.runLater(() -> lblStatus.setText("Error: No hay ticket activo para completar."));
            return;
        }
        if (txtfNombre.getText().isEmpty() || txtfApellidos.getText().isEmpty() || txtfDpi.getText().isEmpty()) {
            Platform.runLater(() -> lblStatus.setText("Error: Llenar todos los campos."));
            return;
        }

        this.ticketActual.setState(true);
        this.ticketActual.setTimestamp(LocalDateTime.now());
        this.ticketActual.setOperator(this.OPERATOR_NAME);

        final Ticket ticketACompletar = this.ticketActual;

        Platform.runLater(() -> {
            lblStatus.setText("Procesando creación de cuenta para: " + ticketACompletar.getValue());
            bttnCrearCuenta.setDisable(true);
        });

        Task<InfoData> taskCompletar = new Task<>() { // Cambiado a Task<InfoData>
            @Override
            protected InfoData call() throws Exception { // Devuelve InfoData
                if (outToServer == null || socket == null || socket.isClosed() || inFromServer == null) {
                    throw new IOException("No conectado al servidor, stream cerrado o no inicializado.");
                }
                InfoData dataToSend = new InfoData();
                dataToSend.setTickets(ticketACompletar);
                dataToSend.setType(ClientTypes.SERVICIO);
                dataToSend.setName(OPERATOR_NAME);
                dataToSend.setIp(InetAddress.getLocalHost().getHostAddress());

                outToServer.writeObject(dataToSend);
                outToServer.flush();

                Object serverResponse = inFromServer.readObject(); // ESPERAR RESPUESTA
                if (serverResponse instanceof InfoData) {
                    return (InfoData) serverResponse;
                } else {
                    throw new IOException("Respuesta inesperada del servidor al completar ticket.");
                }
            }
        };

        taskCompletar.setOnSucceeded(e -> {
            InfoData completionResponse = taskCompletar.getValue();
            System.out.println("[ServicioClienteController] Respuesta del servidor a la completación de " + ticketACompletar.getValue() + ": " + (completionResponse != null ? completionResponse.getMessage() : "Sin mensaje específico"));

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
                Platform.runLater(() -> lblStatus.setText("⛔ Error guardando log: " + ioex.getMessage()));
            }

            Platform.runLater(() -> {
                lblStatus.setText("Ticket " + ticketACompletar.getValue() + " procesado.");
                lblTurnoActual.setText("N/A");
                txtfNombre.clear();
                txtfApellidos.clear();
                txtfDpi.clear();
                bttnCrearCuenta.setDisable(true);
                bttnNextTurn.setDisable(false);
            });
            this.ticketActual = null;
            System.out.println("[ServicioClienteController INF004] Ticket " + ticketACompletar.getValue() + " completado localmente y confirmación recibida.");
        });

        taskCompletar.setOnFailed(e -> {
            Throwable ex = taskCompletar.getException();
            Platform.runLater(() -> {
                lblStatus.setText("⛔ Error al enviar completación de " + ticketACompletar.getValue());
                bttnCrearCuenta.setDisable(false);
            });
            if(this.ticketActual != null) {
                this.ticketActual.setState(false);
            }
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
        if (this.ticketActual != null) {
            Platform.runLater(() -> lblStatus.setText("Complete el ticket actual ("+this.ticketActual.getValue()+") antes de solicitar uno nuevo."));
            return;
        }

        Platform.runLater(() -> {
            lblStatus.setText("Solicitando siguiente ticket...");
            bttnNextTurn.setDisable(true);
            bttnCrearCuenta.setDisable(true);
            lblTurnoActual.setText("Buscando...");
        });

        Task<Ticket> taskPedirTicket = new Task<>() {
            @Override
            protected Ticket call() throws Exception {
                if (outToServer == null || inFromServer == null || socket == null || socket.isClosed()) {
                    throw new IOException("No conectado al servidor o conexión cerrada.");
                }

                Ticket ticketDeSolicitud = new Ticket(null, TicketTypes.SERVICIO);
                InfoData requestData = new InfoData();
                requestData.setTickets(ticketDeSolicitud);
                requestData.setType(ClientTypes.SERVICIO);
                requestData.setName(OPERATOR_NAME);
                requestData.setIp(InetAddress.getLocalHost().getHostAddress());

                outToServer.writeObject(requestData);
                outToServer.flush();

                Object response = inFromServer.readObject();
                if (response instanceof InfoData) {
                    InfoData dataResponse = (InfoData) response;
                    System.out.println("[ServicioClienteController] Respuesta del servidor (siguienteTurno): " + dataResponse.getMessage());
                    return dataResponse.getTickets();
                } else {
                    throw new IOException("Respuesta inesperada del servidor: " + response.getClass().getName());
                }
            }
        };

        taskPedirTicket.setOnSucceeded(e -> {
            Ticket ticketRecibidoDelServidor = taskPedirTicket.getValue();
            this.ticketActual = ticketRecibidoDelServidor;

            if (this.ticketActual != null && this.ticketActual.getValue() != null) {
                Platform.runLater(() -> {
                    lblTurnoActual.setText(this.ticketActual.getValue());
                    String statusMsg = "Atendiendo: " + this.ticketActual.getValue();
                    if(this.ticketActual.getOperator() != null && !this.ticketActual.getOperator().isEmpty()){
                        statusMsg += " (Op: " + this.ticketActual.getOperator() + ")";
                    }
                    lblStatus.setText(statusMsg);
                    bttnCrearCuenta.setDisable(false);
                    bttnNextTurn.setDisable(true);

                    txtfNombre.clear();
                    txtfApellidos.clear();
                    txtfDpi.clear();
                    txtfNombre.requestFocus();
                });
                System.out.println("[ServicioClienteController INF005] Ticket asignado/confirmado por servidor: " + this.ticketActual);
            } else {
                Platform.runLater(() -> {
                    lblTurnoActual.setText("N/A");
                    // lblStatus.setText("No hay tickets disponibles o error al obtener.");
                    bttnCrearCuenta.setDisable(true);
                    bttnNextTurn.setDisable(false);
                });
                this.ticketActual = null;
                System.out.println("[ServicioClienteController INF006] No se recibió nuevo ticket o no hay disponibles.");
            }
        });

        taskPedirTicket.setOnFailed(e -> {
            this.ticketActual = null;
            Throwable ex = taskPedirTicket.getException();
            Platform.runLater(() -> {
                lblTurnoActual.setText("N/A");
                lblStatus.setText("⛔ Error al obtener siguiente ticket.");
                bttnCrearCuenta.setDisable(true);
                bttnNextTurn.setDisable(false);
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
            lblStatus.setText("⛔ Desconexión. Revise Servidor.");
            bttnNextTurn.setDisable(true);
            bttnCrearCuenta.setDisable(true);
            lblTurnoActual.setText("N/A");
        });
        shutdownNetworkResources();
        this.ticketActual = null;
    }

    public void shutdown() {
        System.out.println("[ServicioClienteController] Iniciando cierre (shutdown)...");
        shutdownNetworkResources();
        System.out.println("[ServicioClienteController] Aplicación terminada.");
    }

    private void shutdownNetworkResources() {
        System.out.println("[ServicioClienteController] Cerrando recursos de red...");
        try {
            if (outToServer != null) {
                try { outToServer.close(); } catch (IOException ex) { /* ignorable */ }
            }
            if (inFromServer != null) {
                try { inFromServer.close(); } catch (IOException ex) { /* ignorable */ }
            }
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (IOException ex) { /* ignorable */ }
            }
        } finally {
            outToServer = null;
            inFromServer = null;
            socket = null;
            System.out.println("[ServicioClienteController] Recursos de red nominalmente liberados.");
        }
    }
}