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
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.ResourceBundle;

public class CajaController implements Initializable {
    private Ticket ticket = new Ticket(null, TicketTypes.CAJA);
    private SocketCaja socket;
    private String ipServer;
    private Integer portServer;

    PropertiesInfo propertiesInfo = new PropertiesInfo();
    Properties appProperties = propertiesInfo.getProperties();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle){
        bttnRealizar.setDisable(true);
        bttnNextTurn.setDisable(true);

        loadConfigServer();
        connectToServer();
    }

    public void loadConfigServer(){
        try {
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

            socket.sendTicket(ticket);
            Object object = socket.receiveTicket();
            //System.out.println("[INF02.5]Object received:"+object);

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

        try(BufferedWriter save = new BufferedWriter(new FileWriter(archive, true))){
            save.write("◈"+timeLog+": AccountNum="+accountNum+", Deposit="+deposit);
            save.newLine();
        }catch (IOException e){
            lblStatus.setText("⚠Error al realizar deposito: "+e.getMessage());
        }
        txtfNumCuenta.setText("");
        txtfMontoAcreditar.setText("");

        ticket.setState(true);
        ticket.setOperator(appProperties.getProperty("operator.name","FabiancitoRico"));
        lblStatus.setText("✅Ticket atendido: "+ticket.getValue());

        socket.sendTicket(this.ticket);
        System.out.println("[INF006]Ticket completed/sent:"+this.ticket);
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        //Se envia ticket sin valor para que el servidor me mande un ticket de la cola
        System.out.println("[Debug001]TicketOrigin:"+this.ticket);
        socket.requestTicket();
        System.out.println("[INF004]Data send: null");

        //Recibo el siguiente ticket de la cola que tiene el servidor
        Ticket newTicket = socket.receiveTicket();
        if(newTicket != null){
            this.ticket = newTicket;
            lblStatus.setText("⏳Atendiendo Ticket: "+newTicket.getValue());
            System.out.println("[INF005]Ticket received:"+newTicket);
        }else{
            lblStatus.setText("⚠ Error al recibir o valor nulo del Ticket.");
            System.out.println("[ERROR001]Data received:"+newTicket);
        }
    }


}