package client;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * ChatClient — Java Swing real-time chat client.
 *
 * On first launch shows a username prompt.
 * Left panel = list of online users (refreshes every 5s).
 * Right panel = conversation with selected user.
 * Messages are persisted on the server (SQLite) and loaded on open.
 */
public class ChatClient extends JFrame {

    private static final String SERVER = "http://localhost:8765";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();

    // ── state ──────────────────────────────────────────────────────
    private String myUsername;
    private String chatPartner;      // currently selected user

    // ── UI components ──────────────────────────────────────────────
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private JPanel messageArea;
    private JScrollPane messageScroll;
    private JTextField messageInput;
    private JButton sendButton;
    private JLabel chatWithLabel;
    private JLabel statusLabel;

    // ── SSE thread ─────────────────────────────────────────────────
    private Thread sseThread;
    private volatile boolean sseRunning = false;

    // Colours
    private static final Color BG_DARK      = new Color(18, 18, 24);
    private static final Color BG_PANEL     = new Color(26, 26, 36);
    private static final Color BG_SIDEBAR   = new Color(20, 20, 30);
    private static final Color ACCENT       = new Color(99, 102, 241);
    private static final Color ACCENT_LIGHT = new Color(129, 140, 248);
    private static final Color BUBBLE_MINE  = new Color(79, 70, 229);
    private static final Color BUBBLE_OTHER = new Color(35, 35, 50);
    private static final Color TEXT_PRIMARY = new Color(236, 236, 255);
    private static final Color TEXT_MUTED   = new Color(120, 120, 160);
    private static final Color INPUT_BG     = new Color(30, 30, 45);
    private static final Color BORDER_COLOR = new Color(50, 50, 70);
    private static final Color ONLINE_DOT   = new Color(52, 211, 153);
    private static final Color SELECTED_BG  = new Color(55, 48, 100);

    public static void main(String[] args) {
        // Ask for username before showing window
        String username = JOptionPane.showInputDialog(
            null,
            "Enter your username to join the chat:",
            "Welcome to JavaChat",
            JOptionPane.PLAIN_MESSAGE
        );
        if (username == null || username.isBlank()) {
            System.exit(0);
        }
        username = username.trim();
        final String u = username;
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient(u);
            client.setVisible(true);
            client.loginToServer();
        });
    }

    public ChatClient(String username) {
        this.myUsername = username;
        setTitle("JavaChat — " + username);
        setSize(900, 640);
        setMinimumSize(new Dimension(700, 480));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();
    }

    // ──────────────────────────────────────────────────────────────
    //  UI Construction
    // ──────────────────────────────────────────────────────────────

    private void initUI() {
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("ScrollPane.background", BG_DARK);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        setContentPane(root);

        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildChatPanel(), BorderLayout.CENTER);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(new EmptyBorder(18, 16, 14, 16));

        JLabel appTitle = new JLabel("JavaChat");
        appTitle.setFont(new Font("Dialog", Font.BOLD, 18));
        appTitle.setForeground(ACCENT_LIGHT);

        JLabel meLabel = new JLabel("You: " + myUsername);
        meLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        meLabel.setForeground(TEXT_MUTED);

        header.add(appTitle, BorderLayout.NORTH);
        header.add(meLabel, BorderLayout.SOUTH);

        // Users header
        JLabel usersHeader = new JLabel("ONLINE USERS");
        usersHeader.setFont(new Font("Dialog", Font.BOLD, 10));
        usersHeader.setForeground(TEXT_MUTED);
        usersHeader.setBorder(new EmptyBorder(4, 16, 6, 16));

        JPanel usersHeaderPanel = new JPanel(new BorderLayout());
        usersHeaderPanel.setBackground(BG_SIDEBAR);
        usersHeaderPanel.add(usersHeader);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setBackground(BG_SIDEBAR);
        topSection.add(header, BorderLayout.NORTH);
        topSection.add(usersHeaderPanel, BorderLayout.CENTER);

        // User list
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setForeground(TEXT_PRIMARY);
        userList.setFont(new Font("Dialog", Font.PLAIN, 13));
        userList.setSelectionBackground(SELECTED_BG);
        userList.setSelectionForeground(TEXT_PRIMARY);
        userList.setCellRenderer(new UserCellRenderer());
        userList.setFixedCellHeight(44);
        userList.setBorder(new EmptyBorder(0, 0, 0, 0));

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = userList.getSelectedValue();
                if (selected != null && !selected.equals(myUsername)) {
                    openConversation(selected);
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(userList);
        listScroll.setBackground(BG_SIDEBAR);
        listScroll.getViewport().setBackground(BG_SIDEBAR);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getVerticalScrollBar().setBackground(BG_SIDEBAR);

        // Status
        statusLabel = new JLabel("Connecting...");
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setBorder(new EmptyBorder(8, 16, 8, 16));

        sidebar.add(topSection, BorderLayout.NORTH);
        sidebar.add(listScroll, BorderLayout.CENTER);
        sidebar.add(statusLabel, BorderLayout.SOUTH);

        return sidebar;
    }

    private JPanel buildChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(BG_PANEL);

        // Chat header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_PANEL);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
            new EmptyBorder(14, 20, 14, 20)
        ));
        chatWithLabel = new JLabel("Select a user to start chatting");
        chatWithLabel.setFont(new Font("Dialog", Font.BOLD, 15));
        chatWithLabel.setForeground(TEXT_PRIMARY);
        chatHeader.add(chatWithLabel, BorderLayout.WEST);

        // Messages area
        messageArea = new JPanel();
        messageArea.setLayout(new BoxLayout(messageArea, BoxLayout.Y_AXIS));
        messageArea.setBackground(BG_PANEL);
        messageArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        messageScroll = new JScrollPane(messageArea);
        messageScroll.setBackground(BG_PANEL);
        messageScroll.getViewport().setBackground(BG_PANEL);
        messageScroll.setBorder(BorderFactory.createEmptyBorder());
        messageScroll.getVerticalScrollBar().setBackground(BG_PANEL);
        messageScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(BG_PANEL);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            new EmptyBorder(12, 16, 12, 16)
        ));

        messageInput = new JTextField();
        messageInput.setBackground(INPUT_BG);
        messageInput.setForeground(TEXT_PRIMARY);
        messageInput.setCaretColor(ACCENT_LIGHT);
        messageInput.setFont(new Font("Dialog", Font.PLAIN, 13));
        messageInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(8, 12, 8, 12)
        ));
        messageInput.setEnabled(false);
        messageInput.addActionListener(e -> sendMessage());
        messageInput.putClientProperty("JTextField.placeholderText", "Type a message...");

        sendButton = new JButton("Send");
        sendButton.setBackground(ACCENT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Dialog", Font.BOLD, 13));
        sendButton.setEnabled(false);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(8, 20, 8, 20));
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendMessage());
        sendButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (sendButton.isEnabled()) sendButton.setBackground(ACCENT_LIGHT);
            }
            public void mouseExited(MouseEvent e) {
                sendButton.setBackground(ACCENT);
            }
        });

        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(chatHeader, BorderLayout.NORTH);
        chatPanel.add(messageScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        return chatPanel;
    }

    // ──────────────────────────────────────────────────────────────
    //  Network
    // ──────────────────────────────────────────────────────────────

    private void loginToServer() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER + "/login?username=" + encode(myUsername)))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
                HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                SwingUtilities.invokeLater(() -> statusLabel.setText("Online ✓"));
                startSSE();
                startUserPoller();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    statusLabel.setText("Server offline — start ChatServer first"));
                e.printStackTrace();
            }
        });
    }

    private void startSSE() {
        sseRunning = true;
        sseThread = new Thread(() -> {
            while (sseRunning) {
                try {
                    URL url = new URL(SERVER + "/events?username=" + encode(myUsername));
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setReadTimeout(60_000);
                    conn.setConnectTimeout(5_000);
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null && sseRunning) {
                            if (line.startsWith("data: ")) {
                                String json = line.substring(6).trim();
                                handleIncomingMessage(json);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (sseRunning) {
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        });
        sseThread.setDaemon(true);
        sseThread.start();
    }

    private void startUserPoller() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER + "/users"))
                    .GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                List<String> users = parseJsonArray(resp.body());
                SwingUtilities.invokeLater(() -> refreshUserList(users));
            } catch (Exception ignored) {}
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void sendMessage() {
        if (chatPartner == null) return;
        String content = messageInput.getText().trim();
        if (content.isEmpty()) return;

        String json = "{\"sender\":\"" + esc(myUsername) + "\"," +
                      "\"recipient\":\"" + esc(chatPartner) + "\"," +
                      "\"content\":\"" + esc(content) + "\"}";
        messageInput.setText("");

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER + "/send"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Failed to send message: " + e.getMessage()));
            }
        });
    }

    private void openConversation(String partner) {
        chatPartner = partner;
        chatWithLabel.setText("Chat with " + partner);
        messageInput.setEnabled(true);
        sendButton.setEnabled(true);
        messageInput.requestFocus();
        messageArea.removeAll();
        messageArea.revalidate();
        messageArea.repaint();

        // Load history
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER + "/history?user1=" + encode(myUsername)
                                             + "&user2=" + encode(partner)))
                    .GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                List<Map<String,String>> messages = parseJsonObjects(resp.body());
                SwingUtilities.invokeLater(() -> {
                    for (Map<String,String> msg : messages) {
                        addMessageBubble(
                            msg.get("sender"),
                            msg.get("content"),
                            Long.parseLong(msg.getOrDefault("ts","0"))
                        );
                    }
                    scrollToBottom();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleIncomingMessage(String json) {
        Map<String,String> msg = parseJsonObject(json);
        String sender    = msg.get("sender");
        String recipient = msg.get("recipient");
        String content   = msg.get("content");
        long   ts        = Long.parseLong(msg.getOrDefault("ts","0"));

        // Only show if it's part of the active conversation
        String partner = sender.equals(myUsername) ? recipient : sender;
        if (chatPartner != null && chatPartner.equals(partner)) {
            SwingUtilities.invokeLater(() -> {
                addMessageBubble(sender, content, ts);
                scrollToBottom();
            });
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  UI Updates
    // ──────────────────────────────────────────────────────────────

    private void refreshUserList(List<String> users) {
        String selected = userList.getSelectedValue();
        userListModel.clear();
        for (String u : users) {
            if (!u.equals(myUsername)) {
                userListModel.addElement(u);
            }
        }
        if (selected != null && userListModel.contains(selected)) {
            userList.setSelectedValue(selected, false);
        }
        statusLabel.setText("Online — " + users.size() + " user(s)");
    }

    private void addMessageBubble(String sender, String content, long ts) {
        boolean mine = sender.equals(myUsername);

        JPanel wrapper = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 2));
        wrapper.setBackground(BG_PANEL);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(mine ? BUBBLE_MINE : BUBBLE_OTHER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setLayout(new BorderLayout(0, 4));
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

        if (!mine) {
            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font("Dialog", Font.BOLD, 10));
            nameLabel.setForeground(ACCENT_LIGHT);
            bubble.add(nameLabel, BorderLayout.NORTH);
        }

        JTextArea textArea = new JTextArea(content);
        textArea.setFont(new Font("Dialog", Font.PLAIN, 13));
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setBackground(new Color(0,0,0,0));
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        // Calculate preferred width
        FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
        int textWidth = Math.min(fm.stringWidth(content) + 10, 380);
        textArea.setPreferredSize(null);
        textArea.setColumns(0);
        textArea.setSize(new Dimension(textWidth, Short.MAX_VALUE));
        int prefHeight = textArea.getPreferredSize().height;
        textArea.setPreferredSize(new Dimension(textWidth, prefHeight));

        String timeStr = formatTime(ts);
        JLabel timeLabel = new JLabel(timeStr);
        timeLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        timeLabel.setForeground(new Color(mine ? 200 : 100, mine ? 200 : 100, mine ? 255 : 140, 180));
        timeLabel.setHorizontalAlignment(mine ? SwingConstants.RIGHT : SwingConstants.LEFT);

        bubble.add(textArea, BorderLayout.CENTER);
        bubble.add(timeLabel, BorderLayout.SOUTH);

        wrapper.add(bubble);
        messageArea.add(wrapper);
        messageArea.revalidate();
        messageArea.repaint();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vb = messageScroll.getVerticalScrollBar();
            vb.setValue(vb.getMaximum());
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Cell renderer for user list
    // ──────────────────────────────────────────────────────────────

    private class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JPanel cell = new JPanel(new BorderLayout(10, 0));
            cell.setBorder(new EmptyBorder(0, 12, 0, 12));
            cell.setBackground(isSelected ? SELECTED_BG : BG_SIDEBAR);

            // Online dot
            JLabel dot = new JLabel("●");
            dot.setFont(new Font("Dialog", Font.PLAIN, 10));
            dot.setForeground(ONLINE_DOT);
            dot.setPreferredSize(new Dimension(14, 14));

            JLabel name = new JLabel(value.toString());
            name.setFont(new Font("Dialog", isSelected ? Font.BOLD : Font.PLAIN, 13));
            name.setForeground(isSelected ? TEXT_PRIMARY : new Color(200, 200, 230));

            cell.add(dot, BorderLayout.WEST);
            cell.add(name, BorderLayout.CENTER);

            return cell;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilities
    // ──────────────────────────────────────────────────────────────

    private static String formatTime(long epochMs) {
        if (epochMs == 0) return "";
        LocalDateTime ldt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMs),
            ZoneId.systemDefault()
        );
        return ldt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","").replace("\t","\\t");
    }

    /** Parse ["a","b","c"] */
    private static List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null) return result;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length()-1);
        for (String part : json.split(",")) {
            part = part.trim().replace("\"","");
            if (!part.isEmpty()) result.add(part);
        }
        return result;
    }

    /** Parse a single JSON object into a map (simple string fields only) */
    private static Map<String,String> parseJsonObject(String json) {
        Map<String,String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        // Find all "key":"value" pairs
        int i = 0;
        while (i < json.length()) {
            int ks = json.indexOf('"', i);
            if (ks < 0) break;
            int ke = json.indexOf('"', ks+1);
            if (ke < 0) break;
            String key = json.substring(ks+1, ke);
            int colon = json.indexOf(':', ke+1);
            if (colon < 0) break;
            int valStart = colon+1;
            while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;
            if (valStart >= json.length()) break;
            char first = json.charAt(valStart);
            String value;
            if (first == '"') {
                // string value
                int vs = valStart+1, ve = vs;
                while (ve < json.length()) {
                    if (json.charAt(ve) == '\\') { ve += 2; continue; }
                    if (json.charAt(ve) == '"') break;
                    ve++;
                }
                value = json.substring(vs, ve)
                            .replace("\\n","\n").replace("\\r","")
                            .replace("\\\"","\"").replace("\\\\","\\");
                i = ve+1;
            } else {
                // numeric / boolean
                int ve = valStart;
                while (ve < json.length() && ",}]".indexOf(json.charAt(ve)) < 0) ve++;
                value = json.substring(valStart, ve).trim();
                i = ve;
            }
            map.put(key, value);
        }
        return map;
    }

    /** Parse a JSON array of objects */
    private static List<Map<String,String>> parseJsonObjects(String json) {
        List<Map<String,String>> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        // Split on top-level { }
        int depth = 0, start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') {
                if (--depth == 0 && start >= 0) {
                    result.add(parseJsonObject(json.substring(start, i+1)));
                    start = -1;
                }
            }
        }
        return result;
    }
}
