package client;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.FormSubmitEvent;
import javax.swing.text.html.HTMLDocument;

public class BrowserGUI extends JFrame {

    // ====== Một mục trong lịch sử ======
    private static class HistoryEntry {
        final String url, method, postData, title;
        final long time;

        HistoryEntry(String url, String method, String postData, String title) {
            this.url = url;
            this.method = method;
            this.postData = postData;
            this.title = title;
            this.time = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            String t = new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date(time));
            String m = "GET".equals(method) ? "" : "  [" + method + "]";
            return t + "   " + title + "  —  " + url + m;
        }
    }

    // Ngăn xếp Back/Forward
    private final List<HistoryEntry> navStack = new ArrayList<>();
    private int navIndex = -1;
    // Nhật ký lịch sử đầy đủ
    private final List<HistoryEntry> historyLog = new ArrayList<>();

    private JTextField txtUrl;
    private JRadioButton rdoGet, rdoPost, rdoHead;
    private JTextArea txtPostData;
    private JButton btnGo, btnBack, btnForward, btnReload, btnHistory;

    private JTextArea txtResult;
    private JEditorPane webRenderPane;
    private JLabel lblStatus;

    public BrowserGUI() {
        setTitle("🌐 Custom Java Web Browser (Group 1)");
        setSize(950, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // --- THANH ĐỊA CHỈ & THAO TÁC (TOP) ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createTitledBorder("Thanh điều khiển Browser"));

        // Hàng 1: Back / Forward / Reload | URL | GO | Lịch sử
        JPanel row1 = new JPanel(new BorderLayout(5, 5));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        btnBack = new JButton("◀");
        btnForward = new JButton("▶");
        btnReload = new JButton("⟳");
        btnBack.setToolTipText("Quay lại (Alt+←)");
        btnForward.setToolTipText("Tiến tới (Alt+→)");
        btnReload.setToolTipText("Tải lại (F5)");
        btnBack.setEnabled(false);
        btnForward.setEnabled(false);
        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnReload);
        row1.add(navButtons, BorderLayout.WEST);

        txtUrl = new JTextField("http://26.60.13.119:8080/index.html");
        row1.add(txtUrl, BorderLayout.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        btnGo = new JButton(" GO ");
        btnGo.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnHistory = new JButton("🕘 Lịch sử");
        btnHistory.setToolTipText("Xem lịch sử (Ctrl+H)");
        rightButtons.add(btnGo);
        rightButtons.add(btnHistory);
        row1.add(rightButtons, BorderLayout.EAST);
        topPanel.add(row1);

        // Hàng 2: Radio Button Phương thức
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Phương thức: "));
        rdoGet = new JRadioButton("GET", true);
        rdoPost = new JRadioButton("POST");
        rdoHead = new JRadioButton("HEAD");
        ButtonGroup group = new ButtonGroup();
        group.add(rdoGet); group.add(rdoPost); group.add(rdoHead);
        row2.add(rdoGet); row2.add(rdoPost); row2.add(rdoHead);

        row2.add(new JLabel("   Dữ liệu POST: "));
        txtPostData = new JTextArea(1, 25);
        txtPostData.setText("user=admin&pass=123");
        txtPostData.setEnabled(false);
        row2.add(new JScrollPane(txtPostData));

        topPanel.add(row2);
        add(topPanel, BorderLayout.NORTH);

        // --- KHU VỰC HIỂN THỊ KẾT QUẢ (CENTER) ---
        JTabbedPane tabPane = new JTabbedPane();

        txtResult = new JTextArea();
        txtResult.setEditable(false);
        txtResult.setFont(new Font("Monospaced", Font.PLAIN, 13));
        tabPane.addTab("📊 Báo cáo phân tích / Header", new JScrollPane(txtResult));

        webRenderPane = new JEditorPane();
        webRenderPane.setEditable(false);
        webRenderPane.setContentType("text/html");

        // Bấm vào Link / Submit form trên giao diện web -> điều hướng mới (có ghi lịch sử)
        webRenderPane.addHyperlinkListener(e -> {
            if (e instanceof FormSubmitEvent) {
                FormSubmitEvent formEvt = (FormSubmitEvent) e;
                txtUrl.setText(formEvt.getURL().toString());

                if (formEvt.getMethod() == FormSubmitEvent.MethodType.POST) {
                    rdoPost.setSelected(true);
                    txtPostData.setEnabled(true);
                    txtPostData.setText(formEvt.getData());
                } else {
                    rdoGet.setSelected(true);
                    txtPostData.setEnabled(false);
                }
                execute(true);
            } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String targetUrl = e.getURL() != null ? e.getURL().toString() : e.getDescription();
                if (targetUrl != null && !targetUrl.isEmpty()) {
                    txtUrl.setText(targetUrl);
                    rdoGet.setSelected(true);
                    txtPostData.setEnabled(false);
                    execute(true);
                }
            }
        });

        tabPane.addTab("🌐 Giao diện Trang Web (Bonus)", new JScrollPane(webRenderPane));
        add(tabPane, BorderLayout.CENTER);

        // --- THANH TRẠNG THÁI (SOUTH) ---
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        lblStatus = new JLabel(" Trạng thái: Sẵn sàng");
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(lblStatus);
        add(statusPanel, BorderLayout.SOUTH);

        // --- SỰ KIỆN GIAO DIỆN ---
        rdoGet.addActionListener(e -> txtPostData.setEnabled(false));
        rdoHead.addActionListener(e -> txtPostData.setEnabled(false));
        rdoPost.addActionListener(e -> txtPostData.setEnabled(true));

        btnGo.addActionListener(e -> execute(true));
        txtUrl.addActionListener(e -> execute(true)); // Nhấn Enter trong ô URL
        btnBack.addActionListener(e -> goBack());
        btnForward.addActionListener(e -> goForward());
        btnReload.addActionListener(e -> reload());
        btnHistory.addActionListener(e -> showHistoryDialog());

        // Phím tắt giống Chrome
        bindKey("alt LEFT", this::goBack);
        bindKey("alt RIGHT", this::goForward);
        bindKey("F5", this::reload);
        bindKey("ctrl R", this::reload);
        bindKey("ctrl H", this::showHistoryDialog);
    }

    private void bindKey(String keyStroke, Runnable action) {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
        root.getActionMap().put(keyStroke, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ================= ĐIỀU HƯỚNG =================

    private void goBack() {
        if (navIndex > 0) {
            navIndex--;
            applyEntryToUI(navStack.get(navIndex));
            updateNavButtons();
            execute(false);
        }
    }

    private void goForward() {
        if (navIndex < navStack.size() - 1) {
            navIndex++;
            applyEntryToUI(navStack.get(navIndex));
            updateNavButtons();
            execute(false);
        }
    }

    private void reload() {
        execute(false);
    }

    private void updateNavButtons() {
        btnBack.setEnabled(navIndex > 0);
        btnForward.setEnabled(navIndex < navStack.size() - 1);
    }

    // Đưa thông tin của một mục lịch sử lên giao diện (URL, phương thức, dữ liệu POST)
    private void applyEntryToUI(HistoryEntry h) {
        txtUrl.setText(h.url);
        switch (h.method) {
            case "POST":
                rdoPost.setSelected(true);
                txtPostData.setEnabled(true);
                txtPostData.setText(h.postData);
                break;
            case "HEAD":
                rdoHead.setSelected(true);
                txtPostData.setEnabled(false);
                break;
            default:
                rdoGet.setSelected(true);
                txtPostData.setEnabled(false);
        }
    }

    /**
     * Thực thi request theo dữ liệu hiện có trên giao diện.
     * @param pushHistory true = điều hướng mới (ghi lịch sử); false = Back/Forward/Reload (không ghi)
     */
    private void execute(boolean pushHistory) {
        String url = txtUrl.getText().trim();
        String method = rdoGet.isSelected() ? "GET" : (rdoPost.isSelected() ? "POST" : "HEAD");
        String postData = txtPostData.getText().trim();

        txtResult.setText("Đang kết nối tới " + url + "...\n");
        webRenderPane.setText("");
        lblStatus.setText(" ⏳ Đang tải dữ liệu từ " + url + "...");

        new Thread(() -> {
            try {
                HttpSocketClient.ResponseData res = HttpSocketClient.executeRequest(url, method, postData);

                SwingUtilities.invokeLater(() -> {
                    txtUrl.setText(res.finalUrl);

                    if ("HEAD".equals(method)) {
                        txtResult.setText("=== CÁC THÔNG TIN TÀI NGUYÊN (HTTP HEADERS) ===\n\n" + res.headers);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("=== PHÂN TÍCH KẾT QUẢ TRẢ VỀ ===\n");
                        sb.append("• Phương thức: ").append(method).append("\n");
                        sb.append("• Chiều dài trang HTML: ").append(res.htmlLength).append(" ký tự\n\n");
                        sb.append("--- THỐNG KÊ CÁC THẺ HTML ---\n");
                        sb.append("• Thẻ <p>:    ").append(res.pCount).append("\n");
                        sb.append("• Thẻ <div>:  ").append(res.divCount).append("\n");
                        sb.append("• Thẻ <span>: ").append(res.spanCount).append("\n");
                        sb.append("• Thẻ <img>:  ").append(res.imgCount).append("\n");
                        txtResult.setText(sb.toString());

                        try {
                            if ("POST".equals(method)) {
                                // setPage() sẽ GET lại URL nên sẽ sai với kết quả POST -> hiển thị trực tiếp body
                                webRenderPane.setText(res.body);
                                ((HTMLDocument) webRenderPane.getDocument()).setBase(new URL(res.finalUrl));
                                webRenderPane.setCaretPosition(0);
                            } else {
                                webRenderPane.setPage(res.finalUrl);
                            }
                        } catch (Exception ignore) {
                            webRenderPane.setText(res.body);
                        }
                    }

                    // Ghi lịch sử nếu là điều hướng mới
                    if (pushHistory) {
                        String m = res.redirected ? "GET" : method; // redirect luôn chuyển thành GET
                        String pd = res.redirected ? "" : postData;
                        recordNavigation(new HistoryEntry(res.finalUrl, m, pd, extractTitle(res)));
                    }
                    updateNavButtons();

                    double sizeKb = res.htmlLength / 1024.0;
                    lblStatus.setText(String.format(" Mã trạng thái: %d %s | Thời gian: %d ms | Dung lượng: %.2f KB",
                            res.statusCode, res.statusText, res.responseTimeMs, sizeKb));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtResult.setText("Lỗi: " + ex.getMessage());
                    lblStatus.setText(" ❌ Kết nối thất bại: " + ex.getMessage());
                    updateNavButtons();
                });
            }
        }).start();
    }

    private void recordNavigation(HistoryEntry entry) {
        while (navStack.size() > navIndex + 1) {
            navStack.remove(navStack.size() - 1);
        }
        navStack.add(entry);
        navIndex = navStack.size() - 1;
        historyLog.add(entry);
    }

    private static String extractTitle(HttpSocketClient.ResponseData res) {
        if (res.body != null) {
            Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(res.body);
            if (m.find()) {
                String t = m.group(1).trim().replaceAll("\\s+", " ");
                if (!t.isEmpty()) return t;
            }
        }
        return "(Không có tiêu đề)";
    }

    // ================= CỬA SỔ LỊCH SỬ =================

    private void showHistoryDialog() {
        JDialog dlg = new JDialog(this, "🕘 Lịch sử duyệt web", false);
        dlg.setSize(760, 480);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(6, 6));

        DefaultListModel<HistoryEntry> model = new DefaultListModel<>();
        JTextField txtSearch = new JTextField();
        txtSearch.setToolTipText("Tìm theo tiêu đề hoặc URL");

        Runnable refill = () -> {
            model.clear();
            String q = txtSearch.getText().trim().toLowerCase();
            for (int i = historyLog.size() - 1; i >= 0; i--) { // Mới nhất lên đầu
                HistoryEntry h = historyLog.get(i);
                if (q.isEmpty() || h.url.toLowerCase().contains(q) || h.title.toLowerCase().contains(q)) {
                    model.addElement(h);
                }
            }
        };
        refill.run();

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        searchPanel.add(new JLabel("🔍 Tìm kiếm: "), BorderLayout.WEST);
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        dlg.add(searchPanel, BorderLayout.NORTH);

        JList<HistoryEntry> list = new JList<>(model);
        list.setFont(new Font("SansSerif", Font.PLAIN, 13));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);

        Runnable openSelected = () -> {
            HistoryEntry h = list.getSelectedValue();
            if (h != null) {
                applyEntryToUI(h);
                execute(true);
            }
        };

        // Double-click để mở lại
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected.run();
            }
        });

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refill.run(); }
            public void removeUpdate(DocumentEvent e) { refill.run(); }
            public void changedUpdate(DocumentEvent e) { refill.run(); }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOpen = new JButton("Mở");
        JButton btnDelOne = new JButton("Xóa mục đã chọn");
        JButton btnDelAll = new JButton("Xóa toàn bộ lịch sử");
        JButton btnClose = new JButton("Đóng");
        buttons.add(btnOpen);
        buttons.add(btnDelOne);
        buttons.add(btnDelAll);
        buttons.add(btnClose);
        dlg.add(buttons, BorderLayout.SOUTH);

        btnOpen.addActionListener(e -> openSelected.run());
        btnDelOne.addActionListener(e -> {
            HistoryEntry h = list.getSelectedValue();
            if (h != null) {
                historyLog.remove(h);
                refill.run();
            }
        });
        btnDelAll.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(dlg, "Xóa toàn bộ lịch sử duyệt web?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                historyLog.clear();
                refill.run();
            }
        });
        btnClose.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BrowserGUI().setVisible(true));
    }
}