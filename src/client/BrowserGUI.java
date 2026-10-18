package client;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.FormSubmitEvent;
public class BrowserGUI extends JFrame {

    private JTextField txtUrl;
    private JRadioButton rdoGet, rdoPost, rdoHead;
    private JTextArea txtPostData;
    private JButton btnGo;

    private JTextArea txtResult;
    private JEditorPane webRenderPane;
    private JLabel lblStatus; // Thanh trạng thái dưới đáy

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

        // Hàng 1: URL & Nút GO
        JPanel row1 = new JPanel(new BorderLayout(5, 5));
        row1.add(new JLabel(" URL: "), BorderLayout.WEST);
        txtUrl = new JTextField("http://26.60.13.119:8080/index.html");
        btnGo = new JButton(" GO ");
        btnGo.setFont(new Font("SansSerif", Font.BOLD, 12));
        row1.add(txtUrl, BorderLayout.CENTER);
        row1.add(btnGo, BorderLayout.EAST);
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

        // Ô dữ liệu POST
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
        
        // TÍNH NĂNG MỚI: Bấm vào Link chuyển trang ngay trên giao diện web
        webRenderPane.addHyperlinkListener(e -> {
            if (e instanceof FormSubmitEvent) {
        FormSubmitEvent formEvt = (FormSubmitEvent) e;
        txtUrl.setText(formEvt.getURL().toString());

        if (formEvt.getMethod() == FormSubmitEvent.MethodType.POST) {
            rdoPost.setSelected(true);
            txtPostData.setEnabled(true);
            txtPostData.setText(formEvt.getData()); // Tự động lấy chuỗi user=admin&pass=123456
        } else {
            rdoGet.setSelected(true);
            txtPostData.setEnabled(false);
        }
        onExecute(); 
    }
            else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String targetUrl = e.getURL() != null ? e.getURL().toString() : e.getDescription();
                if (targetUrl != null && !targetUrl.isEmpty()) {
                    txtUrl.setText(targetUrl);
                    rdoGet.setSelected(true);
                    txtPostData.setEnabled(false);
                    onExecute();
                }
            }
        });

        tabPane.addTab("🌐 Giao diện Trang Web (Bonus)", new JScrollPane(webRenderPane));
        add(tabPane, BorderLayout.CENTER);

        // --- TÍNH NĂNG MỚI: THANH TRẠNG THÁI STATUS BAR (SOUTH) ---
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

        btnGo.addActionListener(e -> onExecute());
    }

    private void onExecute() {
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
                    txtUrl.setText(res.finalUrl); // Cập nhật URL cuối nếu có chuyển hướng
                    
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
                        
                        // Set base URL để nhận diện các liên kết tương đối
                        try {
                            webRenderPane.setPage(res.finalUrl);
                        } catch (Exception ignore) {
                            webRenderPane.setText(res.body);
                        }
                    }

                    // Cập nhật thanh trạng thái dưới cùng
                    double sizeKb = res.htmlLength / 1024.0;
                    lblStatus.setText(String.format(" Mã trạng thái: %d %s | Thời gian: %d ms | Dung lượng: %.2f KB", 
                            res.statusCode, res.statusText, res.responseTimeMs, sizeKb));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtResult.setText("Lỗi: " + ex.getMessage());
                    lblStatus.setText(" ❌ Kết nối thất bại: " + ex.getMessage());
                });
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BrowserGUI().setVisible(true));
    }
}