package org.example.teller;

import org.example.shared.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
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
            sc.setSoTimeout(5000); //Manda un SocketTimeException de 5 segundos
            out = new ObjectOutputStream(sc.getOutputStream());
            out.flush();
            in = new ObjectInputStream(sc.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void sendTicket(Ticket ticket){
        try {
            InfoData dataCaja = new InfoData();
            dataCaja.setName("Caja_Fabian");
            dataCaja.setType(ClientTypes.CAJA);
            dataCaja.setIp(InetAddress.getLocalHost().getHostAddress());//Manda mi direccion IP local
            dataCaja.setTickets(ticket);
            out.writeObject(dataCaja);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Ticket receiveTicket(){
        try {
            InfoData receivedData = (InfoData) in.readObject();
            return receivedData.getTickets();
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

    public void starListening(){
        Thread listenerThread = new Thread(()->{
            try {
                while(!sc.isClosed()){
                    InfoData data = (InfoData) in.readObject();
                    Ticket ticket = data.getTickets();
                    System.out.println("[InfSocket002]Listener active");
                    System.out.println("[InfSocket003]Ticket recibido:"+ticket.toString());
                }
            } catch (Exception e) {
                System.out.println("[ErrorSocket001]Error:"+e.getMessage());
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}
