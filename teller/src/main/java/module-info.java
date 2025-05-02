module org.example.teller {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires org.example.sharedmodel;

    opens org.example.teller to javafx.fxml;
    exports org.example.teller;
    exports utilities;
    opens utilities to javafx.fxml;
}