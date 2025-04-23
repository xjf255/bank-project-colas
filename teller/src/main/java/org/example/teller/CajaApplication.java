package org.example.teller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import utilities.Paths;

import java.io.IOException;

public class CajaApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        /*
        FXMLLoader fxmlLoader = new FXMLLoader(CajaApplication.class.getResource("servicioCaja.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 558.0, 475.0);
        Scene scene1 = new Scene(fxmlLoader.load());
        stage.setTitle("Servicio Caja");
        stage.setScene(scene1);
        stage.show();
        //*/
        //*
        AnchorPane load = FXMLLoader.load(getClass().getResource(Paths.SERVICIO_CAJA));
        Scene scene = new Scene(load);
        stage.setTitle("Servicio Caja");
        stage.setScene(scene);
        stage.show();
        //*/
    }

    public static void main(String[] args) {
        launch();
    }
}