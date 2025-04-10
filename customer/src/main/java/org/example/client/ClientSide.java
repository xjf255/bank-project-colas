package org.example.client;



import org.example.shared.InfoData;

import java.io.*;
import java.net.Socket;

public class ClientSide {
    private Socket socket;
    private BufferedReader consoleInput;
    private ObjectOutputStream out;

    public ClientSide(String address, int port) {
        try {
            socket = new Socket(address, port);
            System.out.println("Connected to " + address + ":" + port);

            consoleInput = new BufferedReader(new InputStreamReader(System.in));
            out = new ObjectOutputStream(socket.getOutputStream());


            String line = "";
            while (!line.equalsIgnoreCase("Over")) {
                System.out.print("Enter message: ");
                line = consoleInput.readLine();
                InfoData data = new InfoData();
                data.setName("Server");
                data.setIp("127.0.0.1");
                data.setMessage(line);
                out.writeObject(data);
            }

            System.out.println("Closing connection...");

            // Close everything
            consoleInput.close();
            out.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ClientSide("127.0.0.1", 5000);
    }
}

