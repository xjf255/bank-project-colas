package org.example.teller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import org.example.shared.TicketTypes;
import utilities.Ticket;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ResourceBundle;

public class CajaController implements Initializable {
    private Ticket ticket= new Ticket(null, TicketTypes.CAJA);
    private SocketCaja socket;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle){
        bttnRealizar.setDisable(true);
        bttnNextTurn.setDisable(true);

        TextInputDialog ipDialog = new TextInputDialog("25.53.36.80");
        ipDialog.setTitle("Conexión al Servidor");
        ipDialog.setHeaderText("Ingrese la IP del Servidor");
        Optional<String> ipResult = ipDialog.showAndWait();

        TextInputDialog portDialog = new TextInputDialog("5000");
        portDialog.setTitle("Conexión al Servidor");
        portDialog.setHeaderText("Ingrese el Puerto del Servidor");
        Optional<String> portResult = portDialog.showAndWait();

        if(ipResult.isPresent() && portResult.isPresent()){
            try {
                String host = ipResult.get();
                Integer port = Integer.parseInt(portResult.get());
                socket = new SocketCaja(host, port);
                socket.connect();
                bttnRealizar.setDisable(false);
                bttnNextTurn.setDisable(false);
            } catch (Exception e) {
                lblStatus.setText("⛔Conexión cancelada");
                System.out.println("Error de conexión:"+e.getMessage());
            }
        }else{
            lblStatus.setText("⛔Conexión cancelada");
        }
    }

    @FXML
    private Button bttnNextTurn;

    @FXML
    private Button bttnRealizar;

    @FXML
    private Label lblStatus;

    @FXML
    private TextField txtfMontoAcreditar;

    @FXML
    private TextField txtfNumCuenta;

    @FXML
    void realizarDeposito(ActionEvent event) {
        LocalDateTime timeLog = LocalDateTime.now();
        String accountNum = txtfNumCuenta.getText();
        String deposit = txtfMontoAcreditar.getText();
        String archive = "cajaLogs.txt";
        System.out.println("DepositTime:"+timeLog); //Debug

        try(BufferedWriter save = new BufferedWriter(new FileWriter(archive, true))){
            save.write("◈"+timeLog+": AccountNum="+accountNum+", Deposit="+deposit);
            save.newLine();
        }catch (IOException e){
            lblStatus.setText("Error: "+e.getMessage());
        }
        txtfNumCuenta.setText("");
        txtfMontoAcreditar.setText("");

        ticket.setState(true);
        ticket.setOperator("CajaFabian");
        lblStatus.setText("TicketNow: "+ticket);
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        //Se envia ticket sin valor para que el servidor me mande un ticket de la cola
        Ticket needTicket = new Ticket(null, TicketTypes.CAJA);
        socket.sendTicket(needTicket);

        //Recibo el siguiente ticket de la cola que tiene el servidor
        Ticket newTicket = socket.receiveTicket();
        this.ticket = newTicket;
        lblStatus.setText("TicketNow:"+newTicket);
    }


}