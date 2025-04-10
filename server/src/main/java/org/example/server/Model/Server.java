package org.example.server.Model;

import org.example.shared.InfoData;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private Socket socket = null;
    private ServerSocket server = null;
    private ObjectInputStream in = null;

    public Server(int port) {
        try {
            server = new ServerSocket(port);
            System.out.println("Server started");
            System.out.println("Waiting for a client ...");
            socket = server.accept();
            System.out.println("Client accepted");
            in = new ObjectInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            String line = "";
            InfoData info;
            while (!line.equals("Over")) {
                try {
                    info = (InfoData) in.readObject();
                    System.out.println(info.getName() + ":" + info.getMessage());
                } catch(IOException i) {
                    System.out.println(i);
                    line = "Over";
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("Closing connection");
            socket.close();
            in.close();
        } catch(IOException i) {
            System.out.println(i);
        }
    }

    public static void main(String args[]) {
        Server server = new Server(5000);
    }
}
