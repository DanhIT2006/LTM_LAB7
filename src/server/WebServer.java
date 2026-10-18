package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class WebServer {
    private static final int PORT = 8080;
    private static final String WEB_ROOT = "webroot"; 

    public static void main(String[] args) {
        System.out.println("=== WEB SERVER ĐÃ CHẠY TRÊN PORT " + PORT + " ===");
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
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
        ) {
            // 1. Đọc dòng Request-Line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            System.out.println("[SERVER REC]: " + requestLine);
            String[] parts = requestLine.split(" ");
            String method = parts[0].toUpperCase();
            String path = parts[1].equals("/") ? "/index.html" : parts[1];

            // 2. Đọc tất cả Header & lấy Content-Length (nếu là POST)
            String headerLine;
            int contentLength = 0;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                }
            }

            // 3. Đọc dữ liệu Body nếu là phương thức POST
            String postBodyData = "";
            if ("POST".equals(method) && contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int bytesRead = 0;
                while (bytesRead < contentLength) {
                    int read = reader.read(bodyChars, bytesRead, contentLength - bytesRead);
                    if (read == -1) break;
                    bytesRead += read;
                }
                postBodyData = new String(bodyChars);
                System.out.println("[SERVER POST DATA]: " + postBodyData);
            }

            // 4. Chuẩn bị nội dung HTML phản hồi
            byte[] bodyBytes;
            int statusCode = 200;
            String statusText = "OK";

            if ("POST".equals(method)) {
                // Tách dữ liệu gửi lên (Ví dụ: user=admin&pass=123 -> Map)
                Map<String, String> params = parseFormData(postBodyData);
                
                // Sinh HTML ĐỘNG dựa trên dữ liệu POST gửi lên
                String dynamicHtml = "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head><meta charset=\"UTF-8\"><title>KẾT QUẢ XỬ LÝ POST</title></head>\n" +
                        "<body>\n" +
                        "   <h1>Xử Lý Dữ Liệu POST Thành Công!</h1>\n" +
                        "   <p>Dữ liệu đã gửi lên Web Server:</p>\n" +
                        "   <div><b>Tài khoản:</b> " + params.getOrDefault("user", params.getOrDefault("username", "Chưa nhập")) + "</div>\n" +
                        "   <div><b>Mật khẩu:</b> " + params.getOrDefault("pass", params.getOrDefault("password", "Chưa nhập")) + "</div>\n" +
                        "   <p>Thông tin của bạn đã được tiếp nhận và cập nhật vào hệ thống thành công.</p>\n" +
                        "   <span>Trạng thái: Đã xác thực (Success)</span>\n" +
                        "   <br/><br/>\n" +
                        "   <img src=\"success.png\" alt=\"Xác thực thành công\" />\n" +
                        "</body>\n" +
                        "</html>";
                
                bodyBytes = dynamicHtml.getBytes(StandardCharsets.UTF_8);

            } else { // Phương thức GET hoặc HEAD: Đọc file tĩnh webroot/index.html
                File file = new File(WEB_ROOT + path);
                if (file.exists() && !file.isDirectory()) {
                    bodyBytes = Files.readAllBytes(file.toPath());
                } else {
                    statusCode = 404;
                    statusText = "Not Found";
                    bodyBytes = "<h1>404 File Not Found</h1>".getBytes(StandardCharsets.UTF_8);
                }
            }

            // 5. Tạo HTTP Response Headers
            String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + bodyBytes.length + "\r\n" +
                            "Server: JavaSimpleServer/1.0\r\n" +
                            "Connection: close\r\n\r\n";

            out.write(header.getBytes(StandardCharsets.UTF_8));

            // Nếu là GET hoặc POST -> Gửi Body. Nếu HEAD -> Chỉ gửi Header
            if ("GET".equals(method) || "POST".equals(method)) {
                out.write(bodyBytes);
            }

            out.flush();
            socket.close();

        } catch (Exception e) {
            System.err.println("Lỗi xử lý Client: " + e.getMessage());
        }
    }

    // Hàm hỗ trợ tách chuỗi URL-encoded (user=admin&pass=123) thành Map
    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        if (formData == null || formData.trim().isEmpty()) return map;

        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            } else if (keyValue.length == 1) {
                map.put(keyValue[0].trim(), "");
            }
        }
        return map;
    }
}