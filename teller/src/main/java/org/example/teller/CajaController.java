package org.example.teller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.example.shared.TicketTypes;
import utilities.Ticket;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class CajaController {
    public Ticket ticket= new Ticket();
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
        System.out.println("Tiempo:"+timeLog); //Debug

        try(BufferedWriter save = new BufferedWriter(new FileWriter(archive, true))){
            save.write("◈"+timeLog+": AccountNum="+accountNum+", Deposit="+deposit);
            save.newLine();
        }catch (IOException e){
            lblStatus.setText("Error: "+e.getMessage());
        }
        txtfNumCuenta.setText("");
        txtfMontoAcreditar.setText("");
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        //Este ticket es el que se recibe
        //Ticket newTicket = ticketDelSocket;
        Ticket newTikcet = new Ticket("asdasd", TicketTypes.CAJA);
        newTikcet.setOperator("cajaFabian");
        newTikcet.setState(true);
        lblStatus.setText("TicketNow:"+newTikcet);
    }


}