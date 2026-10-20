package client;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class HttpSocketClient {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    public static class ResponseData {
        public String headers;
        public String body;
        public int htmlLength;
        public int pCount, divCount, spanCount, imgCount;
        public int statusCode = 200;
        public String statusText = "OK";
        public long responseTimeMs;
        public String finalUrl;
        public boolean redirected = false; // true nếu URL cuối khác URL ban đầu do bị chuyển hướng
    }

    public static ResponseData executeRequest(String urlStr, String method, String postData) throws Exception {
        urlStr = urlStr.trim();
        String lower = urlStr.toLowerCase();

        // Người dùng đã gõ sẵn http:// hoặc https:// -> dùng đúng như vậy
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return executeRequestWithRedirect(urlStr, method, postData, 0);
        }

        // Không có scheme (vd: vku.udn.vn)
        String hostPart = urlStr.split("[/?#]", 2)[0];

        // Có ghi rõ port (vd: 26.60.13.119:8080) -> thường là server nội bộ, dùng HTTP luôn
        if (hostPart.contains(":")) {
            return executeRequestWithRedirect("http://" + urlStr, method, postData, 0);
        }

        // Giống trình duyệt hiện đại: thử HTTPS trước, lỗi thì quay về HTTP
        try {
            return executeRequestWithRedirect("https://" + urlStr, method, postData, 0);
        } catch (Exception httpsError) {
            try {
                return executeRequestWithRedirect("http://" + urlStr, method, postData, 0);
            } catch (Exception httpError) {
                throw new Exception("Không kết nối được bằng cả HTTPS lẫn HTTP.\n"
                        + "  • HTTPS: " + httpsError.getMessage() + "\n"
                        + "  • HTTP:  " + httpError.getMessage());
            }
        }
    }

    private static ResponseData executeRequestWithRedirect(String urlStr, String method, String postData, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new Exception("Lỗi: Quá nhiều lần chuyển hướng (Too many redirects)!");
        }

        if (!urlStr.toLowerCase().startsWith("http://") && !urlStr.toLowerCase().startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }

        // Bỏ phần #fragment (không gửi lên server)
        int hashIdx = urlStr.indexOf('#');
        if (hashIdx != -1) urlStr = urlStr.substring(0, hashIdx);

        long startTime = System.currentTimeMillis();
        boolean isHttps = urlStr.toLowerCase().startsWith("https://");

        // 1. Tách chuỗi URL
        String cleanUrl = isHttps ? urlStr.substring(8) : urlStr.substring(7);
        int cut = cleanUrl.length();
        int slashIdx = cleanUrl.indexOf('/');
        int queryIdx = cleanUrl.indexOf('?');
        if (slashIdx != -1) cut = Math.min(cut, slashIdx);
        if (queryIdx != -1) cut = Math.min(cut, queryIdx);

        String authority = cleanUrl.substring(0, cut);
        String rest = cleanUrl.substring(cut);
        String path = rest.isEmpty() ? "/" : (rest.startsWith("?") ? "/" + rest : rest);

        String host = authority;
        int port = isHttps ? 443 : 80;
        int colonIdx = authority.lastIndexOf(':');
        if (colonIdx != -1) {
            host = authority.substring(0, colonIdx);
            port = Integer.parseInt(authority.substring(colonIdx + 1));
        }
        boolean defaultPort = (isHttps && port == 443) || (!isHttps && port == 80);
        String hostHeader = defaultPort ? host : host + ":" + port;

        // 2. Khởi tạo Socket (có timeout; mã hóa SSL nếu là HTTPS)
        Socket raw = new Socket();
        Socket socket;
        try {
            raw.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            raw.setSoTimeout(READ_TIMEOUT_MS);
            if (isHttps) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(raw, host, port, true); // truyền host để hỗ trợ SNI
                ssl.startHandshake();
                socket = ssl;
            } else {
                socket = raw;
            }
        } catch (IOException e) {
            try { raw.close(); } catch (IOException ignored) {}
            throw e;
        }

        try (Socket s = socket;
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"))) {

            // Gửi HTTP Request
            writer.write(method + " " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + hostHeader + "\r\n");
            writer.write("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) CustomJavaBrowser/2.0\r\n");
            writer.write("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n");
            writer.write("Connection: close\r\n");

            if ("POST".equalsIgnoreCase(method)) {
                String data = postData == null ? "" : postData;
                writer.write("Content-Type: application/x-www-form-urlencoded\r\n");
                writer.write("Content-Length: " + data.getBytes("UTF-8").length + "\r\n");
                writer.write("\r\n");
                writer.write(data);
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
            if (statusLine == null) {
                throw new IOException("Server đóng kết nối mà không phản hồi");
            }
            headerBuilder.append(statusLine).append("\n");
            String[] statusParts = statusLine.split(" ", 3);
            if (statusParts.length >= 2) {
                try {
                    statusCode = Integer.parseInt(statusParts[1]);
                    statusText = statusParts.length >= 3 ? statusParts[2] : "";
                } catch (NumberFormatException ignored) {}
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

            // Tự động theo dõi chuyển hướng (301 / 302 / 303 / 307 / 308)
            if ((statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308)
                    && redirectLocation != null && !redirectLocation.isEmpty()) {

                String newUrl;
                try {
                    // Xử lý đúng mọi dạng: tuyệt đối, //host/path, /path, path tương đối
                    newUrl = URI.create(urlStr).resolve(redirectLocation).toString();
                } catch (IllegalArgumentException ex) {
                    if (redirectLocation.toLowerCase().startsWith("http")) {
                        newUrl = redirectLocation;
                    } else {
                        newUrl = (isHttps ? "https://" : "http://") + hostHeader
                                + (redirectLocation.startsWith("/") ? "" : "/") + redirectLocation;
                    }
                }

                ResponseData redirectedResult = executeRequestWithRedirect(newUrl, "GET", "", redirectCount + 1);
                redirectedResult.redirected = true;
                return redirectedResult;
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