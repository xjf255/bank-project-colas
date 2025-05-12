package org.example.teller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.example.shared.TicketTypes;

import org.example.shared.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.ResourceBundle;

public class CajaController implements Initializable {
    private Ticket ticket = new Ticket(null, TicketTypes.CAJA);
    private SocketCaja socket;
    private String ipServer;
    private Integer portServer;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle){
        bttnRealizar.setDisable(true);
        bttnNextTurn.setDisable(true);

        loadConfigServer();
        connectToServer();
        /*
        if(socket != null){
            socket.starListening();
        }
        //*/
    }

    public void loadConfigServer(){
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties appProperties = propertiesInfo.getProperties();
            ipServer = appProperties.getProperty("server.ip","127.0.0.1");
            portServer = Integer.parseInt(appProperties.getProperty("server.port","12345"));
            System.out.println("[INF001]Load server config: ip="+ipServer+" port="+portServer);
        } catch (Exception e) {
            System.out.println("[E001]Load error: ip="+ipServer+" port="+portServer+". More:"+e.getMessage());
        }
    }

    public void connectToServer(){
        System.out.println("[INF002]Connecting to server with ip="+ipServer+" port="+portServer);
        try {
            socket = new SocketCaja(ipServer, portServer);
            socket.connect();
            bttnRealizar.setDisable(false);
            bttnNextTurn.setDisable(false);

            System.out.println("[INF003]Server connect successful");
        } catch (Exception e) {
            lblStatus.setText("⛔Sin Conexión");
            System.out.println("[E002]Connection error:"+e.getMessage());
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
            socket.closeConnection();
        }
        txtfNumCuenta.setText("");
        txtfMontoAcreditar.setText("");

        ticket.setState(true);
        ticket.setOperator("CajaFabian");
        lblStatus.setText("TicketNow: "+ticket);

        socket.sendTicket(this.ticket);
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        //Se envia ticket sin valor para que el servidor me mande un ticket de la cola
        System.out.println("[Debug]TicketOrigin:"+this.ticket);
        Ticket needTicket = new Ticket(null, TicketTypes.CAJA);
        socket.sendTicket(needTicket);
        System.out.println("[INF004]Data send:"+needTicket.toString());

        //Recibo el siguiente ticket de la cola que tiene el servidor
        Ticket newTicket = socket.receiveTicket();
        System.out.println("[INF005]Data received:"+newTicket.toString());
        this.ticket = newTicket;
        lblStatus.setText("TicketNow:"+newTicket);
    }


}