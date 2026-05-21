package server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


public class ChatServer {

    private static final int    PORT    = 8765;
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/chatdb?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    // username -> list of active SSE output streams
    private static final Map<String, List<OutputStream>> subscribers = new ConcurrentHashMap<>();
    private static final AtomicLong messageIdGen = new AtomicLong(System.currentTimeMillis());

    public static void main(String[] args) throws Exception {
        initDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/login",   ChatServer::handleLogin);
        server.createContext("/send",    ChatServer::handleSend);
        server.createContext("/events",  ChatServer::handleEvents);
        server.createContext("/history", ChatServer::handleHistory);
        server.createContext("/users",   ChatServer::handleUsers);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("===========================================");
        System.out.println("  Chat Server started on port " + PORT);
        System.out.println("  Database: " + DB_URL);
        System.out.println("===========================================");
    }

    // ──────────────────────────────────────────────────────────────
    //  Database
    // ──────────────────────────────────────────────────────────────

  private static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(
        "jdbc:mysql://localhost:3306/chatdb",
        "root",
        ""        // empty string = no password
    );
}
    private static void initDatabase() throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    sender    VARCHAR(100) NOT NULL,
                    recipient VARCHAR(100) NOT NULL,
                    content   TEXT         NOT NULL,
                    ts        BIGINT       NOT NULL
                )
            """);
            s.execute("""
                CREATE INDEX IF NOT EXISTS idx_conv
                ON messages (sender, recipient, ts)
            """);
            System.out.println("Database ready.");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Handlers
    // ──────────────────────────────────────────────────────────────

    /** POST /login?username=alice */
    private static void handleLogin(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { respond(ex, 405, "Method Not Allowed"); return; }
        addCors(ex);
        Map<String,String> params = queryParams(ex.getRequestURI().getQuery());
        String username = params.getOrDefault("username", "").trim();
        if (username.isEmpty()) { respond(ex, 400, "{\"error\":\"username required\"}"); return; }
        subscribers.putIfAbsent(username, new CopyOnWriteArrayList<>());
        respond(ex, 200, "{\"ok\":true,\"username\":\"" + esc(username) + "\"}");
        System.out.println("User logged in: " + username);
    }

    /** POST /send  body: {"sender":"alice","recipient":"bob","content":"hello"} */
    private static void handleSend(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { respond(ex, 405, "Method Not Allowed"); return; }
        addCors(ex);
        String body      = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String sender    = jsonField(body, "sender");
        String recipient = jsonField(body, "recipient");
        String content   = jsonField(body, "content");

        if (sender == null || recipient == null || content == null || content.isBlank()) {
            respond(ex, 400, "{\"error\":\"sender, recipient, content required\"}");
            return;
        }

        long ts = System.currentTimeMillis();
        long id;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO messages(sender, recipient, content, ts) VALUES(?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sender);
            ps.setString(2, recipient);
            ps.setString(3, content);
            ps.setLong(4, ts);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                id = rs.next() ? rs.getLong(1) : messageIdGen.incrementAndGet();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            respond(ex, 500, "{\"error\":\"db error\"}");
            return;
        }

        String event = buildMessageEvent(id, sender, recipient, content, ts);
        pushEvent(recipient, event);  // deliver to recipient
        pushEvent(sender, event);     // echo back to sender

        respond(ex, 200, "{\"ok\":true,\"id\":" + id + "}");
    }

    /** GET /events?username=alice — SSE long-poll */
    private static void handleEvents(HttpExchange ex) throws IOException {
        addCors(ex);
        Map<String,String> params = queryParams(ex.getRequestURI().getQuery());
        String username = params.getOrDefault("username", "").trim();
        if (username.isEmpty()) { respond(ex, 400, "username required"); return; }

        subscribers.putIfAbsent(username, new CopyOnWriteArrayList<>());

        ex.getResponseHeaders().set("Content-Type",  "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection",    "keep-alive");
        ex.sendResponseHeaders(200, 0);

        OutputStream out = ex.getResponseBody();
        subscribers.get(username).add(out);
        System.out.println("SSE connected: " + username +
                           " (streams: " + subscribers.get(username).size() + ")");

        try {
            out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {}

        // Keep connection alive with periodic heartbeats
        try {
            while (true) {
                Thread.sleep(25_000);
                out.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            List<OutputStream> list = subscribers.get(username);
            if (list != null) list.remove(out);
            System.out.println("SSE disconnected: " + username);
            try { out.close(); } catch (IOException ignored) {}
        }
    }

    /** GET /history?user1=alice&user2=bob */
    private static void handleHistory(HttpExchange ex) throws IOException {
        addCors(ex);
        Map<String,String> params = queryParams(ex.getRequestURI().getQuery());
        String u1 = params.getOrDefault("user1", "").trim();
        String u2 = params.getOrDefault("user2", "").trim();
        if (u1.isEmpty() || u2.isEmpty()) {
            respond(ex, 400, "{\"error\":\"user1 and user2 required\"}");
            return;
        }

        List<String> rows = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, sender, recipient, content, ts FROM messages " +
                 "WHERE (sender = ? AND recipient = ?) OR (sender = ? AND recipient = ?) " +
                 "ORDER BY ts ASC LIMIT 500")) {
            ps.setString(1, u1); ps.setString(2, u2);
            ps.setString(3, u2); ps.setString(4, u1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add("{\"id\":"        + rs.getLong("id")              +
                             ",\"sender\":\""    + esc(rs.getString("sender"))    + "\"" +
                             ",\"recipient\":\"" + esc(rs.getString("recipient")) + "\"" +
                             ",\"content\":\""   + esc(rs.getString("content"))   + "\"" +
                             ",\"ts\":"          + rs.getLong("ts")               + "}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            respond(ex, 500, "{\"error\":\"db error\"}");
            return;
        }
        respond(ex, 200, "[" + String.join(",", rows) + "]");
    }

    /** GET /users — list currently online users */
    private static void handleUsers(HttpExchange ex) throws IOException {
        addCors(ex);
        List<String> online = new ArrayList<>();
        for (Map.Entry<String, List<OutputStream>> e : subscribers.entrySet()) {
            if (!e.getValue().isEmpty()) {
                online.add("\"" + esc(e.getKey()) + "\"");
            }
        }
        respond(ex, 200, "[" + String.join(",", online) + "]");
    }

    // ──────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────

    private static void pushEvent(String username, String event) {
        List<OutputStream> streams = subscribers.get(username);
        if (streams == null) return;
        List<OutputStream> dead = new ArrayList<>();
        for (OutputStream out : streams) {
            try {
                out.write(event.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                dead.add(out);
            }
        }
        streams.removeAll(dead);
    }

    private static String buildMessageEvent(long id, String sender, String recipient,
                                             String content, long ts) {
        String json = "{\"id\":"          + id                  +
                      ",\"sender\":\""    + esc(sender)         + "\"" +
                      ",\"recipient\":\"" + esc(recipient)      + "\"" +
                      ",\"content\":\""   + esc(content)        + "\"" +
                      ",\"ts\":"          + ts                  + "}";
        return "data: " + json + "\n\n";
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        addCors(ex);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static Map<String,String> queryParams(String query) {
        Map<String,String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], "UTF-8"),
                            URLDecoder.decode(kv[1], "UTF-8"));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /** Minimal JSON string-field extractor */
    private static String jsonField(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return null;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = start + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '\\') { end += 2; continue; }
            if (json.charAt(end) == '"')  break;
            end++;
        }
        return json.substring(start + 1, end)
                   .replace("\\n",  "\n")
                   .replace("\\r",  "")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\");
    }

    /** Escape a string for safe JSON embedding */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
