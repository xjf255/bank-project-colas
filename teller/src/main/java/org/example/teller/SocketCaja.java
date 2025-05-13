package org.example.teller;

import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.Ticket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SocketCaja {
    private String host;
    private int port;
    private String clientName; // Nombre del operador/caja
    private Socket sc;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public SocketCaja(String host, int port, String clientName) {
        this.host = host;
        this.port = port;
        this.clientName = clientName;
    }

    public void connect() throws IOException { // Lanzar IOException para que el Controller la maneje
        try {
            sc = new Socket(host, port);
            sc.setSoTimeout(15000); // Timeout para operaciones de lectura (ej. 15 segundos)
            out = new ObjectOutputStream(sc.getOutputStream());
            out.flush(); // Importante para asegurar que el header del stream se envíe
            in = new ObjectInputStream(sc.getInputStream());

            InfoData dataCaja = new InfoData();
            dataCaja.setType(ClientTypes.CAJA); // O podría ser SERVICIO según el tipo de teller
            dataCaja.setName(this.clientName);
            dataCaja.setIp(InetAddress.getLocalHost().getHostAddress());
            out.writeObject(dataCaja);
            out.flush();
            System.out.println("SocketCaja: Conectado y datos iniciales enviados para " + this.clientName);

            // Opcional: Recibir un ACK del servidor para confirmar el registro
            // Object ack = in.readObject();
            // if (ack instanceof InfoData) {
            //     System.out.println("SocketCaja: ACK del servidor - " + ((InfoData) ack).getMessage());
            // } else {
            //     System.err.println("SocketCaja: Respuesta inesperada del servidor tras conexión.");
            // }

        } catch (IOException e) { // No atrapar ClassNotFoundException aquí si solo se envía.
            System.err.println("SocketCaja: Error de conexión - " + e.getMessage());
            closeConnection(); // Intentar limpiar si falla la conexión
            throw e; // Relanzar para que el Controller sepa que falló
        }
    }

    public void sendTicket(Ticket ticket) throws IOException {
        if (out == null || sc == null || sc.isClosed()) {
            throw new IOException("SocketCaja: No conectado o stream cerrado. Imposible enviar ticket.");
        }
        try {
            InfoData dataCaja = new InfoData();
            dataCaja.setName(this.clientName); // El servidor ya debería conocer al cliente por su conexión
            dataCaja.setType(ClientTypes.CAJA); // O el tipo correspondiente
            dataCaja.setTickets(ticket); // El ticket que se envía (puede ser de solicitud o completado)
            out.writeObject(dataCaja);
            out.flush();
            System.out.println("SocketCaja: Ticket enviado - " + ticket);
        } catch (IOException e) {
            System.err.println("SocketCaja: Error enviando ticket - " + e.getMessage());
            throw e;
        }
    }

    public Ticket receiveTicket() throws IOException, ClassNotFoundException {
        if (in == null || sc == null || sc.isClosed()) {
            throw new IOException("SocketCaja: No conectado o stream cerrado. Imposible recibir ticket.");
        }
        try {
            InfoData receivedData = (InfoData) in.readObject();
            System.out.println("SocketCaja: InfoData recibida del servidor.");
            if (receivedData.getTickets() != null) {
                System.out.println("SocketCaja: Ticket recibido - " + receivedData.getTickets());
                return receivedData.getTickets();
            } else if (receivedData.getMessage() != null) {
                // El servidor podría enviar un mensaje en lugar de un ticket (ej. "no hay tickets")
                System.out.println("SocketCaja: Mensaje del servidor - " + receivedData.getMessage());
                // Podrías lanzar una excepción personalizada o devolver null y que el controller maneje el mensaje.
                // Por ahora, si no es un ticket, devolvemos null y el controller debe verificarlo.
                return null;
            }
            return null; // No se recibió un ticket
        } catch (SocketTimeoutException e) {
            System.err.println("SocketCaja: Timeout esperando recibir ticket - " + e.getMessage());
            throw e;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("SocketCaja: Error recibiendo ticket - " + e.getMessage());
            throw e;
        }
    }

    public void closeConnection() {
        System.out.println("SocketCaja: Iniciando cierre de conexión para " + clientName);
        try {
            if (in != null) in.close();
        } catch (IOException e) {
            System.err.println("SocketCaja: Error cerrando INPUT - " + e.getMessage());
        }
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            System.err.println("SocketCaja: Error cerrando OUTPUT - " + e.getMessage());
        }
        try {
            if (sc != null && !sc.isClosed()) sc.close();
        } catch (IOException e) {
            System.err.println("SocketCaja: Error cerrando SOCKET - " + e.getMessage());
        }
        System.out.println("SocketCaja: Conexión cerrada para " + clientName);
    }

    public boolean isConnected() {
        return sc != null && sc.isConnected() && !sc.isClosed();
    }

    // El listener asíncrono no es estrictamente necesario para el flujo actual
    // de solicitud-respuesta de tickets, pero podría ser útil para otras notificaciones del servidor.
    /*
    public void startListening(Consumer<InfoData> onDataReceived, Runnable onDisconnect) {
        Thread listenerThread = new Thread(() -> {
            try {
                while (isConnected()) {
                    InfoData data = (InfoData) in.readObject(); // Bloqueante
                    if (onDataReceived != null) {
                        Platform.runLater(() -> onDataReceived.accept(data));
                    }
                }
            } catch (SocketTimeoutException ste) {
                // Ignorar, es normal si no hay datos y el socket tiene timeout
            } catch (IOException | ClassNotFoundException e) {
                if (isConnected()) { // Si aún está "conectado" pero falla la lectura, es un problema
                    System.err.println("SocketCaja Listener: Error - " + e.getMessage());
                }
            } finally {
                if (onDisconnect != null) {
                    Platform.runLater(onDisconnect);
                }
                closeConnection(); // Asegurar que todo se cierre
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    */
}