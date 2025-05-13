package org.example.service;

import org.example.shared.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SocketServicioCliente {
    private String host;
    private int port;
    private Socket sc;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public SocketServicioCliente(String host, int port){
        this.host = host;
        this.port = port;
    }

    public void connect(){
        try {
            sc = new Socket(host, port);
            sc.setSoTimeout(10000); // Timeout de 10 segundos para operaciones de lectura
            out = new ObjectOutputStream(sc.getOutputStream());
            out.flush(); // Asegura que el header del stream se envíe
            in = new ObjectInputStream(sc.getInputStream());

            InfoData dataServicio = new InfoData();
            dataServicio.setType(ClientTypes.SERVICIO); // Identifica este cliente como SERVICIO
            dataServicio.setName("Servicio_Cliente_Desk_01"); // Puedes generar nombres dinámicos si es necesario
            dataServicio.setIp(InetAddress.getLocalHost().getHostAddress());
            out.writeObject(dataServicio);
            out.flush();
            System.out.println("[SocketServicio] Info inicial enviada: " + dataServicio);

        } catch (IOException e) {
            System.err.println("[SocketServicio] Error de conexión: " + e.getMessage());
            throw new RuntimeException("Error conectando al servidor: " + e.getMessage(), e);
        }
    }

    public void sendTicket(Ticket ticket){
        try {
            if (sc == null || sc.isClosed() || out == null) {
                throw new RuntimeException("Socket no conectado o stream cerrado. No se puede enviar ticket.");
            }
            InfoData dataServicio = new InfoData();
            // El tipo de cliente ya fue establecido en la conexión inicial.
            // Solo necesitamos enviar el ticket.
            dataServicio.setTickets(ticket);
            out.writeObject(dataServicio);
            out.flush();
            System.out.println("[SocketServicio] Ticket enviado: " + ticket);
        } catch (IOException e) {
            System.err.println("[SocketServicio] Error enviando ticket: " + e.getMessage());
            throw new RuntimeException("Error enviando ticket: " + e.getMessage(), e);
        }
    }

    public Ticket receiveTicket(){
        try {
            if (sc == null || sc.isClosed() || in == null) {
                throw new RuntimeException("Socket no conectado o stream cerrado. No se puede recibir ticket.");
            }
            InfoData receivedData = (InfoData) in.readObject();
            System.out.println("[SocketServicio] Datos recibidos: " + receivedData);
            return receivedData.getTickets();
        } catch (SocketTimeoutException e) {
            System.err.println("[SocketServicio] Timeout esperando respuesta del servidor.");
            // Devolver null o un ticket especial para indicar timeout en el controller
            return null;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[SocketServicio] Error recibiendo ticket: " + e.getMessage());
            // Podrías querer cerrar la conexión aquí si el error es crítico
            // closeConnection();
            throw new RuntimeException("Error recibiendo ticket: " + e.getMessage(), e);
        }
    }

    public void closeConnection(){
        System.out.println("[SocketServicio] Intentando cerrar conexión...");
        try {
            if(in != null) in.close();
        } catch (IOException e) { // Es IOException, no Exception genérica
            System.err.println("[SocketServicio] Error cerrando INPUT stream: "+e.getMessage());
        }
        in = null; // Ayuda al GC y previene reintentos sobre streams cerrados

        try {
            if(out != null) out.close();
        } catch (IOException e) { // Es IOException, no Exception genérica
            System.err.println("[SocketServicio] Error cerrando OUTPUT stream: "+e.getMessage());
        }
        out = null;

        try {
            if(sc != null && !sc.isClosed()) sc.close();
            System.out.println("[SocketServicio] Socket cerrado.");
        } catch (IOException e) { // Es IOException, no Exception genérica
            System.err.println("[SocketServicio] Error cerrando SOCKET: "+e.getMessage());
        }
        sc = null;
    }

    public boolean isConnected() {
        return sc != null && sc.isConnected() && !sc.isClosed();
    }
}