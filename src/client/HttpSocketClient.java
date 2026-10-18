package client;

import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocketFactory;

public class HttpSocketClient {

    public static class ResponseData {
        public String headers;
        public String body;
        public int htmlLength;
        public int pCount, divCount, spanCount, imgCount;
        public int statusCode = 200;
        public String statusText = "OK";
        public long responseTimeMs;
        public String finalUrl;
    }

    public static ResponseData executeRequest(String urlStr, String method, String postData) throws Exception {
        return executeRequestWithRedirect(urlStr, method, postData, 0);
    }

    private static ResponseData executeRequestWithRedirect(String urlStr, String method, String postData, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new Exception("Lỗi: Quá nhiều lần chuyển hướng (Too many redirects)!");
        }

        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }

        long startTime = System.currentTimeMillis();
        boolean isHttps = urlStr.startsWith("https://");

        // 1. Tách chuỗi URL
        String cleanUrl = isHttps ? urlStr.substring(8) : urlStr.substring(7);
        String host;
        String path = "/";
        int port = isHttps ? 443 : 80;

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

        // 2. Khởi tạo Socket (Mã hóa SSL nếu là HTTPS)
        Socket socket = isHttps ? SSLSocketFactory.getDefault().createSocket(host, port) : new Socket(host, port);

        try (Socket s = socket;
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"))) {

            // Gửi HTTP Request
            writer.write(method + " " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + host + "\r\n");
            writer.write("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) CustomJavaBrowser/2.0\r\n");
            writer.write("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n");
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

            // 3. Đọc HTTP Response
            StringBuilder headerBuilder = new StringBuilder();
            StringBuilder bodyBuilder = new StringBuilder();
            String line;
            boolean isBody = false;

            int statusCode = 200;
            String statusText = "OK";
            String redirectLocation = null;

            // Đọc Status-Line đầu tiên
            String statusLine = reader.readLine();
            if (statusLine != null) {
                headerBuilder.append(statusLine).append("\n");
                String[] statusParts = statusLine.split(" ", 3);
                if (statusParts.length >= 2) {
                    try {
                        statusCode = Integer.parseInt(statusParts[1]);
                        statusText = statusParts.length >= 3 ? statusParts[2] : "";
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Đọc các Header tiếp theo
            while ((line = reader.readLine()) != null) {
                if (!isBody) {
                    if (line.isEmpty()) {
                        isBody = true;
                    } else {
                        headerBuilder.append(line).append("\n");
                        if (line.toLowerCase().startsWith("location:")) {
                            redirectLocation = line.substring(9).trim();
                        }
                    }
                } else {
                    bodyBuilder.append(line).append("\n");
                }
            }

            // Xử lý chuyển hướng Tự động (Follow 301 / 302 Redirect)
            if ((statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308) 
                    && redirectLocation != null && !redirectLocation.isEmpty()) {
                
                // Giải mã đường dẫn tương đối nếu cần
                String newUrl = redirectLocation;
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    String baseUrl = (isHttps ? "https://" : "http://") + host + (port != 80 && port != 443 ? ":" + port : "");
                    if (!newUrl.startsWith("/")) newUrl = "/" + newUrl;
                    newUrl = baseUrl + newUrl;
                }
                
                // Gọi lại chính hàm này cho URL mới (Giữ lại phương thức GET cho redirect)
                return executeRequestWithRedirect(newUrl, "GET", "", redirectCount + 1);
            }

            long endTime = System.currentTimeMillis();

            ResponseData result = new ResponseData();
            result.headers = headerBuilder.toString();
            result.body = bodyBuilder.toString();
            result.htmlLength = result.body.length();
            result.statusCode = statusCode;
            result.statusText = statusText;
            result.responseTimeMs = (endTime - startTime);
            result.finalUrl = urlStr;

            // Đếm các thẻ HTML
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