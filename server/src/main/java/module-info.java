module org.example.server {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires org.example.sharedmodel;
    requires javatuples;

    opens org.example.server to javafx.fxml;
    exports org.example.server;
    exports org.example.server.Controller;
    opens org.example.server.Controller to javafx.fxml;
}