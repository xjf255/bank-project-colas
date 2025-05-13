package org.example.teller;

import org.example.shared.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Properties;

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
            in = new ObjectInputStream(sc.getInputStream());
            out = new ObjectOutputStream(sc.getOutputStream());
            out.flush();

            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties appProperties = propertiesInfo.getProperties();

            InfoData dataCaja = new InfoData();
            dataCaja.setType(ClientTypes.CAJA);
            dataCaja.setName(appProperties.getProperty("operator.name","FabianPapasote"));
            dataCaja.setIp(InetAddress.getLocalHost().getHostAddress());
            out.writeObject(dataCaja);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void requestTicket(){
        try {
            InfoData request = new InfoData();
            request.setType(ClientTypes.CAJA);
            request.setTickets(null);
            request.setMessage("[DebugSocket04]I need a Ticket");
            out.writeObject(request);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendTicket(Ticket ticket){
        try {
            InfoData dataCaja = new InfoData();
            dataCaja.setType(ClientTypes.CAJA); //El server necesita que le enviemos que tipo de
            dataCaja.setTickets(ticket);
            //dataCaja.setMessage("[DebugSocket03]I need a new Ticket Daddy!!");
            out.writeObject(dataCaja);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Ticket receiveTicket(){
        try {
            InfoData receivedData = (InfoData) in.readObject();
            //System.out.println("[DebugSocket001]Objeto recibido del server:"+ receivedData);
            //System.out.println("[DebugSocket002]Data ticket:"+ receivedData.getTickets());
            return receivedData.getTickets();
        } catch (Exception e) {
            return new Ticket("ErrorTicketSocket",TicketTypes.CAJA);
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
            while (!sc.isClosed()){
                try {
                    InfoData data = (InfoData) in.readObject();
                    Ticket ticket = data.getTickets();
                    System.out.println("[InfSocket002]Listener active");
                    System.out.println("[InfSocket003]Ticket recibido:"+ticket.toString());
                }catch (SocketTimeoutException e){
                    System.out.println("[ErrorSocket002]Error:"+e.getMessage());
                } catch (Exception e) {
                    System.out.println("[ErrorSocket001]Error:"+e.getMessage());
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}
