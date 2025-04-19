package org.example.teller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class HelloController {
    @FXML
    private Button bttnNextTurn;

    @FXML
    private Button bttnRealizar;

    @FXML
    private TextField txtfMontoAcreditar;

    @FXML
    private TextField txtfNumCuenta;

    @FXML
    void realizarDeposito(ActionEvent event) {
        txtfNumCuenta.setText("Funciona el boton de deposito");
    }

    @FXML
    void siguienteTurno(ActionEvent event) {
        txtfMontoAcreditar.setText("Funciona el boton de siguiente turno");
    }

}