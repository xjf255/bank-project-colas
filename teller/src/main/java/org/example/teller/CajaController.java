package org.example.teller;

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

import java.io.BufferedInputStream; // Añadido
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
// InputStream ya no es necesario si no cargamos el properties
import java.io.ObjectInputStream;   // Añadido
import java.io.ObjectOutputStream;  // Añadido
import java.net.InetAddress;        // Añadido
import java.net.Socket;             // Añadido
import java.net.URL;
import java.time.LocalDateTime;
// Properties ya no es necesario
import java.util.ResourceBundle;

public class CajaController implements Initializable {
    private Ticket currentTicket;

    // Miembros de red gestionados directamente
    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;

    private String ipServer;
    private Integer portServer;
    private String operatorName = "Caja_Default";

    @FXML private Button bttnNextTurn;
    @FXML private Button bttnRealizar;
    @FXML private Label lblStatus;
    @FXML private Label lblCurrentTicketValue;
    @FXML private TextField txtfNumCuenta;
    @FXML private TextField txtfMontoAcreditar;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        bttnRealizar.setDisable(true);
        bttnNextTurn.setDisable(true);

        if (lblCurrentTicketValue != null) {
            lblCurrentTicketValue.setText("N/A");
        } else {
            System.err.println("ERROR CRITICO FXML: lblCurrentTicketValue no fue inyectado. Verifica tu archivo FXML.");
        }

        loadConfigServer();
        connectToServer();
    }

    public void loadConfigServer() {
        // Usar directamente los valores por defecto
        ipServer = "25.53.112.39"; // IP de tu configuración previa
        portServer = 12345;
        operatorName = "Caja_Default"; // Nombre de operador por defecto

        // Ya no se intenta cargar desde el archivo, por lo que el mensaje de advertencia no es necesario.
        // Si deseas, puedes mantener un mensaje informativo indicando que se usan valores fijos.
        System.out.println("[CajaController INF000] Usando configuración fija/por defecto.");
        System.out.println("[CajaController INF001] Configuración: IP=" + ipServer + ", Puerto=" + portServer + ", Operador=" + operatorName);

        // El manejo de errores por no encontrar el archivo o por formato numérico ya no es necesario aquí.
        // Si hubiera un error al asignar estos valores (lo cual es improbable con valores fijos),
        // la aplicación fallaría directamente, lo cual sería un error de programación.
    }

    public void connectToServer() {
        System.out.println("[CajaController INF002] Conectando a servidor: IP=" + ipServer + ", Puerto=" + portServer);
        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("Conectando a " + ipServer + "...");
            if (bttnNextTurn != null) bttnNextTurn.setDisable(true);
            if (bttnRealizar != null) bttnRealizar.setDisable(true);
        });

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    socket = new Socket(ipServer, portServer);
                    outToServer = new ObjectOutputStream(socket.getOutputStream());
                    outToServer.flush(); // Importante antes de crear ObjectInputStream
                    inFromServer = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

                    InfoData registroCaja = new InfoData();
                    registroCaja.setType(ClientTypes.CAJA);
                    registroCaja.setName(operatorName);
                    try {
                        registroCaja.setIp(InetAddress.getLocalHost().getHostAddress());
                    } catch (Exception e) {
                        registroCaja.setIp("Unknown"); // Fallback si no se puede obtener IP
                    }
                    outToServer.writeObject(registroCaja);
                    outToServer.flush();
                    return true;
                } catch (IOException e) {
                    System.err.println("[CajaController ConnectTask] Error de conexión: " + e.getMessage());
                    shutdownNetworkResources(); // Limpiar si falla la conexión
                    throw e; // Para que se active onFailed
                }
            }
        };

        connectTask.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(connectTask.getValue())) {
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.setText("✅ Conectado como: " + operatorName);
                    if (bttnNextTurn != null) bttnNextTurn.setDisable(false);
                });
                System.out.println("[CajaController INF003] Conexión exitosa al servidor.");
            }
        });

        connectTask.setOnFailed(event -> {
            Throwable e = connectTask.getException();
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("⛔ Sin Conexión: " + (e != null ? e.getClass().getSimpleName() : "Error"));
            });
            System.err.println("[CajaController E002] Falla en Task de conexión: " + (e != null ? e.getMessage() : "Desconocido"));
            if (e != null) e.printStackTrace(); // Esto imprimirá el stack trace del error original, incluyendo el "Connection refused"
            shutdownNetworkResources(); // Asegurar limpieza en caso de fallo
        });
        new Thread(connectTask).start();
    }

    @FXML
    void realizarDeposito(ActionEvent event) {
        if (currentTicket == null || currentTicket.getValue() == null) {
            Platform.runLater(() -> { if (lblStatus != null) lblStatus.setText("Error: No hay ticket activo."); });
            return;
        }
        if (txtfNumCuenta.getText().isEmpty() || txtfMontoAcreditar.getText().isEmpty()) {
            Platform.runLater(() -> { if (lblStatus != null) lblStatus.setText("Error: Llenar campos de depósito."); });
            return;
        }

        this.currentTicket.setState(true);
        this.currentTicket.setTimestamp(LocalDateTime.now());
        this.currentTicket.setOperator(this.operatorName); // Asegurar que el operador esté en el ticket

        final Ticket ticketACompletar = this.currentTicket;

        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("Procesando depósito para: " + ticketACompletar.getValue());
            if (bttnRealizar != null) bttnRealizar.setDisable(true);
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
                dataToSend.setType(ClientTypes.CAJA);
                dataToSend.setName(operatorName);
                try {
                    dataToSend.setIp(InetAddress.getLocalHost().getHostAddress());
                } catch (Exception e) { dataToSend.setIp("Unknown"); }

                outToServer.writeObject(dataToSend);
                outToServer.flush();
                return null;
            }
        };

        taskCompletar.setOnSucceeded(e -> {
            String cuenta = txtfNumCuenta.getText();
            String monto = txtfMontoAcreditar.getText();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("log_depositos_" + operatorName + ".txt", true))) {
                bw.write(String.format("%s - Ticket: %s, Cuenta: %s, Monto: %s, Operador: %s%n",
                        LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        ticketACompletar.getValue(), cuenta, monto, operatorName));
            } catch (IOException ioex) {
                System.err.println("[CajaController E004] Error escribiendo log de depósito: " + ioex.getMessage());
            }

            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("Ticket " + ticketACompletar.getValue() + " completado.");
                if (lblCurrentTicketValue != null) lblCurrentTicketValue.setText("N/A");
                if (txtfNumCuenta != null) txtfNumCuenta.clear();
                if (txtfMontoAcreditar != null) txtfMontoAcreditar.clear();
                if (bttnNextTurn != null) bttnNextTurn.setDisable(false);
            });
            this.currentTicket = null; // Limpiar ticket actual
            System.out.println("[CajaController INF004] Ticket " + ticketACompletar.getValue() + " completado.");
        });

        taskCompletar.setOnFailed(e -> {
            Throwable ex = taskCompletar.getException();
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("⛔ Error al completar ticket " + ticketACompletar.getValue());
                if (bttnRealizar != null) bttnRealizar.setDisable(false); // Habilitar para reintentar
                if (bttnNextTurn != null) bttnNextTurn.setDisable(false);
            });
            System.err.println("[CajaController E005] Falló envío ticket completado: " + (ex != null ? ex.getMessage() : "Error"));
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
            if (bttnRealizar != null) bttnRealizar.setDisable(true); // Mientras se busca nuevo ticket
            if (lblCurrentTicketValue != null) lblCurrentTicketValue.setText("Buscando...");
        });

        Task<Ticket> taskPedirTicket = new Task<>() {
            @Override
            protected Ticket call() throws Exception {
                if (outToServer == null || inFromServer == null || socket == null || socket.isClosed()) {
                    throw new IOException("No conectado al servidor o conexión cerrada.");
                }

                Ticket ticketDeSolicitud = new Ticket(null, TicketTypes.CAJA); // Sin valor, solo tipo
                InfoData requestData = new InfoData();
                requestData.setTickets(ticketDeSolicitud);
                requestData.setType(ClientTypes.CAJA);
                requestData.setName(operatorName); // Para que el servidor sepa quién pide
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
                            receivedTicket.setOperator(operatorName);
                        }
                        return receivedTicket;
                    } else {
                        if (dataResponse.getMessage() != null) {
                            System.out.println("[CajaController] Mensaje del servidor: " + dataResponse.getMessage());
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
            this.currentTicket = ticketAsignado;

            if (this.currentTicket != null && this.currentTicket.getValue() != null) {
                Platform.runLater(() -> {
                    if (lblCurrentTicketValue != null) lblCurrentTicketValue.setText(this.currentTicket.getValue());
                    if (lblStatus != null) lblStatus.setText("Atendiendo: " + this.currentTicket.getValue() + " (Op: " + this.currentTicket.getOperator() + ")");
                    if (bttnRealizar != null) bttnRealizar.setDisable(false); // Habilitar para realizar depósito
                    if (txtfNumCuenta != null) txtfNumCuenta.clear();
                    if (txtfMontoAcreditar != null) txtfMontoAcreditar.clear();
                    if (txtfNumCuenta != null) txtfNumCuenta.requestFocus();
                });
                System.out.println("[CajaController INF005] Nuevo ticket asignado: " + this.currentTicket);
            } else {
                Platform.runLater(() -> {
                    if (lblCurrentTicketValue != null) lblCurrentTicketValue.setText("N/A");
                    if (lblStatus != null) lblStatus.setText("No hay tickets disponibles o error.");
                });
                System.out.println("[CajaController INF006] No se recibió nuevo ticket válido o no hay tickets.");
            }
            Platform.runLater(() -> { if (bttnNextTurn != null) bttnNextTurn.setDisable(false); }); // Siempre re-habilitar
        });

        taskPedirTicket.setOnFailed(e -> {
            this.currentTicket = null;
            Throwable ex = taskPedirTicket.getException();
            Platform.runLater(() -> {
                if (lblCurrentTicketValue != null) lblCurrentTicketValue.setText("N/A");
                if (lblStatus != null) lblStatus.setText("⛔ Error al obtener siguiente ticket.");
                if (bttnNextTurn != null) bttnNextTurn.setDisable(false); // Re-habilitar
            });
            System.err.println("[CajaController E006] Falló obtención siguiente ticket: " + (ex != null ? ex.getMessage() : "Error"));
            if (ex != null) ex.printStackTrace();
            if (ex instanceof IOException) {
                handleDisconnectionError("Error de red al solicitar ticket.");
            }
        });
        new Thread(taskPedirTicket).start();
    }

    private void handleDisconnectionError(String contextMessage) {
        System.err.println("[CajaController] Error de desconexión: " + contextMessage);
        Platform.runLater(() -> {
            if (lblStatus != null) lblStatus.setText("⛔ Desconexión. Revise Servidor.");
            if (bttnNextTurn != null) bttnNextTurn.setDisable(true);
            if (bttnRealizar != null) bttnRealizar.setDisable(true);
            if (lblCurrentTicketValue != null) lblCurrentTicketValue.setText("N/A");
        });
        shutdownNetworkResources(); // Cierra recursos actuales
        // Aquí podrías implementar lógica para reintentar conexión o habilitar un botón de reconexión.
    }

    public void shutdown() { // Llamado al cerrar la aplicación
        System.out.println("[CajaController] Iniciando cierre (shutdown)...");
        shutdownNetworkResources();
        System.out.println("[CajaController] Aplicación terminada.");
    }

    private void shutdownNetworkResources() {
        System.out.println("[CajaController] Cerrando recursos de red...");
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
            System.out.println("[CajaController] Recursos de red nominalmente liberados.");
        }
    }
}