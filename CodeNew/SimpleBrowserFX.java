import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

public class SimpleBrowserFX extends Application {

    static class Result {
        String finalUrl, statusLine, html = "";
        Map<String, List<String>> headers = new LinkedHashMap<>();
        long bytes, millis;
        int redirects;
    }

    private static final String HOME = "https://google.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15";

    private WebEngine engine;
    private WebHistory history;
    private final TextField urlField = new TextField();
    private final TextField postField = new TextField();
    private final ComboBox<String> methodBox = new ComboBox<>();
    private final TextArea sourceArea = new TextArea();
    private final TextArea infoArea = new TextArea();
    private final TabPane tabs = new TabPane();
    private final Label status = new Label(" Sẵn sàng");
    private final HistoryStore store = new HistoryStore("history.json"); // lưu lịch sử ra file JSON
    private boolean suppress = false; // true khi vừa loadContent (POST) -> không fetch lại

    @Override
    public void start(Stage stage) {
        WebView view = new WebView();
        engine = view.getEngine();
        engine.setUserAgent(UA);
        history = engine.getHistory();

        // ---- Thanh điều hướng ----
        Button back = new Button("◀");
        Button fwd = new Button("▶");
        Button reload = new Button("⟳");
        Button home = new Button("⌂");
        Button histBtn = new Button("☰ Lịch sử");
        Button go = new Button("Go");

        back.disableProperty().bind(history.currentIndexProperty().lessThanOrEqualTo(0));
        fwd.disableProperty().bind(Bindings.createBooleanBinding(
                () -> history.getCurrentIndex() >= history.getEntries().size() - 1,
                history.currentIndexProperty(), history.getEntries()));

        back.setOnAction(e -> history.go(-1));
        fwd.setOnAction(e -> history.go(1));
        reload.setOnAction(e -> engine.reload());
        home.setOnAction(e -> { methodBox.setValue("GET"); urlField.setText(HOME); go(); });
        go.setOnAction(e -> go());
        urlField.setOnAction(e -> go());
        postField.setOnAction(e -> go());
        histBtn.setOnAction(e -> showHistory(histBtn));

        methodBox.getItems().addAll("GET", "POST", "HEAD");
        methodBox.setValue("GET");
        urlField.setPromptText("Nhập URL hoặc từ khóa tìm kiếm...");
        HBox.setHgrow(urlField, Priority.ALWAYS);

        HBox nav = new HBox(6, back, fwd, reload, home, urlField, methodBox, go, histBtn);
        nav.setPadding(new Insets(8));

        postField.setPromptText("POST data, vd: user=abc&pass=123");
        HBox postRow = new HBox(6, new Label("POST data:"), postField);
        HBox.setHgrow(postField, Priority.ALWAYS);
        postRow.setPadding(new Insets(0, 8, 6, 8));
        postRow.setVisible(false);
        postRow.setManaged(false);
        methodBox.setOnAction(e -> {
            boolean p = "POST".equals(methodBox.getValue());
            postRow.setVisible(p);
            postRow.setManaged(p);
        });

        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(120);
        bar.setVisible(false);
        bar.progressProperty().bind(engine.getLoadWorker().progressProperty());

        // ---- Tabs ----
        sourceArea.setEditable(false);
        sourceArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12;");
        infoArea.setEditable(false);
        infoArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13;");
        Tab t1 = new Tab("Giao diện", view);
        Tab t2 = new Tab("Mã nguồn HTML", sourceArea);
        Tab t3 = new Tab("Thông tin / Thống kê", infoArea);
        for (Tab t : new Tab[]{t1, t2, t3}) t.setClosable(false);
        tabs.getTabs().addAll(t1, t2, t3);

        HBox bottom = new HBox(10, status, bar);
        bottom.setPadding(new Insets(3, 8, 3, 8));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(nav, postRow));
        root.setCenter(tabs);
        root.setBottom(bottom);

        // ---- Sự kiện engine ----
        engine.titleProperty().addListener((o, a, t) -> {
            stage.setTitle((t == null || t.isEmpty() ? "Simple Browser" : t) + " - Simple Browser");
            String cur = engine.getLocation();
            if (t != null && cur != null) store.setTitle(cur, t);
        });
        engine.locationProperty().addListener((o, a, loc) -> {
            if (!suppress && loc != null && loc.startsWith("http")) {
                urlField.setText(loc);
                store.visit(loc, "GET"); // Duyệt web bình thường trên WebEngine mặc định là GET
            }
        });
        engine.setOnStatusChanged(ev -> status.setText(" " + (ev.getData() == null ? "" : ev.getData())));
        engine.getLoadWorker().stateProperty().addListener((o, old, st) -> {
            bar.setVisible(st == Worker.State.RUNNING);
            if (st == Worker.State.SUCCEEDED) {
                if (suppress) { suppress = false; return; }
                String loc = engine.getLocation();
                if (loc != null && loc.startsWith("http")) analyze("GET", loc, "", false);
            } else if (st == Worker.State.FAILED) {
                status.setText(" Không thể tải trang");
            }
        });

        Scene scene = new Scene(root, 1200, 800);
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case F5: engine.reload(); break;
                case L: if (e.isControlDown()) { urlField.requestFocus(); urlField.selectAll(); } break;
                default: break;
            }
        });
        stage.setScene(scene);
        stage.setTitle("Simple Browser");
        stage.show();

        urlField.setText(HOME);
        go();
    }

    // ---- Xử lý nút Go ----
    private void go() {
        String url = normalize(urlField.getText());
        if (url == null) return;
        String method = methodBox.getValue();
        urlField.setText(url);
        if ("GET".equals(method)) {
            suppress = false;
            engine.load(url);       // WebView tự render + tự lưu lịch sử
        } else {
            analyze(method, url, postField.getText(), true); // POST / HEAD: tự gửi request
        }
    }

    private String normalize(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if (s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) return s;
        if (s.contains(" ") || !(s.contains(".") || s.startsWith("localhost")))
            return "https://www.google.com/search?q=" + URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        return "https://" + s;
    }

    // ---- Gửi request ở thread riêng, rồi cập nhật UI ----
    private void analyze(String method, String url, String body, boolean render) {
        status.setText(" Đang gửi " + method + " " + url + " ...");
        new Thread(() -> {
            try {
                Result r = fetch(method, url, body);
                Platform.runLater(() -> show(method, r, render));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText(" Lỗi: " + ex);
                    infoArea.setText("Không thể " + method + " " + url + "\n\n" + ex);
                    tabs.getSelectionModel().select(2);
                });
            }
        }, "http-worker").start();
    }

    private void show(String method, Result r, boolean render) {
        // Ghi nhận lịch sử với phương thức tương ứng (GET, POST, HEAD)
        store.visit(r.finalUrl, method);

        StringBuilder sb = new StringBuilder();
        sb.append("URL         : ").append(r.finalUrl).append('\n');
        sb.append("Phương thức : ").append(method).append('\n');
        sb.append("Trạng thái  : ").append(r.statusLine).append('\n');
        sb.append("Thời gian   : ").append(r.millis).append(" ms");
        if (r.redirects > 0) sb.append("  (").append(r.redirects).append(" lần redirect)");
        sb.append("\n\n");

        if ("HEAD".equals(method)) {
            sb.append("=== THÔNG TIN TÀI NGUYÊN (HEAD) ===\n");
            for (String h : new String[]{"Content-Type", "Content-Length", "Last-Modified", "Server", "Date", "Content-Encoding"})
                sb.append(String.format("%-20s: %s%n", h, first(r, h)));
            sb.append("\n=== TẤT CẢ HEADER ===\n");
            appendHeaders(sb, r);
            sourceArea.setText("(HEAD không trả về nội dung)");
            infoArea.setText(sb.toString());
            tabs.getSelectionModel().select(2);
        } else {
            String html = r.html;
            sb.append("=== THỐNG KÊ TRANG HTML ===\n");
            sb.append(String.format("%-28s: %,d ký tự (%,d bytes)%n", "Chiều dài HTML", html.length(), r.bytes));
            sb.append(String.format("%-28s: %d%n", "Số tag <p>", count(html, "p")));
            sb.append(String.format("%-28s: %d%n", "Số tag <div>", count(html, "div")));
            sb.append(String.format("%-28s: %d%n", "Số tag <span>", count(html, "span")));
            sb.append(String.format("%-28s: %d%n", "Số tag <img>", count(html, "img")));
            sb.append("\n=== HEADER PHẢN HỒI ===\n");
            appendHeaders(sb, r);
            infoArea.setText(sb.toString());
            sourceArea.setText(html);
            if (render) { // POST: WebEngine không tự POST được nên render nội dung trả về
                suppress = true;
                urlField.setText(r.finalUrl);
                String base = "<base href=\"" + r.finalUrl.replace("\"", "%22") + "\">";
                Matcher hm = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
                String page = hm.find()
                        ? html.substring(0, hm.end()) + base + html.substring(hm.end())
                        : base + html;
                engine.loadContent(page, "text/html");
                tabs.getSelectionModel().select(0);
            }
        }
        status.setText(" " + r.statusLine + " | " + r.millis + " ms | " + r.bytes + " bytes");
    }

    private void showHistory(Button anchor) {
        Stage st = new Stage();
        st.setTitle("Lịch sử duyệt web  (" + store.getFile() + ")");

        ObservableList<HistoryStore.Entry> all = FXCollections.observableArrayList(store.list());
        FilteredList<HistoryStore.Entry> filtered = new FilteredList<>(all, e -> true);

        TextField search = new TextField();
        search.setPromptText("Tìm theo tiêu đề hoặc URL...");
        search.textProperty().addListener((o, a, q) -> {
            String k = q == null ? "" : q.toLowerCase().trim();
            filtered.setPredicate(e -> k.isEmpty()
                    || e.url.toLowerCase().contains(k) 
                    || e.title.toLowerCase().contains(k)
                    || (e.method != null && e.method.toLowerCase().contains(k)));
        });

        TableView<HistoryStore.Entry> table = new TableView<>(filtered);
        TableColumn<HistoryStore.Entry, String> cTime = new TableColumn<>("Lần cuối");
        cTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().lastVisit));
        cTime.setPrefWidth(140);

        TableColumn<HistoryStore.Entry, String> cMethod = new TableColumn<>("Phương thức");
        cMethod.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().method == null || c.getValue().method.isEmpty() ? "GET" : c.getValue().method));
        cMethod.setPrefWidth(90);

        TableColumn<HistoryStore.Entry, String> cTitle = new TableColumn<>("Tiêu đề");
        cTitle.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().title.isEmpty() ? "(chưa có tiêu đề)" : c.getValue().title));
        cTitle.setPrefWidth(260);

        TableColumn<HistoryStore.Entry, String> cUrl = new TableColumn<>("URL");
        cUrl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().url));
        cUrl.setPrefWidth(420);

        TableColumn<HistoryStore.Entry, String> cCount = new TableColumn<>("Số lần");
        cCount.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().visits)));
        cCount.setPrefWidth(60);

        table.getColumns().addAll(cTime, cMethod, cTitle, cUrl, cCount);
        table.setPlaceholder(new Label("Chưa có lịch sử"));

        // Nhấp đúp = mở lại đúng URL đó
        table.setRowFactory(tv -> {
            TableRow<HistoryStore.Entry> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    methodBox.setValue(row.getItem().method != null ? row.getItem().method : "GET");
                    urlField.setText(row.getItem().url);
                    go();
                    st.close();
                }
            });
            return row;
        });

        Button del = new Button("Xóa mục đã chọn");
        del.setOnAction(e -> {
            HistoryStore.Entry sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) { store.delete(sel.url); all.remove(sel); }
        });
        Button clr = new Button("Xóa tất cả");
        clr.setOnAction(e -> {
            Alert al = new Alert(Alert.AlertType.CONFIRMATION, "Xóa toàn bộ lịch sử?", ButtonType.YES, ButtonType.NO);
            al.showAndWait().ifPresent(bt -> { if (bt == ButtonType.YES) { store.clear(); all.clear(); } });
        });

        HBox tools = new HBox(8, search, del, clr);
        HBox.setHgrow(search, Priority.ALWAYS);
        tools.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        st.setScene(new Scene(new VBox(tools, table), 980, 520));
        st.show();
    }

    // ---- HTTP thủ công ----
    private Result fetch(String method, String urlStr, String body) throws Exception {
        long t0 = System.currentTimeMillis();
        Result res = new Result();
        for (int hop = 0; hop <= 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestMethod(method);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
            c.setRequestProperty("Accept-Encoding", "gzip");
            if ("POST".equals(method)) {
                byte[] data = body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = c.getOutputStream()) { os.write(data); }
            }
            int code = c.getResponseCode();
            res.finalUrl = urlStr;
            res.statusLine = "HTTP " + code + " " + (c.getResponseMessage() == null ? "" : c.getResponseMessage());
            res.headers = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> h : c.getHeaderFields().entrySet())
                if (h.getKey() != null) res.headers.put(h.getKey(), h.getValue());

            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                urlStr = new URL(new URL(urlStr), c.getHeaderField("Location")).toString();
                if (code == 303 || ((code == 301 || code == 302) && "POST".equals(method))) {
                    if (!"HEAD".equals(method)) method = "GET";
                    body = "";
                }
                res.redirects++;
                c.disconnect();
                continue;
            }
            if (!"HEAD".equals(method)) {
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
                    byte[] raw = in.readAllBytes();
                    in.close();
                    res.bytes = raw.length;
                    res.html = new String(raw, detectCharset(c.getContentType(), raw));
                }
            }
            res.millis = System.currentTimeMillis() - t0;
            return res;
        }
        throw new IOException("Quá nhiều lần chuyển hướng (redirect)");
    }

    private Charset detectCharset(String ct, byte[] raw) {
        try {
            if (ct != null) {
                Matcher m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(ct);
                if (m.find()) return Charset.forName(m.group(1));
            }
            String head = new String(raw, 0, Math.min(raw.length, 2048), "ISO-8859-1");
            Matcher m = Pattern.compile("charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(head);
            if (m.find()) return Charset.forName(m.group(1));
        } catch (Exception ignored) { }
        return Charset.forName("UTF-8");
    }

    private static int count(String html, String tag) {
        Matcher m = Pattern.compile("<" + tag + "(?=[\\s/>])", Pattern.CASE_INSENSITIVE).matcher(html);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String first(Result r, String name) {
        for (Map.Entry<String, List<String>> e : r.headers.entrySet())
            if (e.getKey().equalsIgnoreCase(name)) return String.join(", ", e.getValue());
        return "(không có)";
    }

    private static void appendHeaders(StringBuilder sb, Result r) {
        for (Map.Entry<String, List<String>> e : r.headers.entrySet())
            sb.append(e.getKey()).append(": ").append(String.join(", ", e.getValue())).append('\n');
    }

    public static void main(String[] args) {
        launch(args);
    }
}