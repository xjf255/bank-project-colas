package org.example.server.Controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class HelloController {
    @FXML
    private TextArea txt_logs;

    @FXML
    protected void onHelloButtonClick() {
        txt_logs.appendText("Hello World!\n");
    }
}