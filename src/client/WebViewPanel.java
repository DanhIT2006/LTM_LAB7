package client;

import java.awt.BorderLayout;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Khung hiển thị web dùng JavaFX WebView (engine WebKit) -> hỗ trợ CSS/JS hiện đại,
 * thay cho JEditorPane (chỉ hỗ trợ HTML 3.2 / CSS 1).
 */
public class WebViewPanel extends JPanel {

    public interface LinkHandler {
        void onLinkClicked(String url);
    }

    public interface FormHandler {
        void onFormSubmitted(String method, String action, String data);
    }

    // JS gửi dữ liệu form về Java qua alert() với tiền tố này (không cần JSObject)
    private static final String FORM_PREFIX = "__JAVAFORM__\n";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0 Safari/537.36 CustomJavaBrowser/3.0";

    // Chặn submit form để Java tự gửi bằng Socket (giữ đúng tính năng POST của bài lab)
    private static final String FORM_JS =
            "(function(){\n" +
            " if (window.__formHooked) return; window.__formHooked = true;\n" +
            " document.addEventListener('submit', function(e){\n" +
            "  var f = e.target;\n" +
            "  if (!f || !f.elements) return;\n" +
            "  var p = [];\n" +
            "  for (var i = 0; i < f.elements.length; i++) {\n" +
            "   var el = f.elements[i];\n" +
            "   if (!el.name || el.disabled) continue;\n" +
            "   var t = (el.type || '').toLowerCase();\n" +
            "   if (t === 'submit' || t === 'button' || t === 'file' || t === 'reset' || t === 'image') continue;\n" +
            "   if ((t === 'checkbox' || t === 'radio') && !el.checked) continue;\n" +
            "   p.push(encodeURIComponent(el.name) + '=' + encodeURIComponent(el.value));\n" +
            "  }\n" +
            "  e.preventDefault();\n" +
            "  alert('__JAVAFORM__\\n' + (f.method || 'get') + '\\n' + f.action + '\\n' + p.join('&'));\n" +
            " }, true);\n" +
            "})();";

    private final JFXPanel jfxPanel = new JFXPanel();
    private WebEngine engine;
    private volatile boolean ownLoadInFlight = false;
    private volatile String lastLoadedUrl = "";
    private volatile LinkHandler linkHandler;
    private volatile FormHandler formHandler;

    public WebViewPanel() {
        super(new BorderLayout());
        add(jfxPanel, BorderLayout.CENTER);
        Platform.setImplicitExit(false);
        Platform.runLater(this::initFx);
    }

    public void setLinkHandler(LinkHandler handler) {
        this.linkHandler = handler;
    }

    public void setFormHandler(FormHandler handler) {
        this.formHandler = handler;
    }

    private void initFx() {
        WebView view = new WebView();
        engine = view.getEngine();
        engine.setUserAgent(USER_AGENT);

        // Nhận dữ liệu form do JS gửi qua alert(): "__JAVAFORM__\n<method>\n<action>\n<data>"
        engine.setOnAlert(event -> {
            String msg = event.getData();
            if (msg != null && msg.startsWith(FORM_PREFIX)) {
                String[] p = msg.substring(FORM_PREFIX.length()).split("\n", 3);
                if (p.length == 3) {
                    SwingUtilities.invokeLater(() -> {
                        if (formHandler != null) formHandler.onFormSubmitted(p[0], p[1], p[2]);
                    });
                }
            }
        });

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                ownLoadInFlight = false;
                String loc = engine.getLocation();
                lastLoadedUrl = loc == null ? "" : loc;
                installFormInterceptor();
            } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                ownLoadInFlight = false;
            }
        });

        // Người dùng bấm link trong trang -> hủy việc WebView tự điều hướng,
        // chuyển cho BrowserGUI xử lý (để ghi lịch sử, thống kê, ...)
        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (ownLoadInFlight) return;
            if (newLoc == null || newLoc.isEmpty() || newLoc.startsWith("about:")) return;
            if (stripFragment(newLoc).equals(stripFragment(lastLoadedUrl))) return; // chỉ nhảy #anchor
            engine.getLoadWorker().cancel();
            SwingUtilities.invokeLater(() -> {
                if (linkHandler != null) linkHandler.onLinkClicked(newLoc);
            });
        });

        jfxPanel.setScene(new Scene(view));
    }

    private void installFormInterceptor() {
        try {
            engine.executeScript(FORM_JS);
        } catch (Exception ignored) {
        }
    }

    // ===== API dùng từ Swing (an toàn gọi từ bất kỳ thread nào) =====

    public void loadUrl(String url) {
        Platform.runLater(() -> {
            ownLoadInFlight = true;
            lastLoadedUrl = url;
            engine.load(url);
        });
    }

    /** Hiển thị HTML có sẵn (dùng cho kết quả POST); baseUrl để tải đúng CSS/ảnh/link tương đối. */
    public void loadHtml(String html, String baseUrl) {
        String content = injectBase(html, baseUrl);
        Platform.runLater(() -> {
            ownLoadInFlight = true;
            lastLoadedUrl = "";
            engine.loadContent(content, "text/html");
        });
    }

    public void clear() {
        Platform.runLater(() -> {
            ownLoadInFlight = true;
            lastLoadedUrl = "";
            engine.loadContent("<html><body></body></html>", "text/html");
        });
    }

    // ===== Tiện ích =====

    private static String stripFragment(String url) {
        int i = url.indexOf('#');
        return i == -1 ? url : url.substring(0, i);
    }

    private static String injectBase(String html, String baseUrl) {
        if (html == null) html = "";
        String tag = "<base href=\"" + baseUrl.replace("\"", "&quot;") + "\">";
        Matcher m = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(m.group() + tag));
        }
        return tag + html;
    }
}