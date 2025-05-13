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

public class CajaController implements Initializable {
    private Ticket currentTicket;
    private Socket socket;
    private ObjectOutputStream outToServer;
    private ObjectInputStream inFromServer;

    private String ipServer;
    private Integer portServer;
    private String operatorName = "Ventanilla 1";

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
        lblCurrentTicketValue.setText("N/A");
        loadConfigServer();
        connectToServer();
    }

    public void loadConfigServer() {
        ipServer = "localhost";
        portServer = 5000;
        System.out.println("[CajaController INF001] Configuración: IP=" + ipServer + ", Puerto=" + portServer + ", Operador=" + operatorName);
    }

    public void connectToServer() {
        System.out.println("[CajaController INF002] Conectando a servidor: IP=" + ipServer + ", Puerto=" + portServer);
        Platform.runLater(() -> {
            lblStatus.setText("Conectando a " + ipServer + "...");
            bttnNextTurn.setDisable(true);
            bttnRealizar.setDisable(true);
        });

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    socket = new Socket(ipServer, portServer);
                    outToServer = new ObjectOutputStream(socket.getOutputStream());
                    outToServer.flush();
                    inFromServer = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

                    InfoData registroCaja = new InfoData();
                    registroCaja.setType(ClientTypes.CAJA);
                    registroCaja.setName(operatorName);
                    registroCaja.setIp(InetAddress.getLocalHost().getHostAddress());
                    outToServer.writeObject(registroCaja);
                    outToServer.flush();

                    // Leer la respuesta de registro del servidor
                    Object serverResponse = inFromServer.readObject();
                    if (serverResponse instanceof InfoData) {
                        InfoData ack = (InfoData) serverResponse;
                        System.out.println("[CajaController] Respuesta de registro del servidor: " + ack.getMessage());
                    }
                    return true;
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("[CajaController ConnectTask] Error de conexión o lectura: " + e.getMessage());
                    shutdownNetworkResources();
                    throw e;
                }
            }
        };

        connectTask.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(connectTask.getValue())) {
                Platform.runLater(() -> {
                    lblStatus.setText("✅ Conectado como: " + operatorName);
                    bttnNextTurn.setDisable(false);
                    bttnRealizar.setDisable(true);
                });
                System.out.println("[CajaController INF003] Conexión exitosa al servidor.");
            }
        });

        connectTask.setOnFailed(event -> {
            Throwable e = connectTask.getException();
            Platform.runLater(() -> lblStatus.setText("⛔ Sin Conexión: " + (e != null ? e.getClass().getSimpleName() : "Error")));
            System.err.println("[CajaController E002] Falla en Task de conexión: " + (e != null ? e.getMessage() : "Desconocido"));
            if (e != null) e.printStackTrace();
            shutdownNetworkResources();
        });
        new Thread(connectTask).start();
    }

    @FXML
    void realizarDeposito(ActionEvent event) {
        if (currentTicket == null || currentTicket.getValue() == null) {
            Platform.runLater(() -> lblStatus.setText("Error: No hay ticket activo para completar."));
            return;
        }
        if (txtfNumCuenta.getText().isEmpty() || txtfMontoAcreditar.getText().isEmpty()) {
            Platform.runLater(() -> lblStatus.setText("Error: Llenar campos de depósito."));
            return;
        }

        this.currentTicket.setState(true);
        this.currentTicket.setTimestamp(LocalDateTime.now());
        this.currentTicket.setOperator(this.operatorName);

        final Ticket ticketACompletar = this.currentTicket;

        Platform.runLater(() -> {
            lblStatus.setText("Procesando depósito para: " + ticketACompletar.getValue());
            bttnRealizar.setDisable(true);
        });

        Task<InfoData> taskCompletar = new Task<>() { // Cambiado a Task<InfoData>
            @Override
            protected InfoData call() throws Exception { // Devuelve InfoData
                if (outToServer == null || socket == null || socket.isClosed() || inFromServer == null) {
                    throw new IOException("No conectado al servidor, stream cerrado o no inicializado.");
                }
                InfoData dataToSend = new InfoData();
                dataToSend.setTickets(ticketACompletar);
                dataToSend.setType(ClientTypes.CAJA);
                dataToSend.setName(operatorName);
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
            System.out.println("[CajaController] Respuesta del servidor a la completación de " + ticketACompletar.getValue() + ": " + (completionResponse != null ? completionResponse.getMessage() : "Sin mensaje específico"));

            String cuenta = txtfNumCuenta.getText();
            String monto = txtfMontoAcreditar.getText();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("log_depositos_" + operatorName.replace(" ", "_") + ".txt", true))) {
                bw.write(String.format("%s - Ticket: %s, Cuenta: %s, Monto: %s, Operador: %s%n",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        ticketACompletar.getValue(), cuenta, monto, operatorName));
            } catch (IOException ioex) {
                System.err.println("[CajaController E004] Error escribiendo log de depósito: " + ioex.getMessage());
            }

            Platform.runLater(() -> {
                lblStatus.setText("Ticket " + ticketACompletar.getValue() + " procesado.");
                lblCurrentTicketValue.setText("N/A");
                txtfNumCuenta.clear();
                txtfMontoAcreditar.clear();
                bttnRealizar.setDisable(true);
                bttnNextTurn.setDisable(false);
            });
            this.currentTicket = null;
            System.out.println("[CajaController INF004] Ticket " + ticketACompletar.getValue() + " completado localmente y confirmación recibida.");
        });

        taskCompletar.setOnFailed(e -> {
            Throwable ex = taskCompletar.getException();
            Platform.runLater(() -> {
                lblStatus.setText("⛔ Error al enviar completación de " + ticketACompletar.getValue());
                bttnRealizar.setDisable(false);
            });
            if(this.currentTicket != null) { // Si aún tenemos el ticket, revertir el estado local si es necesario
                this.currentTicket.setState(false);
            }
            System.err.println("[CajaController E005] Falló envío de ticket completado: " + (ex != null ? ex.getMessage() : "Error"));
            if (ex != null) ex.printStackTrace();
            if (ex instanceof IOException) {
                handleDisconnectionError("Error de red al completar ticket.");
            }
        });
        new Thread(taskCompletar).start();
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        if (this.currentTicket != null) {
            Platform.runLater(() -> lblStatus.setText("Complete el ticket actual ("+this.currentTicket.getValue()+") antes de solicitar uno nuevo."));
            return;
        }

        Platform.runLater(() -> {
            lblStatus.setText("Solicitando siguiente ticket...");
            bttnNextTurn.setDisable(true);
            bttnRealizar.setDisable(true);
            lblCurrentTicketValue.setText("Buscando...");
        });

        Task<Ticket> taskPedirTicket = new Task<>() {
            @Override
            protected Ticket call() throws Exception {
                if (outToServer == null || inFromServer == null || socket == null || socket.isClosed()) {
                    throw new IOException("No conectado al servidor o conexión cerrada.");
                }

                Ticket ticketDeSolicitud = new Ticket(null, TicketTypes.CAJA);
                InfoData requestData = new InfoData();
                requestData.setTickets(ticketDeSolicitud);
                requestData.setType(ClientTypes.CAJA);
                requestData.setName(operatorName);
                requestData.setIp(InetAddress.getLocalHost().getHostAddress());

                outToServer.writeObject(requestData);
                outToServer.flush();

                Object response = inFromServer.readObject();
                if (response instanceof InfoData) {
                    InfoData dataResponse = (InfoData) response;
                    System.out.println("[CajaController] Respuesta del servidor (siguienteTurno): " + dataResponse.getMessage());
                    return dataResponse.getTickets();
                } else {
                    throw new IOException("Respuesta inesperada del servidor: " + response.getClass().getName());
                }
            }
        };

        taskPedirTicket.setOnSucceeded(e -> {
            Ticket ticketRecibidoDelServidor = taskPedirTicket.getValue();
            this.currentTicket = ticketRecibidoDelServidor;

            if (this.currentTicket != null && this.currentTicket.getValue() != null) {
                Platform.runLater(() -> {
                    lblCurrentTicketValue.setText(this.currentTicket.getValue());
                    String statusMsg = "Atendiendo: " + this.currentTicket.getValue();
                    if(this.currentTicket.getOperator() != null && !this.currentTicket.getOperator().isEmpty()){
                        statusMsg += " (Op: " + this.currentTicket.getOperator() + ")";
                    }
                    lblStatus.setText(statusMsg);
                    bttnRealizar.setDisable(false);
                    bttnNextTurn.setDisable(true);

                    txtfNumCuenta.clear();
                    txtfMontoAcreditar.clear();
                    txtfNumCuenta.requestFocus();
                });
                System.out.println("[CajaController INF005] Ticket asignado/confirmado por servidor: " + this.currentTicket);
            } else {
                Platform.runLater(() -> {
                    lblCurrentTicketValue.setText("N/A");
                    // El mensaje del servidor ya fue logueado en call()
                    // lblStatus.setText("No hay tickets disponibles o error al obtener.");
                    bttnRealizar.setDisable(true);
                    bttnNextTurn.setDisable(false);
                });
                this.currentTicket = null;
                System.out.println("[CajaController INF006] No se recibió nuevo ticket o no hay disponibles.");
            }
        });

        taskPedirTicket.setOnFailed(e -> {
            this.currentTicket = null;
            Throwable ex = taskPedirTicket.getException();
            Platform.runLater(() -> {
                lblCurrentTicketValue.setText("N/A");
                lblStatus.setText("⛔ Error al obtener siguiente ticket.");
                bttnRealizar.setDisable(true);
                bttnNextTurn.setDisable(false);
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
            lblStatus.setText("⛔ Desconexión. Revise Servidor.");
            bttnNextTurn.setDisable(true);
            bttnRealizar.setDisable(true);
            lblCurrentTicketValue.setText("N/A");
        });
        shutdownNetworkResources();
        this.currentTicket = null;
    }

    public void shutdown() {
        System.out.println("[CajaController] Iniciando cierre (shutdown)...");
        shutdownNetworkResources();
        System.out.println("[CajaController] Aplicación terminada.");
    }

    private void shutdownNetworkResources() {
        System.out.println("[CajaController] Cerrando recursos de red...");
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
            System.out.println("[CajaController] Recursos de red nominalmente liberados.");
        }
    }
}