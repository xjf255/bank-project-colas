package org.example.service;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.example.shared.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.ResourceBundle;

public class ServicioClienteController implements Initializable {

    private Ticket ticketActual = null;
    private SocketServicioCliente socket;
    private String ipServer;
    private Integer portServer;
    private final String LOG_FILE = "servicioClienteLogs.txt";
    private final String OPERATOR_NAME = "ServicioCliente_Desk_01"; // O un nombre configurable

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
        lblTurnoActual.setText("-");
        lblStatus.setText("Cargando configuración...");

        loadConfigServer();
        if (ipServer != null && portServer != null) {
            connectToServer();
        } else {
            updateStatus("⛔ Error: Configuración del servidor no encontrada.", true);
        }
    }

    public void loadConfigServer() {
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties appProperties = propertiesInfo.getProperties();
            ipServer = appProperties.getProperty("server.ip");
            String portStr = appProperties.getProperty("server.port");

            if (ipServer == null || ipServer.trim().isEmpty() || portStr == null || portStr.trim().isEmpty()) {
                System.err.println("[E001A] IP o puerto del servidor no están configurados en application.properties.");
                updateStatus("⛔ Error: IP/Puerto del servidor no configurado.", true);
                ipServer = null; // Asegurar que no se intente conectar
                portServer = null;
                return;
            }
            portServer = Integer.parseInt(portStr);
            System.out.println("[INF001] Configuración servidor cargada: ip=" + ipServer + " port=" + portServer);
        } catch (NumberFormatException e) {
            System.err.println("[E001B] Error de formato en el puerto del servidor: " + e.getMessage());
            updateStatus("⛔ Error: Puerto del servidor inválido.", true);
            portServer = null;
        }
        catch (Exception e) {
            System.err.println("[E001C] Error cargando configuración: " + e.getMessage());
            updateStatus("⛔ Error cargando configuración del servidor.", true);
            ipServer = null;
            portServer = null;
        }
    }

    public void connectToServer() {
        updateStatus("Intentando conectar a " + ipServer + ":" + portServer + "...", false);
        new Thread(() -> {
            try {
                socket = new SocketServicioCliente(ipServer, portServer);
                socket.connect();
                Platform.runLater(() -> {
                    updateStatus("✅ Conectado al servidor.", false);
                    bttnNextTurn.setDisable(false);
                });
                System.out.println("[INF003] Conexión con servidor exitosa.");
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("⛔ Sin Conexión: " + e.getMessage(), true);
                    bttnNextTurn.setDisable(true);
                    bttnCrearCuenta.setDisable(true);
                });
                System.err.println("[E002] Error de Conexión: " + e.getMessage());
                // Podrías implementar lógica de reintento aquí
            }
        }).start();
    }

    @FXML
    void realizarCreacionCuenta(ActionEvent event) {
        LocalDateTime timeLog = LocalDateTime.now();
        String nombre = txtfNombre.getText().trim();
        String apellidos = txtfApellidos.getText().trim();
        String dpi = txtfDpi.getText().trim();

        if (nombre.isEmpty() || apellidos.isEmpty() || dpi.isEmpty()) {
            updateStatus("⚠️ Error: Nombre, Apellidos y DPI son requeridos.", true);
            return;
        }

        if (ticketActual == null || ticketActual.getValue() == null) {
            updateStatus("⚠️ Error: No hay un turno activo para procesar.", true);
            return;
        }

        if (!socket.isConnected()) {
            updateStatus("⛔ Error: No hay conexión con el servidor.", true);
            // Intentar reconectar o manejar el error
            bttnCrearCuenta.setDisable(true);
            bttnNextTurn.setDisable(true); // O intentar habilitarlo para reconectar
            connectToServer(); // Intento de reconexión simple
            return;
        }

        // Log
        String logEntry = String.format("◈%s: Ticket=%s, Nombre=%s, Apellidos=%s, DPI=%s, Operador=%s",
                timeLog.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ticketActual.getValue(), nombre, apellidos, dpi, OPERATOR_NAME);
        System.out.println("Creación Cuenta: " + logEntry);

        try (BufferedWriter save = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            save.write(logEntry);
            save.newLine();
        } catch (IOException e) {
            updateStatus("⛔ Error guardando log: " + e.getMessage(), true);
            System.err.println("[E003] Error escribiendo log: " + e.getMessage());
        }

        ticketActual.setState(true);
        ticketActual.setOperator(OPERATOR_NAME);
        ticketActual.setTimestamp(LocalDateTime.now()); // Actualiza timestamp al momento de completar

        updateStatus("Procesando ticket: " + ticketActual.getValue(), false);
        lblTurnoActual.setText("Ticket: " + ticketActual.getValue() + " (Completado)");

        new Thread(() -> {
            try {
                socket.sendTicket(this.ticketActual);
                Platform.runLater(() -> {
                    updateStatus("Ticket " + this.ticketActual.getValue() + " completado y enviado. Solicite el siguiente.", false);
                    txtfNombre.clear();
                    txtfApellidos.clear();
                    txtfDpi.clear();
                    bttnCrearCuenta.setDisable(true);
                    lblTurnoActual.setText("-");
                    this.ticketActual = null;
                });
                System.out.println("[INF006] Ticket completado enviado: " + this.ticketActual);
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    updateStatus("⛔ Error enviando ticket completado: " + e.getMessage(), true);
                    // Considerar cómo manejar este error: reintentar envío, guardar localmente, etc.
                });
                System.err.println("[E004] Error enviando ticket completado: " + e.getMessage());
            }
        }).start();
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        if (socket == null || !socket.isConnected()) {
            updateStatus("⛔ Error: No conectado al servidor. Intentando reconectar...", true);
            // Podrías tener un botón de reconectar o intentar reconectar automáticamente.
            connectToServer(); // Intento simple de reconexión
            return;
        }

        bttnNextTurn.setDisable(true); // Deshabilitar mientras se solicita
        bttnCrearCuenta.setDisable(true);
        updateStatus("Solicitando siguiente turno...", false);
        lblTurnoActual.setText("...");

        new Thread(() -> {
            try {
                Ticket needTicket = new Ticket(null, TicketTypes.SERVICIO); // Pedir ticket de SERVICIO
                socket.sendTicket(needTicket);
                System.out.println("[INF004] Solicitud de nuevo ticket enviada.");

                Ticket newTicket = socket.receiveTicket();

                Platform.runLater(() -> {
                    if (newTicket != null && newTicket.getValue() != null) {
                        this.ticketActual = newTicket;
                        lblTurnoActual.setText("Ticket Actual: " + newTicket.getValue() + " (" + newTicket.getType() + ")");
                        updateStatus("Turno " + newTicket.getValue() + " asignado. Complete los datos.", false);
                        bttnCrearCuenta.setDisable(false);
                        System.out.println("[INF005] Ticket recibido: " + newTicket.toString());
                    } else if (newTicket != null && newTicket.getValue() == null) {
                        updateStatus("ℹ️ No hay turnos pendientes en la cola de servicio.", false);
                        lblTurnoActual.setText("-");
                        this.ticketActual = null;
                        System.out.println("[INF007] No hay tickets en la cola de servicio.");
                    } else { // newTicket es null (posiblemente por timeout)
                        updateStatus("⚠️ No se pudo obtener un nuevo turno (timeout o error). Intente de nuevo.", true);
                        lblTurnoActual.setText("-");
                        this.ticketActual = null;
                        System.err.println("[E005] Error o timeout recibiendo ticket nuevo.");
                    }
                    bttnNextTurn.setDisable(false); // Habilitar para reintentar o pedir el siguiente
                });

            } catch (RuntimeException e) { // Captura errores de sendTicket o receiveTicket (como IOExceptions envueltas)
                System.err.println("[E006] Error de comunicación durante 'siguienteTurno': " + e.getMessage());
                Platform.runLater(() -> {
                    updateStatus("⛔ Error de comunicación con servidor: " + e.getMessage(), true);
                    lblTurnoActual.setText("-");
                    this.ticketActual = null;
                    bttnNextTurn.setDisable(false); // Permitir reintentar
                });
            }
        }).start();
    }

    private void updateStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            lblStatus.setText(message);
            if (isError) {
                lblStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            } else {
                lblStatus.setStyle("-fx-text-fill: black; -fx-font-weight: normal;");
            }
        });
    }

    // Método para ser llamado cuando la aplicación se cierra
    public void shutdown() {
        System.out.println("ServicioClienteController: Iniciando apagado...");
        if (socket != null) {
            socket.closeConnection();
        }
        System.out.println("ServicioClienteController: Apagado completado.");
    }
}