package university;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer {
    public static void main(String[] args) {
        System.out.println("[SOCKET SERVER] Starting Live Audit Logger on port 8888...");
        
        try (ServerSocket serverSocket = new ServerSocket(8888)) {
            while (true) {
                // Wait for a connection from the RMI Server
                Socket socket = serverSocket.accept();
                
                // Read the incoming message
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String logMessage = reader.readLine();
                
                if (logMessage != null) {
                    System.out.println("[AUDIT LOG]: " + logMessage);
                }
                
                socket.close(); // Close connection after receiving the log
            }
        } catch (Exception e) {
            System.err.println("[SOCKET SERVER] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}