package org.example.server.Model;

import org.example.shared.*;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class Logs {
    private Socket socket;
    private BufferedReader consoleInput;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String address;
    private int port;
    public Logs(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public List<String> start(){
        List<String> logs = null;
        try {
            connect(address, port);
            logs =  processCommunication();
            return logs;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
        return logs;
    }

    private void connect(String address, int port) throws IOException {
        socket = new Socket(address, port);
        System.out.println("Connected to " + address + ":" + port);

        consoleInput = new BufferedReader(new InputStreamReader(System.in));
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    private List<String> processCommunication() throws IOException, ClassNotFoundException {
        List<String> logs = null;
        while (true) {
            InfoData dataToSend = buildInfoData("Logs");
            out.writeObject(dataToSend);
            out.flush();

            try {
                InfoData response = (InfoData) in.readObject();
                System.out.println("Server Response: " + response);
                logs = response.getLogMessages();
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error receiving response: " + e.getMessage());
                break;
            }
            return logs;
        }
        return logs;
    }

    private InfoData buildInfoData(String message) {
        Ticket ticket = new Ticket();
        InfoData data = new InfoData();
        data.setType(ClientTypes.MONITOR);
        data.setName(message);
        data.setTickets(ticket);
        data.setRequestLogs(true);
        return data;
    }

    private void cleanup() {
        try {
            if (consoleInput != null) consoleInput.close();
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }

    public static void main(String[] args) {

    }
}
