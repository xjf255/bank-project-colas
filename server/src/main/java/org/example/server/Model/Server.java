package org.example.server.Model;

import org.example.shared.*;
import org.javatuples.Triplet;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private ServerSocket serverSocket;
    private boolean running;
    private final int port;
    private final ExecutorService threadPool;
    private final ConcurrentHashMap<String, Triplet<ClientTypes, String, String>> clients;
    private final ConcurrentHashMap<String, Socket> clientSockets;
    private final List<Ticket> tellerList;
    private final List<Ticket> serviceList;

    // Para mantener un registro de actividades
    private final List<String> logMessages;

    // Contadores para los tickets
    private int tellerTicketCounter = 1;
    private int serviceTicketCounter = 1;

    public Server(int port) {
        this.port = port;
        this.clients = new ConcurrentHashMap<>();
        this.clientSockets = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();

        // Usamos listas sincronizadas para acceso concurrente seguro
        this.tellerList = Collections.synchronizedList(new ArrayList<>());
        this.serviceList = Collections.synchronizedList(new ArrayList<>());
        this.logMessages = Collections.synchronizedList(new ArrayList<>());

        addLog("Servidor inicializado en puerto " + port);
    }

    public void start() {
        running = true;
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Server started on port " + port);
                while (running) {
                    try {
                        System.out.println("Waiting for clients...");
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());

                        threadPool.execute(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("Error accepting client connection: " + e.getMessage());
                            addLog("Error al aceptar conexión: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Server error: " + e.getMessage());
                addLog("Error de servidor: " + e.getMessage());
            } finally {
                shutdown();
            }
        });

        serverThread.start();
    }

    private void handleClient(Socket clientSocket) {
        ObjectInputStream in = null;
        ObjectOutputStream out = null;

        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));

            String clientAddress = clientSocket.getInetAddress().getHostAddress();

            while (running) {
                try {
                    InfoData info = (InfoData) in.readObject();

                    Triplet<ClientTypes, String, String> client =
                            Triplet.with(info.getType(), clientAddress, info.getName());
                    clients.put(info.getName(), client);
                    clientSockets.put(info.getName(), clientSocket);

                    System.out.println(info.getName() + ": " + info);
                    addLog("Cliente " + info.getName() + " (" + info.getType() + ") envió datos");

                    // Procesar la información del cliente y enviar respuesta
                    InfoData response = processClientData(info);
                    if (response != null) {
                        out.writeObject(response);
                        out.flush();
                        addLog("Respuesta enviada a " + info.getName());
                    }

                    if (info.getTickets() != null) {
                        broadcastTicketUpdate(info);
                    }

                } catch (EOFException e) {
                    System.out.println("Client disconnected normally");
                    addLog("Cliente desconectado normalmente");
                    break;
                } catch (IOException e) {
                    System.err.println("Error reading from client: " + e.getMessage());
                    addLog("Error leyendo del cliente: " + e.getMessage());
                    break;
                } catch (ClassNotFoundException e) {
                    System.err.println("Error deserializing object: " + e.getMessage());
                    addLog("Error deserializando objeto: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error setting up client streams: " + e.getMessage());
            addLog("Error configurando streams del cliente: " + e.getMessage());
        } finally {
            closeClientConnection(clientSocket, in, out);
            // Remover cliente de las listas activas
            removeClientBySocket(clientSocket);
        }
    }

    private void removeClientBySocket(Socket socket) {
        String clientToRemove = null;
        for (String clientName : clientSockets.keySet()) {
            if (clientSockets.get(clientName).equals(socket)) {
                clientToRemove = clientName;
                break;
            }
        }

        if (clientToRemove != null) {
            clients.remove(clientToRemove);
            clientSockets.remove(clientToRemove);
            addLog("Cliente " + clientToRemove + " eliminado de la lista de conexiones");
        }
    }

    private void closeClientConnection(Socket clientSocket, ObjectInputStream in, ObjectOutputStream out) {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (clientSocket != null) clientSocket.close();
            System.out.println("Client connection closed");
            addLog("Conexión de cliente cerrada");
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
            addLog("Error cerrando conexión de cliente: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        threadPool.shutdown();

        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
            addLog("Error cerrando servidor: " + e.getMessage());
        }

        System.out.println("Server shutdown complete");
        addLog("Servidor apagado completamente");
    }

    public List<String> getConnectedClients() {
        return new ArrayList<>(clients.keySet());
    }

    public boolean isRunning() {
        return running;
    }

    // Obtener la información mandada por el cliente, y en base a eso se verifica qué hay que devolverle al cliente
    public InfoData processClientData(InfoData info) {
        if (info == null) {
            return null;
        }

        // Crear una copia para modificarla y devolverla como respuesta
        InfoData response = new InfoData();
        response.setName(info.getName());
        response.setType(info.getType());
        response.setIp(info.getIp());

        // Si el cliente solicita todos los tickets (por ejemplo, para la pantalla)
        if (info.isRequestAllTickets()) {
            response.setAllTellerTickets(new ArrayList<>(tellerList));
            response.setAllServiceTickets(new ArrayList<>(serviceList));
            response.setMessage("Lista de tickets enviada");
            addLog("Cliente " + info.getName() + " solicitó todos los tickets");
            return response;
        }

        // Si el cliente solicita logs (para la pantalla de logs)
        if (info.isRequestLogs()) {
            response.setLogMessages(new ArrayList<>(logMessages));
            response.setMessage("Logs enviados");
            addLog("Cliente " + info.getName() + " solicitó logs");
            return response;
        }

        Ticket currentTicket = info.getTickets();
        if (currentTicket != null) {
            // Solicita tickets (valor null indica que quiere generar un nuevo ticket)
            if (currentTicket.getValue() == null) {
                Ticket newTicket = createNewTicket(currentTicket.getType());
                response.setTickets(newTicket);
                response.setMessage("Nuevo ticket generado: " + newTicket.getValue());
                addLog("Nuevo ticket generado: " + newTicket.getValue() + " por " + info.getName());
                return response;
            }

            // Procesar ticket (cambiar estado o asignarlo a un operador)
            if (info.getType() == ClientTypes.CAJA || info.getType() == ClientTypes.SERVICIO) {
                processOperatorTicketRequest(info, response, currentTicket);
                return response;
            }

            // Eliminar ticket (ticket completado)
            if (currentTicket.isState()) {
                if (currentTicket.getType() == TicketTypes.CAJA) {
                    for (Ticket ticket : tellerList) {
                        if (ticket.getValue().equals(currentTicket.getValue())) {
                            tellerList.remove(ticket);
                            break;
                        }
                    }
                } else {
                    for (Ticket ticket : serviceList) {
                        if (ticket.getValue().equals(currentTicket.getValue())) {
                            serviceList.remove(ticket);
                            break;
                        }
                    }
                }
                response.setMessage("Ticket " + currentTicket.getValue() + " completado");
                addLog("Ticket " + currentTicket.getValue() + " completado por " + info.getName());
                return response;
            }
        }

        // Si no se procesó ninguna solicitud específica
        response.setMessage("Conexión mantenida activa");
        return response;
    }

    private Ticket createNewTicket(TicketTypes type) {
        Ticket newTicket = new Ticket();
        newTicket.setType(type);
        newTicket.setState(false); // No completado
        newTicket.setTimestamp(LocalDateTime.now());

        // Generar número de ticket según el tipo
        if (type == TicketTypes.CAJA) {
            newTicket.setValue("C" + String.format("%03d", tellerTicketCounter++));
            tellerList.add(newTicket);
        } else if (type == TicketTypes.SERVICIO) {
            newTicket.setValue("S" + String.format("%03d", serviceTicketCounter++));
            serviceList.add(newTicket);
        }

        return newTicket;
    }

    private void processOperatorTicketRequest(InfoData info, InfoData response, Ticket currentTicket) {
        List<Ticket> relevantList = (currentTicket.getType() == TicketTypes.CAJA) ? tellerList : serviceList;

        for (Ticket ticket : relevantList) {
            if (ticket.getValue().equals(currentTicket.getValue())) {
                // Asignar operador al ticket
                ticket.setOperator(info.getName());
                // Si es un ticket que se está llamando (no completado aún)
                if (!currentTicket.isState()) {
                    response.setMessage("Ticket " + currentTicket.getValue() + " asignado a " + info.getName());
                    addLog("Ticket " + currentTicket.getValue() + " llamado por " + info.getName());
                } else {
                    // Ticket completado
                    relevantList.remove(ticket);
                    response.setMessage("Ticket " + currentTicket.getValue() + " completado");
                    addLog("Ticket " + currentTicket.getValue() + " completado por " + info.getName());
                }
                break;
            }
        }

        // Devolver la lista actualizada
        if (currentTicket.getType() == TicketTypes.CAJA) {
            response.setAllTellerTickets(new ArrayList<>(tellerList));
        } else {
            response.setAllServiceTickets(new ArrayList<>(serviceList));
        }
    }

    private void broadcastTicketUpdate(InfoData info) {
        // Crear mensaje de actualización
        InfoData updateMessage = new InfoData();
        updateMessage.setAllTellerTickets(new ArrayList<>(tellerList));
        updateMessage.setAllServiceTickets(new ArrayList<>(serviceList));
        updateMessage.setMessage("Actualización de tickets");

        // Enviar a todos los clientes tipo PANTALLA
        for (String clientName : clients.keySet()) {
            Triplet<ClientTypes, String, String> clientInfo = clients.get(clientName);
            if (clientInfo.getValue0() == ClientTypes.PANTALLA && clientSockets.containsKey(clientName)) {
                try {
                    Socket clientSocket = clientSockets.get(clientName);
                    if (clientSocket.isConnected() && !clientSocket.isClosed()) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.writeObject(updateMessage);
                        out.flush();
                    }
                } catch (IOException e) {
                    System.err.println("Error broadcasting to client " + clientName + ": " + e.getMessage());
                    addLog("Error enviando actualización a " + clientName);
                }
            }
        }

        // Enviar actualización de logs a las pantallas de log
        broadcastLogs();
    }

    private void broadcastLogs() {
        InfoData logUpdate = new InfoData();
        logUpdate.setLogMessages(new ArrayList<>(logMessages));
        logUpdate.setMessage("Actualización de logs");

        for (String clientName : clients.keySet()) {
            Triplet<ClientTypes, String, String> clientInfo = clients.get(clientName);
            if (clientInfo.getValue0() == ClientTypes.LOGS && clientSockets.containsKey(clientName)) {
                try {
                    Socket clientSocket = clientSockets.get(clientName);
                    if (clientSocket.isConnected() && !clientSocket.isClosed()) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.writeObject(logUpdate);
                        out.flush();
                    }
                } catch (IOException e) {
                    System.err.println("Error broadcasting logs to client " + clientName + ": " + e.getMessage());
                }
            }
        }
    }

    private void addLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logEntry = timestamp + " - " + message;
        logMessages.add(logEntry);
        System.out.println("LOG: " + logEntry);

        // Si hay muchos logs, limitar la cantidad
        if (logMessages.size() > 1000) {
            logMessages.remove(0); // Eliminar el más antiguo
        }
    }

    // Para depuración y monitoreo
    public int getTellerTicketsCount() {
        return tellerList.size();
    }

    public int getServiceTicketsCount() {
        return serviceList.size();
    }

    public List<String> getLogs() {
        return new ArrayList<>(logMessages);
    }

    public static void main(String[] args) {
        int port;
        try {
            PropertiesInfo propertiesInfo = new PropertiesInfo();
            Properties properties = propertiesInfo.getProperties();

            port = Integer.parseInt(properties.getProperty("server.port"));
            Server server = new Server(port);
            server.start();
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}