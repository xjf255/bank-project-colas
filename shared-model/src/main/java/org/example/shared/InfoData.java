package org.example.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class InfoData implements Serializable {
    private String name;
    private String ip;
    private ClientTypes type;
    private Ticket tickets;
    private List<Ticket> allTellerTickets;
    private List<Ticket> allServiceTickets;
    private String message;
    private List<String> logMessages;
    private boolean requestAllTickets = false;
    private boolean requestLogs = false;

    public InfoData() {
        this.allTellerTickets = new ArrayList<>();
        this.allServiceTickets = new ArrayList<>();
        this.logMessages = new ArrayList<>();
    }

    public InfoData(String name, String ip, ClientTypes type) {
        this();
        this.name = name;
        this.ip = ip;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public ClientTypes getType() {
        return type;
    }

    public void setType(ClientTypes type) {
        this.type = type;
    }

    public Ticket getTickets() {
        return tickets;
    }

    public void setTickets(Ticket tickets) {
        this.tickets = tickets;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Ticket> getAllTellerTickets() {
        return allTellerTickets;
    }

    public void setAllTellerTickets(List<Ticket> allTellerTickets) {
        this.allTellerTickets = allTellerTickets;
    }

    public List<Ticket> getAllServiceTickets() {
        return allServiceTickets;
    }

    public void setAllServiceTickets(List<Ticket> allServiceTickets) {
        this.allServiceTickets = allServiceTickets;
    }

    public List<String> getLogMessages() {
        return logMessages;
    }

    public void setLogMessages(List<String> logMessages) {
        this.logMessages = logMessages;
    }

    public boolean isRequestAllTickets() {
        return requestAllTickets;
    }

    public void setRequestAllTickets(boolean requestAllTickets) {
        this.requestAllTickets = requestAllTickets;
    }

    public boolean isRequestLogs() {
        return requestLogs;
    }

    public void setRequestLogs(boolean requestLogs) {
        this.requestLogs = requestLogs;
    }

    @Override
    public String toString() {
        return "InfoData [name=" + name + ", type=" + type +
                ", ticket=" + (tickets != null ? tickets.getValue() : "null") +
                ", message=" + message + "]";
    }
}