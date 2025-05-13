package org.example.server.Model;

import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.Ticket;
import org.example.shared.TicketTypes;
import org.javatuples.Triplet;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet; // Importar HashSet
import java.util.List;
import java.util.Properties;
import java.util.Set; // Importar Set
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Server {
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final int port;
    private final ExecutorService threadPool;
    private final ConcurrentHashMap<String, Triplet<ClientTypes, String, String>> clients;
    private final ConcurrentHashMap<String, Socket> clientSockets;
    private final ConcurrentHashMap<String, ObjectOutputStream> clientOutputStreams;
    private final List<Ticket> tellerList;
    private final List<Ticket> serviceList;
    private final List<String> logMessages;
    private final Set<String> completedTicketIds; // <-- NUEVO CAMPO

    private int tellerTicketCounter = 1;
    private int serviceTicketCounter = 1;
    private static final String SERVER_IDENTIFIER = "TicketSys-Server";

    public Server(int port) {
        this.port = port;
        this.clients = new ConcurrentHashMap<>();
        this.clientSockets = new ConcurrentHashMap<>();
        this.clientOutputStreams = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();
        this.tellerList = Collections.synchronizedList(new ArrayList<>());
        this.serviceList = Collections.synchronizedList(new ArrayList<>());
        this.logMessages = Collections.synchronizedList(new ArrayList<>());
        this.completedTicketIds = Collections.synchronizedSet(new HashSet<>()); // <-- INICIALIZACIÓN
        addLog("Servidor inicializado. Escuchará en el puerto " + port);
    }

    public void start() {
        running = true;
        Thread serverAcceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                addLog("Servidor iniciado y escuchando en el puerto " + port + ".");
                System.out.println("Server running on port " + port + ". Waiting for client connections...");

                while (running) {
                    try {
                        Socket acceptedSocket = serverSocket.accept();
                        addLog("Nuevo cliente conectado desde la IP: " + acceptedSocket.getInetAddress().getHostAddress());
                        System.out.println("Client connected from: " + acceptedSocket.getInetAddress().getHostAddress());
                        threadPool.execute(() -> handleClient(acceptedSocket));
                    } catch (IOException e) {
                        if (running) {
                            String errorMsg = "Error al aceptar nueva conexión de cliente: " + e.getMessage();
                            System.err.println(errorMsg);
                            addLog(errorMsg);
                        }
                    }
                }
            } catch (IOException e) {
                String errorMsg = "Error crítico al iniciar el ServerSocket en el puerto " + port + ": " + e.getMessage();
                System.err.println(errorMsg);
                addLog(errorMsg);
                e.printStackTrace();
            } finally {
                addLog("Hilo de aceptación de clientes del servidor terminado.");
            }
        });
        serverAcceptThread.setName("ServerAcceptLoopThread");
        serverAcceptThread.start();
    }

    private void handleClient(Socket clientSocket) {
        ObjectInputStream objectIn = null;
        ObjectOutputStream objectOut = null;
        String clientName = null;

        try {
            objectOut = new ObjectOutputStream(clientSocket.getOutputStream());
            objectOut.flush();
            objectIn = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            InfoData registrationMsg = (InfoData) objectIn.readObject();
            clientName = registrationMsg.getName();

            if (clientName == null || clientName.trim().isEmpty()) {
                addLog("Intento de conexión de cliente con nombre nulo/vacío. Desconectando.");
                return;
            }

            if (clientOutputStreams.containsKey(clientName)) {
                addLog("Cliente '" + clientName + "' ya conectado. Rechazando nueva conexión duplicada.");
                InfoData errorResponse = new InfoData();
                errorResponse.setMessage("Error: Ya existe una conexión activa con el nombre '" + clientName + "'.");
                errorResponse.setName(SERVER_IDENTIFIER);
                objectOut.reset();
                objectOut.writeObject(errorResponse);
                objectOut.flush();
                return;
            }

            ClientTypes clientType = registrationMsg.getType();
            String clientIp = clientSocket.getInetAddress().getHostAddress();
            Triplet<ClientTypes, String, String> clientDetails = Triplet.with(clientType, clientIp, clientName);

            clients.put(clientName, clientDetails);
            clientSockets.put(clientName, clientSocket);
            clientOutputStreams.put(clientName, objectOut);

            String logMessage = "Cliente '" + clientName + "' (Tipo: " + clientType + ", IP: " + clientIp + ") registrado exitosamente.";
            System.out.println(logMessage);
            addLog(logMessage);

            if (clientType == ClientTypes.PANTALLA) {
                sendCurrentTicketListsToSpecificClient(objectOut, clientName);
            } else {
                InfoData ackResponse = new InfoData();
                ackResponse.setMessage("Conexión y registro exitosos. Bienvenido " + clientName + "!");
                ackResponse.setName(SERVER_IDENTIFIER);
                synchronized (objectOut) {
                    objectOut.reset();
                    objectOut.writeObject(ackResponse);
                    objectOut.flush();
                }
            }

            if (clientType == ClientTypes.GENERATOR && registrationMsg.getTickets() != null) {
                addTicketFromGeneratorAndBroadcast(registrationMsg.getTickets(), clientName);
            }

            while (running && clientSocket.isConnected() && !clientSocket.isClosed()) {
                Object receivedObject = objectIn.readObject();

                if (receivedObject instanceof InfoData) {
                    InfoData clientData = (InfoData) receivedObject;
                    addLog("Mensaje recibido de '" + clientName + "': " + clientData.getMessage());

                    if (clientData.getType() == ClientTypes.GENERATOR && clientData.getTickets() != null) {
                        addTicketFromGeneratorAndBroadcast(clientData.getTickets(), clientName);
                    } else {
                        InfoData responseToClient = processClientData(clientData, clientName);
                        if (responseToClient != null) {
                            synchronized (objectOut) {
                                objectOut.reset();
                                objectOut.writeObject(responseToClient);
                                objectOut.flush();
                                addLog("Respuesta enviada a '" + clientName + "'. Mensaje: " + responseToClient.getMessage() +
                                        (responseToClient.getTickets() != null && responseToClient.getTickets().getValue() != null ? ", Ticket: " + responseToClient.getTickets().getValue() : ""));
                            }
                        }
                    }
                } else {
                    addLog("Advertencia: Objeto de tipo desconocido (" + receivedObject.getClass().getName() + ") recibido de '" + clientName + "'. Ignorando.");
                }
            }

        } catch (EOFException e) {
            String msg = "Cliente '" + (clientName != null ? clientName : clientSocket.getInetAddress().getHostAddress()) + "' cerró la conexión (EOF).";
            System.out.println(msg);
            addLog(msg);
        } catch (IOException e) {
            if (running) {
                String errorCause = (e.getMessage() == null || e.getMessage().contains("Socket closed") || e.getMessage().contains("Connection reset")) ?
                        "Conexión cerrada abruptamente" : e.getMessage();
                String msg = "Error de IOException con '" + (clientName != null ? clientName : clientSocket.getInetAddress().getHostAddress()) + "': " + errorCause;
                System.err.println(msg);
                addLog(msg);
            }
        } catch (ClassNotFoundException e) {
            String msg = "Error ClassNotFoundException de '" + (clientName != null ? clientName : clientSocket.getInetAddress().getHostAddress()) + "': " + e.getMessage();
            System.err.println(msg);
            addLog(msg);
        } finally {
            String finalClientIdentifier = (clientName != null ? clientName : clientSocket.getInetAddress().getHostAddress().toString());
            addLog("Iniciando limpieza de recursos para: " + finalClientIdentifier);

            if (clientName != null) {
                clients.remove(clientName);
                clientSockets.remove(clientName);
                clientOutputStreams.remove(clientName);
                addLog("Cliente '" + clientName + "' eliminado de las listas de activos.");
            }

            try {
                if (objectIn != null) objectIn.close();
            } catch (IOException e) {
                addLog("Error menor cerrando ObjectInputStream para " + finalClientIdentifier + ": " + e.getMessage());
            }
            try {
                if (objectOut != null) objectOut.close();
            } catch (IOException e) {
                addLog("Error menor cerrando ObjectOutputStream para " + finalClientIdentifier + ": " + e.getMessage());
            }
            try {
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) {
                addLog("Error cerrando socket para " + finalClientIdentifier + ": " + e.getMessage());
            }

            addLog("Recursos para '" + finalClientIdentifier + "' completamente limpiados.");
            System.out.println("Connection closed for: " + finalClientIdentifier);
        }
    }

    private synchronized void addTicketFromGeneratorAndBroadcast(Ticket ticket, String generatorName) {
        if (ticket == null || ticket.getValue() == null) {
            addLog("Intento de añadir ticket nulo o sin ID por generador '" + generatorName + "'.");
            return;
        }
        // Adicionalmente, no añadir si ya fue completado alguna vez.
        if (completedTicketIds.contains(ticket.getValue())) {
            addLog("Ticket '" + ticket.getValue() + "' de generador '" + generatorName + "' ya fue completado anteriormente. No fue añadido.");
            return;
        }

        boolean ticketAdded = false;
        if (ticket.getType() == TicketTypes.CAJA) {
            if (tellerList.stream().noneMatch(t -> t.getValue().equals(ticket.getValue()))) {
                tellerList.add(ticket);
                ticketAdded = true;
            }
        } else if (ticket.getType() == TicketTypes.SERVICIO) {
            if (serviceList.stream().noneMatch(t -> t.getValue().equals(ticket.getValue()))) {
                serviceList.add(ticket);
                ticketAdded = true;
            }
        }

        if (ticketAdded) {
            String msg = "Ticket '" + ticket.getValue() + "' (Tipo: " + ticket.getType() + ") de generador '" + generatorName + "' añadido a la lista.";
            System.out.println(msg);
            addLog(msg);
            broadcastTicketUpdate();
        } else {
            addLog("Ticket '" + ticket.getValue() + "' de generador '" + generatorName + "' ya existía en lista activa o tipo desconocido. No fue añadido.");
        }
    }

    private void sendCurrentTicketListsToSpecificClient(ObjectOutputStream clientOut, String screenClientName) {
        InfoData initialState = new InfoData();
        synchronized (tellerList) {
            initialState.setAllTellerTickets(new ArrayList<>(tellerList));
        }
        synchronized (serviceList) {
            initialState.setAllServiceTickets(new ArrayList<>(serviceList));
        }
        initialState.setMessage("Estado inicial de tickets.");
        initialState.setName(SERVER_IDENTIFIER);

        try {
            synchronized (clientOut) {
                clientOut.reset();
                clientOut.writeObject(initialState);
                clientOut.flush();
            }
            addLog("Estado inicial de tickets enviado a PANTALLA '" + screenClientName + "'.");
        } catch (IOException e) {
            addLog("Error al enviar estado inicial a PANTALLA '" + screenClientName + "': " + e.getMessage());
        }
    }

    public InfoData processClientData(InfoData infoFromClient, String clientNameContext) {
        if (infoFromClient == null) return null;

        String ticketInfoForLog = "";
        if (infoFromClient.getTickets() != null) {
            if (infoFromClient.getTickets().getValue() != null) {
                ticketInfoForLog = ", Ticket: " + infoFromClient.getTickets().getValue();
            } else {
                ticketInfoForLog = ", Solicitud de Siguiente Ticket (tipo: " + infoFromClient.getTickets().getType() + ")";
            }
        }
        addLog("Procesando datos de '" + clientNameContext + "'. Tipo Cliente: " + infoFromClient.getType() +
                ticketInfoForLog + ", Mensaje: " + infoFromClient.getMessage());

        InfoData responseToClient = new InfoData();
        responseToClient.setName(SERVER_IDENTIFIER);
        responseToClient.setType(ClientTypes.SERVER);

        if (infoFromClient.isRequestAllTickets()) {
            synchronized (tellerList) {
                responseToClient.setAllTellerTickets(new ArrayList<>(tellerList));
            }
            synchronized (serviceList) {
                responseToClient.setAllServiceTickets(new ArrayList<>(serviceList));
            }
            responseToClient.setMessage("Listas de tickets actuales.");
            addLog("Solicitud de todos los tickets de '" + clientNameContext + "' procesada.");
            return responseToClient;
        }

        if (infoFromClient.isRequestLogs()) {
            synchronized (logMessages) {
                responseToClient.setLogMessages(new ArrayList<>(logMessages));
            }
            responseToClient.setMessage("Registros del servidor.");
            addLog("Solicitud de logs de '" + clientNameContext + "' procesada.");
            return responseToClient;
        }

        Ticket ticketDataFromClient = infoFromClient.getTickets();

        if (ticketDataFromClient != null) {
            if (ticketDataFromClient.getValue() == null &&
                    (infoFromClient.getType() == ClientTypes.CAJA || infoFromClient.getType() == ClientTypes.SERVICIO)) {

                addLog("Operador '" + clientNameContext + "' (Tipo Cliente: " + infoFromClient.getType() +
                        ") solicita el siguiente ticket de tipo: " + ticketDataFromClient.getType());

                List<Ticket> relevantList = (ticketDataFromClient.getType() == TicketTypes.CAJA) ? tellerList : serviceList;
                Ticket ticketToProcess = null;
                boolean operatorAlreadyHadTicket = false;

                synchronized (relevantList) {
                    for (Ticket existingTicket : relevantList) {
                        if (clientNameContext.equals(existingTicket.getOperator()) && !existingTicket.isState()) {
                            operatorAlreadyHadTicket = true;
                            ticketToProcess = existingTicket;
                            addLog("Operador '" + clientNameContext + "' ya tiene el ticket activo '" + existingTicket.getValue() + "'. Reenviando.");
                            responseToClient.setTickets(ticketToProcess);
                            responseToClient.setMessage("Ya tiene asignado y activo el ticket: " + ticketToProcess.getValue());
                            break;
                        }
                    }
                }

                if (!operatorAlreadyHadTicket) {
                    synchronized (relevantList) {
                        for (Ticket ticketInQueue : relevantList) {
                            // MODIFICACIÓN: Asegurarse que no esté completado y que no tenga operador
                            if ((ticketInQueue.getOperator() == null || ticketInQueue.getOperator().isEmpty()) &&
                                    !completedTicketIds.contains(ticketInQueue.getValue())) {
                                ticketInQueue.setOperator(clientNameContext);
                                ticketInQueue.setTimestamp(LocalDateTime.now());
                                ticketInQueue.setState(false);
                                ticketToProcess = ticketInQueue;

                                addLog("Ticket '" + ticketToProcess.getValue() + "' (Tipo: " + ticketToProcess.getType() +
                                        ") asignado al operador '" + clientNameContext + "'.");
                                responseToClient.setTickets(ticketToProcess);
                                responseToClient.setMessage("Ticket '" + ticketToProcess.getValue() + "' asignado.");
                                broadcastTicketUpdate();
                                break;
                            }
                        }
                    }
                }

                if (ticketToProcess == null && !operatorAlreadyHadTicket) {
                    responseToClient.setMessage("No hay tickets disponibles en la cola para el tipo " + ticketDataFromClient.getType() + ".");
                    addLog("No hay tickets disponibles para asignar a '" + clientNameContext +
                            "' (solicitó tipo: " + ticketDataFromClient.getType() + ").");
                }
                return responseToClient;

            } else if (ticketDataFromClient.getValue() != null &&
                    (infoFromClient.getType() == ClientTypes.CAJA || infoFromClient.getType() == ClientTypes.SERVICIO)) {

                addLog("Operador '" + clientNameContext + "' (Tipo Cliente: " + infoFromClient.getType() +
                        ") actuando sobre ticket específico: '" + ticketDataFromClient.getValue() +
                        "' (Estado enviado por cliente: " + ticketDataFromClient.isState() + ").");

                boolean actionSuccess = processOperatorTicketAction(infoFromClient, ticketDataFromClient, clientNameContext);

                if (actionSuccess) {
                    responseToClient.setMessage("Acción sobre ticket '" + ticketDataFromClient.getValue() + "' procesada exitosamente.");
                } else {
                    responseToClient.setMessage("No se pudo procesar la acción sobre el ticket '" +
                            ticketDataFromClient.getValue() + "' (ej. no encontrado, ya procesado, o estado incorrecto).");
                }
                return responseToClient;
            }
        }

        responseToClient.setMessage("Solicitud de '" + clientNameContext + "' recibida. No se realizó acción específica de ticket o solicitud no reconocida.");
        addLog("Solicitud genérica o no reconocida de '" + clientNameContext + "' procesada. Mensaje: " + infoFromClient.getMessage());
        return responseToClient;
    }

    private synchronized boolean processOperatorTicketAction(InfoData operatorInfo, Ticket ticketFromOperator, String operatorName) {
        List<Ticket> relevantList = (ticketFromOperator.getType() == TicketTypes.CAJA) ? tellerList : serviceList;
        boolean foundAndActed = false;

        if (ticketFromOperator != null && ticketFromOperator.getValue() != null) { //Asegurarse que el ticket y su valor no son nulos
            addLog("SERVER-DEBUG processOperatorTicketAction: Recibido ticket '" + ticketFromOperator.getValue() +
                    "' con state=" + ticketFromOperator.isState() + " de operador " + operatorName +
                    " para tipo: " + ticketFromOperator.getType());
        } else {
            addLog("SERVER-DEBUG processOperatorTicketAction: ticketFromOperator o su valor es null para " + operatorName + ". No se puede actuar.");
            return false;
        }

        synchronized (relevantList) {
            java.util.Iterator<Ticket> iterator = relevantList.iterator();
            while (iterator.hasNext()) {
                Ticket ticketInServerList = iterator.next();
                if (ticketInServerList.getValue() != null && ticketInServerList.getValue().equals(ticketFromOperator.getValue())) {

                    addLog("SERVER-DEBUG processOperatorTicketAction: Ticket ENCONTRADO en lista server: '" + ticketInServerList.getValue() +
                            "', su state ANTES de actuar: " + ticketInServerList.isState() +
                            ", su operator ANTES de actuar: " + ticketInServerList.getOperator());

                    if (ticketFromOperator.isState()) { // Cliente envió state=true (completado)
                        addLog("SERVER-DEBUG processOperatorTicketAction: ticketFromOperator.isState() es TRUE. Intentando remover y marcar como completado...");
                        iterator.remove();
                        completedTicketIds.add(ticketInServerList.getValue()); // <--- AÑADIR A COMPLETADOS
                        addLog("Ticket '" + ticketInServerList.getValue() + "' (Tipo: " + ticketInServerList.getType() +
                                ") completado por operador '" + operatorName + "', añadido a completedTicketIds y removido de la lista activa.");
                    } else {
                        // Esta rama es si el cliente envía un ticket con state=false (ej. solo para actualizar operador sin completar)
                        // Lo cual no es el flujo actual de "completar" ticket, pero se maneja por si acaso.
                        addLog("SERVER-DEBUG processOperatorTicketAction: ticketFromOperator.isState() es FALSE. Actualizando ticket en servidor como activo.");
                        ticketInServerList.setOperator(operatorName);
                        ticketInServerList.setTimestamp(LocalDateTime.now());
                        ticketInServerList.setState(false);
                        addLog("Acción (NO completar) sobre ticket '" + ticketInServerList.getValue() +
                                "' por operador '" + operatorName + "'. Estado del ticket recibido del cliente: " + ticketFromOperator.isState() + ". Ticket en servidor se mantiene/actualiza como activo.");
                    }
                    broadcastTicketUpdate();
                    foundAndActed = true;
                    break;
                }
            }
        }

        if (!foundAndActed) {
            // Si no se encontró en la lista activa, podría ser que ya se completó y se intenta completar de nuevo.
            // O es un ticket inválido.
            if (completedTicketIds.contains(ticketFromOperator.getValue())) {
                addLog("Operador '" + operatorName + "' intentó actuar sobre ticket '" + ticketFromOperator.getValue() +
                        "' (Tipo: " + ticketFromOperator.getType() + ") que ya está en la lista de completados.");
                // Podríamos considerar esto una acción "exitosa" en el sentido de que ya está hecho.
                foundAndActed = true; // Para que el cliente reciba un mensaje de acción procesada.
            } else {
                addLog("Operador '" + operatorName + "' intentó actuar sobre ticket '" + ticketFromOperator.getValue() +
                        "' (Tipo: " + ticketFromOperator.getType() + ") pero no fue encontrado en la lista activa ni en completados. Lista activa size: " + relevantList.size());
            }
        }
        return foundAndActed;
    }

    private synchronized Ticket createNewServerGeneratedTicket(TicketTypes type) {
        Ticket newTicket = new Ticket();
        newTicket.setType(type);
        newTicket.setState(false);

        String idPrefix;
        int currentCounter;
        if (type == TicketTypes.CAJA) {
            idPrefix = "C";
            currentCounter = tellerTicketCounter++;
        } else if (type == TicketTypes.SERVICIO) {
            idPrefix = "S";
            currentCounter = serviceTicketCounter++;
        } else {
            addLog("Advertencia: Intento de generar ticket de tipo desconocido: " + type);
            newTicket.setValue("ERR" + System.currentTimeMillis());
            // No añadir a ninguna lista si el tipo es desconocido.
            return newTicket;
        }

        String ticketValue = String.format("%s%03d", idPrefix, currentCounter);
        // Asegurarse de no generar un ID que ya fue completado o está en uso (baja probabilidad con contadores crecientes).
        // Para robustez extrema, se podría verificar contra completedTicketIds y las listas activas aquí.
        newTicket.setValue(ticketValue);

        if (type == TicketTypes.CAJA) {
            tellerList.add(newTicket);
        } else { // SERVICIO
            serviceList.add(newTicket);
        }
        addLog("Servidor generó nuevo ticket: '" + newTicket.getValue() + "'.");
        return newTicket;
    }

    private void broadcastTicketUpdate() {
        InfoData updateMsg = new InfoData();
        synchronized (tellerList) {
            updateMsg.setAllTellerTickets(new ArrayList<>(tellerList));
        }
        synchronized (serviceList) {
            updateMsg.setAllServiceTickets(new ArrayList<>(serviceList));
        }
        updateMsg.setMessage("Actualización periódica del estado de tickets.");
        updateMsg.setName(SERVER_IDENTIFIER);

        List<String> screenClientNames = clientOutputStreams.keySet().stream()
                .filter(name -> clients.containsKey(name) && clients.get(name).getValue0() == ClientTypes.PANTALLA)
                .collect(Collectors.toList());

        if (!screenClientNames.isEmpty()) {
            addLog("Iniciando difusión de actualización de tickets a " + screenClientNames.size() + " pantalla(s).");
        }

        for (String screenName : screenClientNames) {
            ObjectOutputStream outToScreen = clientOutputStreams.get(screenName);
            Socket screenSocket = clientSockets.get(screenName);

            if (outToScreen != null && screenSocket != null && screenSocket.isConnected() && !screenSocket.isClosed()) {
                try {
                    synchronized (outToScreen) {
                        outToScreen.reset();
                        outToScreen.writeObject(updateMsg);
                        outToScreen.flush();
                    }
                } catch (IOException e) {
                    addLog("Error al difundir actualización de tickets a PANTALLA '" + screenName + "': " + e.getMessage() + ". El cliente puede haberse desconectado.");
                }
            } else {
                addLog("No se pudo difundir a PANTALLA '" + screenName + "': stream/socket no válido o no disponible.");
            }
        }
    }

    private void broadcastLogs() {
        InfoData logsUpdateMsg = new InfoData();
        synchronized (logMessages) {
            logsUpdateMsg.setLogMessages(new ArrayList<>(logMessages));
        }
        logsUpdateMsg.setMessage("Actualización de registros del servidor.");
        logsUpdateMsg.setName(SERVER_IDENTIFIER);

        List<String> logClientNames = clientOutputStreams.keySet().stream()
                .filter(name -> clients.containsKey(name) && clients.get(name).getValue0() == ClientTypes.LOGS)
                .collect(Collectors.toList());

        if (!logClientNames.isEmpty()) {
            addLog("Iniciando difusión de logs a " + logClientNames.size() + " cliente(s) de logs.");
        }

        for (String logClientName : logClientNames) {
            ObjectOutputStream outToLogClient = clientOutputStreams.get(logClientName);
            Socket logClientSocket = clientSockets.get(logClientName);

            if (outToLogClient != null && logClientSocket != null && logClientSocket.isConnected() && !logClientSocket.isClosed()) {
                try {
                    synchronized (outToLogClient) {
                        outToLogClient.reset();
                        outToLogClient.writeObject(logsUpdateMsg);
                        outToLogClient.flush();
                    }
                } catch (IOException e) {
                    addLog("Error al difundir logs a cliente '" + logClientName + "': " + e.getMessage());
                }
            } else {
                addLog("No se pudo difundir logs a '" + logClientName + "': stream/socket no válido o no disponible.");
            }
        }
    }

    private synchronized void addLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String logEntry = String.format("[%s] [Server] %s", timestamp, message);
        logMessages.add(logEntry);
        System.out.println(logEntry);

        final int MAX_LOG_ENTRIES = 2000;
        while (logMessages.size() > MAX_LOG_ENTRIES) {
            logMessages.remove(0);
        }
    }

    public synchronized void shutdown() {
        if (!running) {
            addLog("El servidor ya se está apagando o ya está apagado.");
            return;
        }
        addLog("Iniciando proceso de apagado del servidor...");
        System.out.println("Server shutdown sequence started...");
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                addLog("ServerSocket principal cerrado.");
            }
        } catch (IOException e) {
            addLog("Error al cerrar ServerSocket principal: " + e.getMessage());
        }

        new ArrayList<>(clientSockets.keySet()).forEach(clientName -> {
            Socket socket = clientSockets.remove(clientName);
            ObjectOutputStream oos = clientOutputStreams.remove(clientName);
            clients.remove(clientName);
            try {
                if (oos != null) oos.close();
            } catch (Exception e) { /* ignorable */ }
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) { /* ignorable */ }
            addLog("Conexión con cliente '" + clientName + "' cerrada.");
        });

        clientSockets.clear();
        clientOutputStreams.clear();
        clients.clear();
        addLog("Todos los clientes desconectados y recursos limpiados.");

        addLog("Apagando pool de hilos...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    addLog("Error: El pool de hilos no terminó correctamente.");
                }
            }
        } catch (InterruptedException ie) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        addLog("Pool de hilos detenido.");
        System.out.println("Server shutdown complete.");
        addLog("Servidor completamente apagado.");
    }

    public synchronized int getActiveClientsCount() {
        return clients.size();
    }

    public synchronized List<String> getLogSnapshot() {
        return new ArrayList<>(logMessages);
    }

    public boolean isServerRunning() {
        return running;
    }

    public static void main(String[] args) {
        int serverPortNum = 5000;
        try {
            Properties appProps = new Properties();
            try (InputStream input = Server.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (input == null) {
                    System.out.println("ADVERTENCIA: No se pudo encontrar el archivo config.properties. Usando puerto por defecto: " + serverPortNum);
                } else {
                    appProps.load(input);
                    serverPortNum = Integer.parseInt(appProps.getProperty("server.port", "5000"));
                }
            } catch (IOException |NumberFormatException e) {
                System.err.println("ADVERTENCIA: No se pudo cargar la configuración del puerto. Usando puerto por defecto: " + serverPortNum + ". Error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("ADVERTENCIA: Error general al cargar configuración. Usando puerto por defecto: " + serverPortNum + ". Error: " + e.getMessage());
        }

        final Server ticketServer = new Server(serverPortNum);
        ticketServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Detectada señal de apagado de JVM (Shutdown Hook)...");
            if (ticketServer.isServerRunning()) {
                ticketServer.shutdown();
            }
        }, "ServerShutdownHookThread"));
    }
}