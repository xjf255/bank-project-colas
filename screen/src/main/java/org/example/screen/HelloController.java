package org.example.screen;

import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.control.Label;

public class HelloController {

    @FXML
    private ImageView backgroundImage;

    @FXML
    private Pane overlayPane;

    @FXML private Label labelLibre1;
    @FXML private Label labelLibre2;
    @FXML private Label labelLibre3;
    @FXML private Label labelLibre4;
    @FXML private Label labelLibre5;
    @FXML private Label labelLibre6;
    @FXML private Label labelLibre7;
    @FXML private Label labelLibre8;
    @FXML private Label labelLibre9;
    @FXML private Label labelLibre10;
    @FXML private Label labelLibre11;
    @FXML private Label labelLibre12;

    @FXML
    public void initialize() {

        backgroundImage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {

                backgroundImage.fitWidthProperty().bind(newScene.widthProperty());
                backgroundImage.fitHeightProperty().bind(newScene.heightProperty());


                overlayPane.prefWidthProperty().bind(newScene.widthProperty());
                overlayPane.prefHeightProperty().bind(newScene.heightProperty());
            }
        });


        labelLibre1.setText("Texto libre 1");
        labelLibre2.setText("Texto libre 2");
        labelLibre3.setText("Texto libre 3");
        labelLibre4.setText("Texto libre 4");
        labelLibre5.setText("Texto libre 5");
        labelLibre6.setText("Texto libre 6");
        labelLibre7.setText("Texto libre 7");
        labelLibre8.setText("Texto libre 8");
        labelLibre9.setText("Texto libre 9");
        labelLibre10.setText("Texto libre 10");
        labelLibre11.setText("Texto libre 11");
        labelLibre12.setText("Texto libre 12");
    }
}
