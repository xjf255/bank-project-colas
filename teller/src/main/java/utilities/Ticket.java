package utilities;

import org.example.shared.TicketTypes;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Ticket implements Serializable {
    private String value;
    private TicketTypes type;
    private boolean state;
    private String operator;
    private LocalDateTime timestamp;    //CREO
    private LocalDateTime attendTime; // ATENDIO

    public Ticket() {
        this.timestamp = LocalDateTime.now();
    }

    public Ticket(String value, TicketTypes type) {
        this();
        this.value = value;
        this.type = type;
        this.state = false;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public TicketTypes getType() {
        return type;
    }

    public void setType(TicketTypes type) {
        this.type = type;
    }

    public boolean isState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
        if (operator != null && attendTime == null) {
            this.attendTime = LocalDateTime.now();
        }
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedTimestamp() {
        if (timestamp == null) return "N/A";
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public String toString() {
        return "Ticket [" + value + ", tipo=" + type +
                ", estado=" + (state ? "completado" : "pendiente") +
                ", operador=" + (operator != null ? operator : "ninguno") +
                ", creado=" + getFormattedTimestamp() + "]";
    }

    /**
     * Para comparaciones de igualdad basadas en el valor del ticket
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Ticket other = (Ticket) obj;
        return value != null && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}