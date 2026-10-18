package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

public class WebServer {
    private static final int PORT = 8080;
    private static final String WEB_ROOT = "webroot"; 

    public static void main(String[] args) {
        System.out.println("=== WEB SERVER dang chay o port " + PORT + " ===");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream()
        ) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            System.out.println("[SERVER REC]: " + requestLine);
            String[] parts = requestLine.split(" ");
            String method = parts[0].toUpperCase();
            String path = parts[1].equals("/") ? "/index.html" : parts[1];

            // Đọc nội dung file HTML từ thư mục webroot
            File file = new File(WEB_ROOT + path);
            byte[] bodyBytes;
            int statusCode = 200;
            String statusText = "OK";

            if (file.exists() && !file.isDirectory()) {
                bodyBytes = Files.readAllBytes(file.toPath());
            } else {
                statusCode = 404;
                statusText = "Not Found";
                bodyBytes = "<h1>404 Not Found</h1>".getBytes();
            }

         
            String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + bodyBytes.length + "\r\n" +
                            "Server: JavaSimpleServer/1.0\r\n" +
                            "Connection: close\r\n\r\n";

        
            out.write(header.getBytes("UTF-8"));
            
            
            if ("GET".equals(method) || "POST".equals(method)) {
                out.write(bodyBytes);
            }
            
            out.flush();
            socket.close();
        } catch (Exception e) {
            System.err.println("Lỗi xử lý Client: " + e.getMessage());
        }
    }
}