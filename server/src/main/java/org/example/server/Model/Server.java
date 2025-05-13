package org.example.server.Model; // Asegúrate que el package es correcto

import org.example.shared.ClientTypes;
import org.example.shared.InfoData;
import org.example.shared.Ticket;
import org.example.shared.TicketTypes;
import org.example.shared.PropertiesInfo; // Asumido
import org.javatuples.Triplet; // Asegúrate de tener esta dependencia si la usas

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
import java.util.stream.Collectors;

public class Server {
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final int port;
    private final ExecutorService threadPool;
    // Almacena nombre_cliente -> (TipoCliente, IP, OutputStream para ESE cliente)
    private final ConcurrentHashMap<String, ClientConnection> activeClients;

    private final List<Ticket> tellerList;    // Para TicketTypes.CAJA
    private final List<Ticket> serviceList;   // Para TicketTypes.SERVICIO
    private final List<String> logMessages;

    // Contadores para generar tickets si el servidor también puede generarlos
    // (Actualmente la lógica asume que los tickets son creados por GENERATORs o las CAJAS piden de una cola existente)
    // private int tellerTicketCounter = 1;
    // private int serviceTicketCounter = 1;
    private static final String SERVER_IDENTIFIER = "TicketSys-Server";

    // Clase interna para manejar la información de conexión del cliente
    private static class ClientConnection {
        ClientTypes type;
        String ip;
        Socket socket;
        ObjectOutputStream objectOut;
        // ObjectInputStream objectIn; // Podría estar aquí si la lectura no es en un bucle en handleClient

        ClientConnection(ClientTypes type, String ip, Socket socket, ObjectOutputStream objectOut) {
            this.type = type;
            this.ip = ip;
            this.socket = socket;
            this.objectOut = objectOut;
        }
    }

    public Server(int port) {
        this.port = port;
        this.activeClients = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool(); // O newFixedThreadPool(N)
        this.tellerList = Collections.synchronizedList(new ArrayList<>());
        this.serviceList = Collections.synchronizedList(new ArrayList<>());
        this.logMessages = Collections.synchronizedList(new ArrayList<>());
        addLog("Servidor inicializado. Puerto: " + port);
    }

    public void start() {
        running = true;
        Thread serverAcceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                addLog("Servidor iniciado. Escuchando en puerto " + port + ".");
                System.out.println("Servidor operativo en puerto " + port + ". Esperando conexiones...");

                while (running) {
                    try {
                        Socket acceptedSocket = serverSocket.accept(); // Bloqueante
                        // No ejecutar handleClient directamente aquí para no bloquear accept()
                        threadPool.execute(() -> handleClient(acceptedSocket));
                    } catch (IOException e) {
                        if (running) {
                            addLog("Error al aceptar conexión: " + e.getMessage());
                        } // Si !running, es probable que el serverSocket se haya cerrado durante shutdown
                    }
                }
            } catch (IOException e) {
                addLog("Error CRÍTICO al iniciar ServerSocket: " + e.getMessage());
                e.printStackTrace(); // Para depuración
                running = false; // Detener el servidor si el socket principal falla
            } finally {
                addLog("Hilo de aceptación de clientes del servidor ha terminado.");
                // Limpieza adicional si es necesaria fuera del shutdown()
            }
        });
        serverAcceptThread.setName("ServerAcceptLoopThread");
        serverAcceptThread.start();
    }

    private void handleClient(Socket clientSocket) {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        addLog("Nuevo cliente conectado desde IP: " + clientIp);
        System.out.println("Cliente conectado desde: " + clientIp);

        ObjectInputStream objectIn = null;
        ObjectOutputStream objectOut = null;
        String clientName = null; // Nombre único del cliente (ej. "Caja_Fabian")

        try {
            // Es crucial crear el Output stream PRIMERO y hacer flush ANTES de crear el Input stream.
            // Esto evita deadlocks en la negociación del stream.
            objectOut = new ObjectOutputStream(clientSocket.getOutputStream());
            objectOut.flush();
            objectIn = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));

            // 1. Leer el mensaje de registro inicial del cliente
            InfoData registrationMsg = (InfoData) objectIn.readObject();
            clientName = registrationMsg.getName();
            ClientTypes clientType = registrationMsg.getType();

            if (clientName == null || clientName.trim().isEmpty()) {
                addLog("Conexión rechazada: Cliente con nombre nulo/vacío desde IP " + clientIp);
                // Enviar error y cerrar (opcional)
                return; // Termina el handler para este cliente
            }

            synchronized (activeClients) { // Sincronizar al verificar y añadir
                if (activeClients.containsKey(clientName)) {
                    addLog("Conexión rechazada: Cliente '" + clientName + "' ya está conectado. IP: " + clientIp);
                    InfoData errorResponse = new InfoData();
                    errorResponse.setMessage("Error: Ya existe una conexión con el nombre '" + clientName + "'.");
                    errorResponse.setName(SERVER_IDENTIFIER); // Identificador del servidor
                    objectOut.writeObject(errorResponse);
                    objectOut.flush();
                    return; // Termina el handler
                }
                // Registrar al nuevo cliente
                ClientConnection conn = new ClientConnection(clientType, clientIp, clientSocket, objectOut);
                activeClients.put(clientName, conn);
            }

            addLog("Cliente '" + clientName + "' (Tipo: " + clientType + ", IP: " + clientIp + ") registrado.");
            System.out.println("Cliente '" + clientName + "' registrado.");

            // Enviar un ACK de bienvenida
            InfoData ackResponse = new InfoData();
            ackResponse.setMessage("Conexión exitosa. Bienvenido " + clientName + "!");
            ackResponse.setName(SERVER_IDENTIFIER);
            synchronized(objectOut) { // Sincronizar escrituras al stream específico
                objectOut.writeObject(ackResponse);
                objectOut.flush();
            }


            // Si es un cliente PANTALLA, enviar estado inicial de tickets
            if (clientType == ClientTypes.PANTALLA) {
                sendCurrentTicketListsToClient(clientName); // Enviar solo a este cliente
            }

            // Si el cliente GENERATOR envía un ticket en su mensaje de registro (opcional)
            if (clientType == ClientTypes.GENERATOR && registrationMsg.getTickets() != null) {
                addTicketAndBroadcast(registrationMsg.getTickets(), clientName);
            }


            // 2. Bucle principal para atender las solicitudes del cliente
            while (running && clientSocket.isConnected() && !clientSocket.isClosed()) {
                Object receivedObject = objectIn.readObject(); // Bloqueante

                if (receivedObject instanceof InfoData) {
                    InfoData clientData = (InfoData) receivedObject;
                    addLog("Datos recibidos de '" + clientName + "': Ticket=" + (clientData.getTickets() != null ? clientData.getTickets().getValue() : "N/A") + ", Msg=" + clientData.getMessage());

                    InfoData responseToClient = null;
                    if (clientData.getType() == ClientTypes.GENERATOR && clientData.getTickets() != null) {
                        addTicketAndBroadcast(clientData.getTickets(), clientName);
                        // GENERATOR usualmente no espera una respuesta de ticket, solo confirma la adición.
                        responseToClient = new InfoData();
                        responseToClient.setMessage("Ticket " + clientData.getTickets().getValue() + " recibido por el servidor.");
                    } else {
                        // Procesar datos de CAJA, SERVICIO, PANTALLA (peticiones de refresh, etc.)
                        responseToClient = processClientRequest(clientData, clientName, clientType);
                    }

                    if (responseToClient != null) {
                        synchronized(objectOut) { // Sincronizar escrituras al stream específico
                            objectOut.writeObject(responseToClient);
                            objectOut.flush();
                        }
                        addLog("Respuesta enviada a '" + clientName + "'.");
                    }
                } else {
                    addLog("Advertencia: Objeto desconocido (" + receivedObject.getClass().getName() + ") de '" + clientName + "'. Ignorando.");
                }
            }

        } catch (EOFException e) {
            addLog("Cliente '" + (clientName != null ? clientName : clientIp) + "' cerró la conexión (EOF).");
        } catch (IOException e) {
            if (running) { // Solo loguear si el servidor no se está apagando
                String cause = (e.getMessage() == null || e.getMessage().contains("Socket closed") || e.getMessage().contains("Connection reset")) ?
                        "conexión cerrada/perdida" : e.getMessage();
                addLog("Error de IO con '" + (clientName != null ? clientName : clientIp) + "': " + cause);
            }
        } catch (ClassNotFoundException e) {
            addLog("Error ClassNotFound con '" + (clientName != null ? clientName : clientIp) + "': " + e.getMessage());
        } finally {
            // Limpieza de recursos para este cliente
            String finalClientIdentifier = (clientName != null ? clientName : clientIp);
            addLog("Limpiando recursos para: " + finalClientIdentifier);

            if (clientName != null) {
                activeClients.remove(clientName); // Remover de la lista de activos
                addLog("Cliente '" + clientName + "' eliminado de clientes activos.");
            }
            // Cerrar streams y socket del cliente
            try { if (objectIn != null) objectIn.close(); } catch (IOException e) { /* ignorar */ }
            try { if (objectOut != null) objectOut.close(); } catch (IOException e) { /* ignorar */ }
            try { if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close(); } catch (IOException e) { /* ignorar */ }

            addLog("Recursos para '" + finalClientIdentifier + "' limpiados. Conexión cerrada.");
            System.out.println("Conexión cerrada para: " + finalClientIdentifier);
        }
    }

    // Procesa solicitudes de clientes (CAJA, SERVICIO, PANTALLA pidiendo refresh)
    private InfoData processClientRequest(InfoData requestData, String clientName, ClientTypes clientType) {
        InfoData response = new InfoData();
        response.setName(SERVER_IDENTIFIER); // El servidor responde

        Ticket ticketFromClient = requestData.getTickets();

        // CASO 1: Un cliente CAJA o SERVICIO está pidiendo un NUEVO ticket
        if (ticketFromClient != null && ticketFromClient.getValue() == null &&
                (clientType == ClientTypes.CAJA || clientType == ClientTypes.SERVICIO)) {

            TicketTypes requestedType = ticketFromClient.getType(); // El tipo de ticket que pide (CAJA o SERVICIO)
            Ticket assignedTicket = assignNextAvailableTicket(requestedType, clientName); // clientName es el operador

            if (assignedTicket != null) {
                response.setTickets(assignedTicket); // Enviar el ticket con operador y attendTime ya seteados
                response.setMessage("Ticket '" + assignedTicket.getValue() + "' asignado.");
                addLog("Ticket '" + assignedTicket.getValue() + "' tipo " + requestedType + " asignado al operador '" + clientName + "'.");
                broadcastTicketUpdateToScreens(); // Notificar a las pantallas
            } else {
                // No se envía un ticket en la respuesta, solo un mensaje
                response.setMessage("No hay tickets disponibles en la cola " + requestedType + ".");
                addLog("No hay tickets para asignar a '" + clientName + "' de tipo " + requestedType + ".");
            }
        }
        // CASO 2: Un cliente CAJA o SERVICIO envía un ticket PROCESADO/COMPLETADO
        else if (ticketFromClient != null && ticketFromClient.getValue() != null &&
                (clientType == ClientTypes.CAJA || clientType == ClientTypes.SERVICIO)) {

            boolean success = processOperatorActionOnTicket(ticketFromClient, clientName);
            if (success) {
                response.setMessage("Acción sobre ticket '" + ticketFromClient.getValue() + "' procesada.");
                // processOperatorActionOnTicket ya llama a broadcastTicketUpdateToScreens()
            } else {
                response.setMessage("No se pudo procesar acción sobre ticket '" + ticketFromClient.getValue() + "'.");
            }
        }
        // CASO 3: Un cliente PANTALLA pide un refresh (o cualquier otro tipo de solicitud)
        else if (requestData.isRequestAllTickets() && clientType == ClientTypes.PANTALLA) {
            // No se envía una respuesta directa aquí, se usa sendCurrentTicketListsToClient
            // o se confía en el broadcast. Si se quiere respuesta directa:
            synchronized(tellerList) { response.setAllTellerTickets(new ArrayList<>(tellerList)); }
            synchronized(serviceList) { response.setAllServiceTickets(new ArrayList<>(serviceList)); }
            response.setMessage("Listas de tickets actuales para pantalla.");
        }
        // Otros tipos de solicitudes (ej. requestLogs)
        else if (requestData.isRequestLogs()){
            synchronized(logMessages){ response.setLogMessages(new ArrayList<>(logMessages));}
            response.setMessage("Logs del servidor.");
        }
        else {
            response.setMessage("Solicitud recibida. Tipo no procesado específicamente: " + requestData);
            addLog("Solicitud no manejada específicamente de '" + clientName + "': " + requestData);
        }
        return response;
    }

    // Asigna el siguiente ticket disponible a un operador
    private synchronized Ticket assignNextAvailableTicket(TicketTypes type, String operatorName) {
        List<Ticket> relevantList = (type == TicketTypes.CAJA) ? tellerList : serviceList;
        Ticket ticketToAssign = null;

        for (Ticket t : relevantList) {
            // Buscar ticket pendiente (state=false) y sin operador asignado
            if (!t.isState() && t.getOperator() == null) {
                ticketToAssign = t;
                break;
            }
        }

        if (ticketToAssign != null) {
            // Asignar operador (esto también setea el attendTime en la clase Ticket)
            ticketToAssign.setOperator(operatorName);
            // No es necesario cambiar 'state' aquí, sigue siendo 'pendiente' hasta que se complete.
            addLog("Ticket '" + ticketToAssign.getValue() + "' (Tipo: " + type + ") encontrado y asignado a operador '" + operatorName + "'.");
        } else {
            addLog("No hay tickets en espera del tipo " + type + " para asignar a '" + operatorName + "'.");
            // Opcional: generar un ticket nuevo si no hay en cola y la lógica lo permite
            // ticketToAssign = generateNewTicketAndAssign(type, operatorName);
        }
        return ticketToAssign;
    }

    // Procesa una acción de un operador sobre un ticket (generalmente completarlo)
    private synchronized boolean processOperatorActionOnTicket(Ticket ticketFromOperator, String operatorName) {
        List<Ticket> relevantList = (ticketFromOperator.getType() == TicketTypes.CAJA) ? tellerList : serviceList;

        // Buscar el ticket en la lista del servidor por su valor (ID)
        Ticket ticketInServerList = relevantList.stream()
                .filter(t -> t.getValue().equals(ticketFromOperator.getValue()))
                .findFirst()
                .orElse(null);

        if (ticketInServerList != null) {
            // Verificar si el operador que envía es el mismo que tiene el ticket asignado, o si nadie lo tiene
            if (ticketInServerList.getOperator() == null || ticketInServerList.getOperator().equals(operatorName)) {

                if (ticketFromOperator.isState()) { // Si el ticket viene marcado como COMPLETADO
                    relevantList.remove(ticketInServerList);
                    addLog("Ticket '" + ticketInServerList.getValue() + "' (Tipo: " + ticketFromOperator.getType() +
                            ") completado por '" + operatorName + "' y removido de la lista.");
                } else {
                    // Si el ticket NO está state=true (pendiente/atendiendo),
                    // y el operador lo envía, podría ser una actualización (aunque no es el flujo actual de CajaController).
                    // Aquí, simplemente re-afirmamos el operador y el tiempo de atención.
                    ticketInServerList.setOperator(operatorName); // Reafirma o actualiza attendTime
                    // ticketInServerList.setTimestamp(LocalDateTime.now()); // Opcional: actualizar timestamp de última acción
                    addLog("Ticket '" + ticketInServerList.getValue() + "' (Tipo: " + ticketFromOperator.getType() +
                            ") estado 'pendiente/atendiendo' reafirmado por operador '" + operatorName + "'.");
                }
                broadcastTicketUpdateToScreens(); // Notificar a las pantallas del cambio
                return true;
            } else {
                // El ticket está asignado a OTRO operador
                addLog("Acción rechazada: Operador '" + operatorName + "' intentó modificar ticket '" +
                        ticketFromOperator.getValue() + "' asignado a '" + ticketInServerList.getOperator() + "'.");
                return false;
            }
        } else {
            addLog("Acción rechazada: Ticket '" + ticketFromOperator.getValue() + "' (enviado por '" + operatorName +
                    "') no encontrado en la lista del servidor.");
            return false;
        }
    }

    // Añade un ticket (usualmente de un GENERATOR) y notifica
    private synchronized void addTicketAndBroadcast(Ticket ticket, String generatorName) {
        if (ticket == null || ticket.getValue() == null) {
            addLog("Intento de añadir ticket nulo o sin ID por generador '" + generatorName + "'.");
            return;
        }
        List<Ticket> targetList = (ticket.getType() == TicketTypes.CAJA) ? tellerList : serviceList;

        // Evitar duplicados basados en el valor del ticket
        boolean alreadyExists = targetList.stream().anyMatch(t -> t.getValue().equals(ticket.getValue()));
        if (alreadyExists) {
            addLog("Ticket '" + ticket.getValue() + "' de generador '" + generatorName + "' ya existe. No fue añadido.");
            return;
        }

        targetList.add(ticket);
        addLog("Ticket '" + ticket.getValue() + "' (Tipo: " + ticket.getType() + ") de generador '" +
                generatorName + "' añadido a la lista.");
        System.out.println("Nuevo ticket añadido por " + generatorName + ": " + ticket.getValue());
        broadcastTicketUpdateToScreens();
    }

    // Envía las listas de tickets actuales a UN cliente específico (usualmente una PANTALLA al conectarse)
    private void sendCurrentTicketListsToClient(String clientName) {
        ClientConnection clientConn = activeClients.get(clientName);
        if (clientConn == null || clientConn.type != ClientTypes.PANTALLA) {
            // addLog("Intento de enviar listas de tickets a cliente no PANTALLA o no encontrado: " + clientName);
            return;
        }

        InfoData initialState = new InfoData();
        initialState.setName(SERVER_IDENTIFIER);
        initialState.setMessage("Estado inicial/actual de tickets.");
        // Enviar copias de las listas para evitar ConcurrentModificationException y encapsulamiento
        synchronized (tellerList) {
            initialState.setAllTellerTickets(new ArrayList<>(tellerList));
        }
        synchronized (serviceList) {
            initialState.setAllServiceTickets(new ArrayList<>(serviceList));
        }

        try {
            synchronized(clientConn.objectOut) {
                clientConn.objectOut.writeObject(initialState);
                clientConn.objectOut.flush();
            }
            addLog("Estado de tickets enviado a PANTALLA '" + clientName + "'.");
        } catch (IOException e) {
            addLog("Error al enviar estado de tickets a PANTALLA '" + clientName + "': " + e.getMessage());
            // Considerar remover cliente si falla el envío gravemente
        }
    }

    // Notifica a TODOS los clientes PANTALLA sobre cambios en las listas de tickets
    private void broadcastTicketUpdateToScreens() {
        InfoData updateMsg = new InfoData();
        updateMsg.setName(SERVER_IDENTIFIER);
        updateMsg.setMessage("Actualización de estado de tickets.");
        // Enviar copias de las listas
        synchronized (tellerList) {
            updateMsg.setAllTellerTickets(new ArrayList<>(tellerList));
        }
        synchronized (serviceList) {
            updateMsg.setAllServiceTickets(new ArrayList<>(serviceList));
        }

        List<String> screenClientNames = new ArrayList<>();
        activeClients.forEach((name, conn) -> {
            if (conn.type == ClientTypes.PANTALLA) {
                screenClientNames.add(name);
            }
        });

        if (!screenClientNames.isEmpty()) {
            addLog("Iniciando broadcast de actualización de tickets a " + screenClientNames.size() + " PANTALLA(s).");
        }

        for (String screenName : screenClientNames) {
            ClientConnection screenConn = activeClients.get(screenName);
            if (screenConn != null && screenConn.socket.isConnected() && !screenConn.socket.isClosed()) {
                try {
                    synchronized(screenConn.objectOut) {
                        screenConn.objectOut.writeObject(updateMsg);
                        screenConn.objectOut.flush();
                    }
                } catch (IOException e) {
                    addLog("Error en broadcast a PANTALLA '" + screenName + "': " + e.getMessage() + ". Cliente pudo desconectarse.");
                    // Podría implementarse una lógica para remover clientes que fallan repetidamente.
                }
            } else {
                addLog("Broadcast no enviado a PANTALLA '" + screenName + "': conexión no válida.");
            }
        }
    }

    // Notifica a TODOS los clientes LOGS (si existieran)
    private void broadcastLogsToLogClients() {
        // Similar a broadcastTicketUpdateToScreens, pero filtrando por ClientTypes.LOGS
        // y enviando InfoData con logMessages.
    }


    private synchronized void addLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String logEntry = String.format("[%s] [Servidor] %s", timestamp, message);
        logMessages.add(logEntry);
        // Imprimir también en la consola del servidor para monitoreo en tiempo real
        System.out.println(logEntry);

        final int MAX_LOG_ENTRIES_IN_MEMORY = 1000; // Limitar logs en memoria
        while (logMessages.size() > MAX_LOG_ENTRIES_IN_MEMORY) {
            logMessages.remove(0);
        }
        // Aquí se podría añadir la escritura a un archivo de log persistente
        // if (logWriter != null) { try { logWriter.write(logEntry + "\n"); logWriter.flush(); } catch (IOException e) {} }
    }

    public synchronized void shutdown() {
        if (!running) {
            addLog("El servidor ya está apagado o en proceso de apagado.");
            return;
        }
        addLog("Iniciando apagado del servidor...");
        System.out.println("Secuencia de apagado del servidor iniciada...");
        running = false; // Detener bucles principales

        // Cerrar el ServerSocket para no aceptar nuevas conexiones
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                addLog("ServerSocket principal cerrado.");
            }
        } catch (IOException e) {
            addLog("Error cerrando ServerSocket principal: " + e.getMessage());
        }

        // Cerrar todas las conexiones de clientes activos
        addLog("Cerrando " + activeClients.size() + " conexiones de clientes...");
        activeClients.forEach((clientName, conn) -> {
            try {
                if (conn.objectOut != null) conn.objectOut.close();
            } catch (IOException e) { /* Ignorar */ }
            try {
                // El InputStream se cierra en el finally de handleClient,
                // pero si el hilo handleClient está bloqueado en readObject,
                // cerrar el socket lo desbloqueará con una excepción.
                if (conn.socket != null && !conn.socket.isClosed()) conn.socket.close();
            } catch (IOException e) {
                addLog("Error cerrando socket para cliente '" + clientName + "': " + e.getMessage());
            }
            addLog("Conexión con cliente '" + clientName + "' cerrada (o intento).");
        });
        activeClients.clear();
        addLog("Todos los clientes desconectados y listas limpiadas.");

        // Apagar el pool de hilos
        addLog("Apagando pool de hilos...");
        threadPool.shutdown(); // Deshabilitar nuevas tareas
        try {
            // Esperar un tiempo para que las tareas existentes terminen
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow(); // Cancelar tareas en ejecución
                // Esperar un tiempo para que las tareas respondan a la cancelación
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    addLog("Error: Pool de hilos no terminó limpiamente.");
                    System.err.println("El pool de hilos no terminó.");
                }
            }
        } catch (InterruptedException ie) {
            threadPool.shutdownNow(); // Re-cancelar si el hilo actual fue interrumpido
            Thread.currentThread().interrupt(); // Preservar estado de interrupción
        }
        addLog("Pool de hilos detenido.");
        System.out.println("Servidor apagado completamente.");
        addLog("Servidor completamente apagado.");
    }

    public synchronized List<String> getLogSnapshot() {
        return new ArrayList<>(logMessages);
    }

    public boolean isServerRunning() {
        return this.running;
    }


    // Punto de entrada del servidor
    public static void main(String[] args) {
        int serverPortNum = 12345; // Puerto por defecto
        try {
            // Properties appProps = new PropertiesInfo().getProperties("server_config.properties");
            // serverPortNum = Integer.parseInt(appProps.getProperty("server.port", "12345"));
            PropertiesInfo propsInfo = new PropertiesInfo(); // Asumiendo que tienes esta clase
            Properties appProps = propsInfo.getProperties(); // y que carga las propiedades
            serverPortNum = Integer.parseInt(appProps.getProperty("server.port","12345"));

        } catch (Exception e) { // NumberFormatException, IOException, etc.
            System.err.println("ADVERTENCIA: No se pudo cargar puerto desde config. Usando puerto por defecto: " +
                    serverPortNum + ". Error: " + e.getMessage());
        }

        final Server ticketServer = new Server(serverPortNum);
        ticketServer.start();

        // Hook para apagar el servidor de forma elegante al cerrar la JVM (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook: Apagando servidor...");
            if (ticketServer.isServerRunning()) {
                ticketServer.shutdown();
            }
        }, "ServerShutdownHookThread"));
    }
}