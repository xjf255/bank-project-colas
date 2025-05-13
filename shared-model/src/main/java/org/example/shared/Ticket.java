package org.example.shared;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Ticket implements Serializable {
    private static final long serialVersionUID = 20240512L; // Buena práctica poner un serialVersionUID
    private String value;
    private TicketTypes type;
    private boolean state; // false = pendiente/llamado, true = completado
    private String operator;
    private LocalDateTime timestamp;    // Hora de CREACION del ticket
    private LocalDateTime attendTime; // Hora en que fue ATENDIDO/ASIGNADO por un operador

    public Ticket() {
        this.timestamp = LocalDateTime.now(); // Hora de creación
        this.state = false; // Por defecto, pendiente
    }

    public Ticket(String value, TicketTypes type) {
        this(); // Llama al constructor por defecto para establecer el timestamp y estado inicial
        this.value = value;
        this.type = type;
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
        // Si se asigna un operador y el ticket aún no tiene un attendTime,
        // se establece la hora actual como la hora de atención/asignación.
        if (operator != null && this.attendTime == null) {
            this.attendTime = LocalDateTime.now();
        }
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getAttendTime() {
        return attendTime;
    }

    public void setAttendTime(LocalDateTime attendTime) {
        this.attendTime = attendTime;
    }

    public String getFormattedTimestamp() {
        if (timestamp == null) return "N/A";
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public String getFormattedAttendTime() {
        if (attendTime == null) return "N/A";
        return attendTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public String toString() {
        return "Ticket{" +
                "value='" + value + '\'' +
                ", type=" + type +
                ", state=" + (state ? "completado" : "pendiente/atendiendo") +
                ", operator='" + (operator != null ? operator : "N/A") + '\'' +
                ", timestamp=" + getFormattedTimestamp() +
                ", attendTime=" + getFormattedAttendTime() +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Ticket ticket = (Ticket) obj;
        return value != null ? value.equals(ticket.value) : ticket.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}