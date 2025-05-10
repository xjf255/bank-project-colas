package org.example.server.Controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import org.example.server.Model.Logs;
import org.example.server.Model.Server;
import org.example.shared.PropertiesInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;

public class Controller {
    @FXML
    private TextArea txt_logs;

    protected void printInfo(String text) {
        txt_logs.appendText(text + "\n");
    }

    @FXML
    void initialize(){
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties properties = propertiesInfo.getProperties();
            int port = Integer.parseInt(properties.getProperty("server.port"));
            InetAddress localhost = InetAddress.getLocalHost();
            Logs connectionLogs = new Logs("25.53.112.39", port);
            List<String> logs = connectionLogs.start();
            writeLogs(logs);
        } catch (UnknownHostException e) {
            System.err.println("Could not determine local host: " + e.getMessage());
        }
    }

    private void writeLogs(List<String> logs) {
        for (String log : logs) {
            printInfo(log);
        }
    }
}