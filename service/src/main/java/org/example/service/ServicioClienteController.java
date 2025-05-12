package org.example.service;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import org.example.shared.TicketTypes;
import utilities.Ticket;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class ServicioClienteController implements Initializable {
    private Ticket ticket = new Ticket(null, TicketTypes.SERVICIO);
    private SocketServicioCliente socket;

    @FXML private Button bttnNextTurn;
    @FXML private Button bttnFinalizar;
    @FXML private Label lblStatus;
    @FXML private Label lblClienteActual;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        bttnNextTurn.setDisable(true);
        bttnFinalizar.setDisable(true);

        // Configuración de conexión
        TextInputDialog ipDialog = new TextInputDialog("localhost");
        ipDialog.setTitle("Conexión al Servidor");
        ipDialog.setHeaderText("Ingrese la IP del Servidor");
        Optional<String> ipResult = ipDialog.showAndWait();

        TextInputDialog portDialog = new TextInputDialog("5000");
        portDialog.setTitle("Conexión al Servidor");
        portDialog.setHeaderText("Ingrese el Puerto del Servidor");
        Optional<String> portResult = portDialog.showAndWait();

        if(ipResult.isPresent() && portResult.isPresent()) {
            try {
                String host = ipResult.get();
                int port = Integer.parseInt(portResult.get());
                socket = new SocketServicioCliente(host, port);
                socket.connect();
                bttnNextTurn.setDisable(false);
                lblStatus.setText("✅ Conectado al servidor");
            } catch (Exception e) {
                lblStatus.setText("⛔ Error de conexión");
                System.out.println("Error de conexión: " + e.getMessage());
            }
        } else {
            lblStatus.setText("⛔ Conexión cancelada");
        }
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        // Solicitar nuevo ticket al servidor
        Ticket solicitud = new Ticket(null, TicketTypes.SERVICIO);
        socket.sendTicket(solicitud);

        // Recibir ticket del servidor
        Ticket nuevoTicket = socket.receiveTicket();
        this.ticket = nuevoTicket;

        lblClienteActual.setText("Atendiendo: " + nuevoTicket.getValue());
        lblStatus.setText("Ticket actual: " + nuevoTicket);
        bttnFinalizar.setDisable(false);
    }

    @FXML
    void finalizarServicio(ActionEvent event) {
        // Marcar ticket como completado
        ticket.setState(true);
        ticket.setOperator("ServicioCliente1");
        socket.sendTicket(ticket);

        lblClienteActual.setText("Atendiendo: Ninguno");
        lblStatus.setText("✅ Servicio finalizado para ticket: " + ticket.getValue());
        bttnFinalizar.setDisable(true);
    }
}