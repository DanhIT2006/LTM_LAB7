import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lưu lịch sử duyệt web vào file JSON.
 * Khóa của mỗi mục là URL ĐẦY ĐỦ (gồm cả query), chỉ bỏ phần #fragment.
 * Vào lại cùng một URL thì tăng số lần truy cập, cập nhật phương thức và thời điểm gần nhất.
 */
public class HistoryStore {

    public static class Entry {
        public String url;
        public String method = "GET"; // Mặc định là GET
        public String title = "";
        public String firstVisit;
        public String lastVisit;
        public int visits = 1;
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;
    private final Map<String, Entry> map = new LinkedHashMap<>();
    private String lastKey = "";

    public HistoryStore(String fileName) {
        this.file = Paths.get(fileName).toAbsolutePath();
        load();
    }

    public Path getFile() {
        return file;
    }

    /** Ghi nhận một lượt truy cập URL với phương thức GET mặc định. */
    public synchronized void visit(String url) {
        visit(url, "GET");
    }

    /** Ghi nhận một lượt truy cập URL kèm theo phương thức HTTP (GET, POST, HEAD...). */
    public synchronized void visit(String url, String method) {
        String key = normalize(url);
        if (key == null) return;

        String m = (method == null || method.trim().isEmpty()) ? "GET" : method.trim().toUpperCase();
        
        // Bỏ qua nếu truy cập trùng URL và phương thức liên tiếp
        String currentKeyWithMethod = key + "|" + m;
        if (currentKeyWithMethod.equals(lastKey)) return;
        lastKey = currentKeyWithMethod;

        String now = LocalDateTime.now().format(TIME);
        Entry e = map.get(key);
        if (e == null) {
            e = new Entry();
            e.url = key;
            e.method = m;
            e.firstVisit = now;
            map.put(key, e);
        } else {
            e.method = m; // Cập nhật phương thức truy cập mới nhất
            e.visits++;
        }
        e.lastVisit = now;
        save();
    }

    /** Cập nhật tiêu đề cho URL (không tính thêm lượt truy cập). */
    public synchronized void setTitle(String url, String title) {
        String key = normalize(url);
        if (key == null || title == null || title.trim().isEmpty()) return;
        Entry e = map.get(key);
        if (e != null && !title.equals(e.title)) {
            e.title = title;
            save();
        }
    }

    /** Danh sách mới nhất lên đầu. */
    public synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<>(map.values());
        out.sort((a, b) -> b.lastVisit.compareTo(a.lastVisit));
        return out;
    }

    public synchronized void delete(String url) {
        map.remove(url);
        save();
    }

    public synchronized void clear() {
        map.clear();
        lastKey = "";
        save();
    }

    private static String normalize(String url) {
        if (url == null) return null;
        String u = url.trim();
        String low = u.toLowerCase();
        if (!low.startsWith("http://") && !low.startsWith("https://")) return null;
        int h = u.indexOf('#');
        return h == -1 ? u : u.substring(0, h);
    }

    // ================= GHI JSON =================

    private void save() {
        StringBuilder sb = new StringBuilder("[\n");
        Iterator<Entry> it = map.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            sb.append("  {\n");
            sb.append("    \"url\": ").append(quote(e.url)).append(",\n");
            sb.append("    \"method\": ").append(quote(e.method == null ? "GET" : e.method)).append(",\n");
            sb.append("    \"title\": ").append(quote(e.title)).append(",\n");
            sb.append("    \"visits\": ").append(e.visits).append(",\n");
            sb.append("    \"firstVisit\": ").append(quote(e.firstVisit)).append(",\n");
            sb.append("    \"lastVisit\": ").append(quote(e.lastVisit)).append("\n");
            sb.append("  }").append(it.hasNext() ? "," : "").append("\n");
        }
        sb.append("]\n");
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            System.err.println("Không lưu được lịch sử: " + ex.getMessage());
        }
    }

    private static String quote(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ================= ĐỌC JSON (parser nhỏ cho đúng định dạng trên) =================

    private String src;
    private int pos;

    private void load() {
        if (!Files.exists(file)) return;
        try {
            src = Files.readString(file, StandardCharsets.UTF_8);
            pos = 0;
            skipTo('[');
            while (true) {
                skipWs();
                if (pos >= src.length() || src.charAt(pos) == ']') break;
                if (src.charAt(pos) == ',') { pos++; continue; }
                if (src.charAt(pos) != '{') break;
                pos++;
                Entry e = new Entry();
                while (true) {
                    skipWs();
                    char c = src.charAt(pos);
                    if (c == '}') { pos++; break; }
                    if (c == ',') { pos++; continue; }
                    String key = readString();
                    skipTo(':');
                    skipWs();
                    String val = src.charAt(pos) == '"' ? readString() : readBare();
                    switch (key) {
                        case "url": e.url = val; break;
                        case "method": e.method = val; break;
                        case "title": e.title = val; break;
                        case "firstVisit": e.firstVisit = val; break;
                        case "lastVisit": e.lastVisit = val; break;
                        case "visits": e.visits = Integer.parseInt(val.trim()); break;
                        default: break;
                    }
                }
                if (e.url != null) {
                    if (e.lastVisit == null) e.lastVisit = "";
                    if (e.firstVisit == null) e.firstVisit = e.lastVisit;
                    if (e.method == null || e.method.isEmpty()) e.method = "GET";
                    map.put(e.url, e);
                }
            }
        } catch (Exception ex) {
            System.err.println("File lịch sử bị lỗi, bỏ qua: " + ex.getMessage());
            map.clear();
        }
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private void skipTo(char ch) {
        while (pos < src.length() && src.charAt(pos) != ch) pos++;
        pos++;
    }

    private String readBare() {
        int st = pos;
        while (pos < src.length() && ",}\n".indexOf(src.charAt(pos)) == -1) pos++;
        return src.substring(st, pos);
    }

    private String readString() {
        skipTo('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\') {
                char n = src.charAt(pos++);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}