package client;

import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpSocketClient {

    public static class ResponseData {
        public String headers;
        public String body;
        public int htmlLength;
        public int pCount, divCount, spanCount, imgCount;
    }

    public static ResponseData executeRequest(String urlStr, String method, String postData) throws Exception {
        if (!urlStr.startsWith("http://")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ giao thức http://");
        }

        
        String cleanUrl = urlStr.substring(7);
        String host;
        String path = "/";
        int port = 80;

        int slashIdx = cleanUrl.indexOf('/');
        if (slashIdx != -1) {
            host = cleanUrl.substring(0, slashIdx);
            path = cleanUrl.substring(slashIdx);
        } else {
            host = cleanUrl;
        }

        if (host.contains(":")) {
            String[] parts = host.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        }

        // 2. Mở Socket & Gửi Request
        try (Socket socket = new Socket(host, port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            writer.write(method + " " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + host + "\r\n");
            writer.write("User-Agent: SimpleJavaBrowser/1.0\r\n");
            writer.write("Connection: close\r\n");

            if ("POST".equalsIgnoreCase(method)) {
                writer.write("Content-Type: application/x-www-form-urlencoded\r\n");
                writer.write("Content-Length: " + postData.getBytes("UTF-8").length + "\r\n");
                writer.write("\r\n");
                writer.write(postData);
            } else {
                writer.write("\r\n");
            }
            writer.flush();

            // 3. Đọc Response
            StringBuilder headerBuilder = new StringBuilder();
            StringBuilder bodyBuilder = new StringBuilder();
            String line;
            boolean isBody = false;

            while ((line = reader.readLine()) != null) {
                if (!isBody) {
                    if (line.isEmpty()) {
                        isBody = true;
                    } else {
                        headerBuilder.append(line).append("\n");
                    }
                } else {
                    bodyBuilder.append(line).append("\n");
                }
            }

            ResponseData result = new ResponseData();
            result.headers = headerBuilder.toString();
            result.body = bodyBuilder.toString();
            result.htmlLength = result.body.length();

            
            result.pCount = countTag(result.body, "p");
            result.divCount = countTag(result.body, "div");
            result.spanCount = countTag(result.body, "span");
            result.imgCount = countTag(result.body, "img");

            return result;
        }
    }

    private static int countTag(String html, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + "(\\s+[^>]*)?>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}