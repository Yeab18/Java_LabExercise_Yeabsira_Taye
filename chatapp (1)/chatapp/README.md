# JavaChat — Real-Time Multi-User Chat App

A real-time chat application built in **pure Java** (no Spring, no Maven).  
Messages are persisted in an **SQLite database** — conversations load on every open.

---

## Architecture

```
┌─────────────────────────────────────────┐
│           ChatServer (port 8765)        │
│  • HTTP server (built-in com.sun.net)   │
│  • Server-Sent Events for real-time     │
│  • SQLite via JDBC for persistence      │
└────────────────┬────────────────────────┘
                 │  HTTP / SSE
    ┌────────────┴────────────┐
    │                         │
┌───▼────────┐        ┌───────▼────────┐
│ ChatClient │        │  ChatClient    │
│ (Alice)    │        │   (Bob)        │
│ Swing UI   │        │  Swing UI      │
└────────────┘        └────────────────┘
```

---

## Requirements

- **JDK 11 or higher** (JDK 17+ recommended)
  - Ubuntu/Debian: `sudo apt install default-jdk`
  - macOS: `brew install openjdk`
  - Windows: download from https://adoptium.net
- **Internet access** (only needed once to download the SQLite JDBC driver ~10 MB)

---

## Quick Start

### Linux / macOS

```bash
chmod +x run.sh
./run.sh
```

Choose **3** to start both server and one client at once.  
Open a **second terminal** and run `./run.sh` again, choose **2** for a second client.

### Windows

Double-click `run.bat` or run it in a terminal.

---

## Manual Build & Run

```bash
# 1. Download SQLite JDBC once
mkdir lib
curl -L -o lib/sqlite-jdbc.jar \
  https://github.com/xerial/sqlite-jdbc/releases/download/3.45.2.0/sqlite-jdbc-3.45.2.0.jar

# 2. Compile
mkdir out
javac -cp lib/sqlite-jdbc.jar -d out \
  src/server/ChatServer.java \
  src/client/ChatClient.java

# 3. Run server (one terminal)
java -cp out:lib/sqlite-jdbc.jar server.ChatServer

# 4. Run clients (separate terminals — as many as you like)
java -cp out:lib/sqlite-jdbc.jar client.ChatClient
java -cp out:lib/sqlite-jdbc.jar client.ChatClient
```

On **Windows** replace `:` with `;` in the classpath.

---

## How It Works

### Login
- Enter any username when prompted — no password needed
- Multiple people can join with different usernames

### Chatting
1. The left sidebar shows all currently **online users**
2. Click a user's name to open a conversation
3. Type a message and press **Enter** or click **Send**
4. Messages appear in real-time using **Server-Sent Events (SSE)**

### Persistence
- Every message is stored in `chat.db` (SQLite) on the server
- When you open a conversation, the full history loads automatically
- The database survives server restarts

---

## Project Structure

```
chatapp/
├── src/
│   ├── server/
│   │   └── ChatServer.java     ← HTTP + SSE server + SQLite
│   └── client/
│       └── ChatClient.java     ← Swing UI + SSE listener
├── lib/
│   └── sqlite-jdbc.jar         ← downloaded on first build
├── out/                        ← compiled .class files
├── run.sh                      ← Linux/macOS build+run script
├── run.bat                     ← Windows build+run script
└── README.md
```

---

## Server API (for reference)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/login?username=alice` | Register username |
| POST | `/send` | Send a message (JSON body) |
| GET | `/events?username=alice` | SSE stream of incoming messages |
| GET | `/history?user1=alice&user2=bob` | Load conversation history |
| GET | `/users` | List online users |

---

## Customisation Tips

- **Change port**: Edit `PORT = 8765` in `ChatServer.java`
- **Change DB location**: Edit `DB_FILE = "chat.db"` in `ChatServer.java`
- **Add group chat**: Add a broadcast endpoint that sends to all connected users
- **Multiple server machines**: Point `SERVER` in `ChatClient.java` to the server's IP

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `javac: command not found` | Install JDK (not just JRE) |
| `Connection refused` | Start the server first |
| No users showing in sidebar | Both clients must be connected to the same server |
| Old messages not loading | Check `chat.db` exists in the directory you ran the server from |
