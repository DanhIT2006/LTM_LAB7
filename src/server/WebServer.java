package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
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
        String clientIp = socket.getInetAddress().getHostAddress();
        try (
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
        ) {
            // 1. Đọc dòng Request-Line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            String method = parts[0].toUpperCase();
            String path = parts[1].equals("/") ? "/index.html" : parts[1];

            // 2. Đọc Header & Content-Length
            String headerLine;
            int contentLength = 0;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                }
            }

            // 3. Đọc dữ liệu Body nếu là POST
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
            }

            // 4. Chuẩn bị nội dung phản hồi
            byte[] bodyBytes;
            int statusCode = 200;
            String statusText = "OK";
            String contentType = getMimeType(path);

            if ("POST".equals(method)) {
                Map<String, String> params = parseFormData(postBodyData);
                contentType = "text/html; charset=UTF-8";
                
                String dynamicHtml = "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head><meta charset=\"UTF-8\"><title>Xử lý POST</title><link rel=\"stylesheet\" href=\"style.css\"></head>\n" +
                        "<body>\n" +
                        "   <div class=\"container\">\n" +
                        "       <h1>Xử Lý Dữ Liệu POST Thành Công!</h1>\n" +
                        "       <p>Dữ liệu đã nhận được từ Browser của bạn:</p>\n" +
                        "       <div><b>Tài khoản:</b> " + params.getOrDefault("user", "Chưa nhập") + "</div>\n" +
                        "       <div><b>Mật khẩu:</b> " + params.getOrDefault("pass", "Chưa nhập") + "</div>\n" +
                        "       <br/>\n" +
                        "       <span>Trạng thái: Đã xác thực thành công!</span><br/><br/>\n" +
                        "       <a href=\"index.html\"> Quay lại Trang Chủ</a>\n" +
                        "   </div>\n" +
                        "</body>\n" +
                        "</html>";
                
                bodyBytes = dynamicHtml.getBytes(StandardCharsets.UTF_8);

            } else { // GET hoặc HEAD
                File file = new File(WEB_ROOT + path);
                if (!file.exists()) {
                    file = new File("LTM_LAB7/" + WEB_ROOT + path);
                }

                if (file.exists() && !file.isDirectory()) {
                    bodyBytes = Files.readAllBytes(file.toPath());
                } else {
                    statusCode = 404;
                    statusText = "Not Found";
                    contentType = "text/html; charset=UTF-8";
                    bodyBytes = "<h1>404 File Not Found</h1><p>Trang bạn tìm không tồn tại trên Server.</p>".getBytes(StandardCharsets.UTF_8);
                }
            }

            // 5. Tạo HTTP Response Header
            String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                            "Content-Type: " + contentType + "\r\n" +
                            "Content-Length: " + bodyBytes.length + "\r\n" +
                            "Server: JavaSimpleServer/2.0\r\n" +
                            "Connection: close\r\n\r\n";

            out.write(header.getBytes(StandardCharsets.UTF_8));

            if ("GET".equals(method) || "POST".equals(method)) {
                out.write(bodyBytes);
            }
            out.flush();

            // TÍNH NĂNG MỚI: Nhật ký Server Logging chuyên nghiệp
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            System.out.printf("[%s] [%s] %s %s -> %d %s (%d bytes)\n", 
                    timeStamp, clientIp, method, path, statusCode, statusText, bodyBytes.length);

            socket.close();

        } catch (Exception e) {
            System.err.println("Lỗi xử lý Client: " + e.getMessage());
        }
    }

    // TÍNH NĂNG MỚI: Tự động phân tích Content-Type (MIME Types)
    private static String getMimeType(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        return "application/octet-stream";
    }

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