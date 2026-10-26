package server;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.text.*;

public class ServerGUI extends JFrame {

    private static final Pattern STATUS_PATTERN = Pattern.compile("-> (\\d{3}) ");

    private final JTextField txtPort;
    private final JButton btnStart, btnStop, btnClear, btnSave;
    private final JCheckBox chkAutoScroll;
    private final JTextPane logPane;
    private final StyledDocument doc;
    private final JLabel lblStatus;

    private final Style styleInfo, styleOk, styleWarn, styleError;

    private WebServer server;
    private int requestCount = 0;

    public ServerGUI() {
        setTitle("🖥 Java Web Server - Bảng điều khiển");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // --- THANH ĐIỀU KHIỂN (TOP) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Điều khiển Server"));
        topPanel.add(new JLabel("Port:"));
        txtPort = new JTextField("8080", 6);
        topPanel.add(txtPort);

        btnStart = new JButton("▶ Start");
        btnStop = new JButton("■ Stop");
        btnStop.setEnabled(false);
        btnClear = new JButton("🗑 Xóa log");
        btnSave = new JButton("💾 Lưu log");
        chkAutoScroll = new JCheckBox("Tự cuộn", true);

        topPanel.add(btnStart);
        topPanel.add(btnStop);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(btnClear);
        topPanel.add(btnSave);
        topPanel.add(chkAutoScroll);
        add(topPanel, BorderLayout.NORTH);

        // --- KHU VỰC LOG (CENTER) ---
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(30, 30, 30));
        logPane.setFont(new Font("Monospaced", Font.PLAIN, 13));
        doc = logPane.getStyledDocument();

        styleInfo = doc.addStyle("info", null);
        StyleConstants.setForeground(styleInfo, new Color(100, 180, 255));
        styleOk = doc.addStyle("ok", null);
        StyleConstants.setForeground(styleOk, new Color(120, 220, 120));
        styleWarn = doc.addStyle("warn", null);
        StyleConstants.setForeground(styleWarn, new Color(255, 200, 80));
        styleError = doc.addStyle("error", null);
        StyleConstants.setForeground(styleError, new Color(255, 100, 100));

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createTitledBorder("Nhật ký Server (Log)"));
        add(scroll, BorderLayout.CENTER);

        // --- THANH TRẠNG THÁI (SOUTH) ---
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        lblStatus = new JLabel();
        statusPanel.add(lblStatus);
        add(statusPanel, BorderLayout.SOUTH);
        updateStatus(false);

        // --- SỰ KIỆN ---
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        btnClear.addActionListener(e -> logPane.setText(""));
        btnSave.addActionListener(e -> saveLog());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (server != null) server.stop();
                dispose();
                System.exit(0);
            }
        });
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ (1 - 65535)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            requestCount = 0;
            server = new WebServer(port, this::appendLog);
            server.start();
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            txtPort.setEnabled(false);
            updateStatus(true);
        } catch (IOException ex) {
            appendLog("Không thể khởi động server: " + ex.getMessage());
        }
    }

    private void stopServer() {
        if (server != null) server.stop();
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        txtPort.setEnabled(true);
        updateStatus(false);
    }

    // Có thể gọi từ bất kỳ thread nào -> đẩy về Event Dispatch Thread
    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            Style style = styleInfo;
            Matcher m = STATUS_PATTERN.matcher(msg);
            if (m.find()) {
                requestCount++;
                int code = Integer.parseInt(m.group(1));
                style = code < 400 ? styleOk : (code < 500 ? styleWarn : styleError);
            } else if (msg.startsWith("Lỗi") || msg.startsWith("Không thể")) {
                style = styleError;
            }
            try {
                doc.insertString(doc.getLength(), msg + "\n", style);
            } catch (BadLocationException ignored) {}

            if (chkAutoScroll.isSelected()) {
                logPane.setCaretPosition(doc.getLength());
            }
            updateStatus(server != null && server.isRunning());
        });
    }

    private void updateStatus(boolean running) {
        lblStatus.setText(" Trạng thái: " + (running ? "🟢 Đang chạy" : "🔴 Đã dừng")
                + "  |  Số request: " + requestCount);
    }

    private void saveLog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("server.log"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(chooser.getSelectedFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                fw.write(logPane.getDocument().getText(0, logPane.getDocument().getLength()));
            } catch (IOException | BadLocationException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi lưu file: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerGUI().setVisible(true));
    }
}