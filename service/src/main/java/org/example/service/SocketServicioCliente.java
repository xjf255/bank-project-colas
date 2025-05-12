package org.example.service;

import utilities.Ticket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SocketServicioCliente {
    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public SocketServicioCliente(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    public void sendTicket(Ticket ticket) {
        try {
            out.writeObject(ticket);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error enviando ticket", e);
        }
    }

    public Ticket receiveTicket() {
        try {
            return (Ticket) in.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Error recibiendo ticket", e);
        }
    }

    public void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("Error cerrando conexión: " + e.getMessage());
        }
    }
}