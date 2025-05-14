module org.example.screen {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;
    requires org.example.sharedmodel;

    opens org.example.screen to javafx.fxml;
    exports org.example.screen;
}