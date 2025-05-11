package org.example.teller;

import utilities.Ticket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SocketCaja {
    private String host;
    private int port;
    private Socket sc;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public SocketCaja(String host, int port){
        this.host = host;
        this.port = port;
    }

    public void connect(){
        try {
            sc = new Socket(host, port);
            out = new ObjectOutputStream(sc.getOutputStream());
            in = new ObjectInputStream(sc.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void sendTicket(Ticket ticket){
        try {
            out.writeObject(ticket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Ticket receiveTicket(){
        try {
            return (Ticket) in.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void closeConnection(){
        try {
            if(in != null) in.close();
        } catch (Exception e) {
            System.out.println("Error closing INPUT: "+e.getMessage());
        }

        try {
            if(out != null) out.close();
        } catch (Exception e) {
            System.out.println("Error closing OUTPUT: "+e.getMessage());
        }

        try {
            if(sc != null && !sc.isClosed()) sc.close();
        } catch (Exception e) {
            System.out.println("Error closing SOCKET: "+e.getMessage());
        }
    }

}
