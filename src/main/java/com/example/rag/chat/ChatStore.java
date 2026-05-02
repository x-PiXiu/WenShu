package com.example.rag.chat;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 聊天持久化：基于 SQLite 存储对话和消息
 */
public class ChatStore {

    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Simple connection pool: pre-created connections with WAL + busy_timeout.
    // Proxy wrapper ensures try-with-resources in callers returns connections
    // to the pool instead of closing them.
    private static final int POOL_SIZE = 3;
    private static final BlockingQueue<Connection> pool = new LinkedBlockingQueue<>(POOL_SIZE);

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                pool.offer(createRawConnection());
            } catch (SQLException e) {
                System.err.println("[WARN] Failed to init DB connection: " + e.getMessage());
            }
        }
    }

    private static Connection createRawConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    /**
     * Borrow a pooled connection, wrapped so that close() returns it to the pool.
     * Existing try-with-resources patterns work without any code changes.
     */
    private static Connection getConnection() throws SQLException {
        Connection raw;
        try {
            raw = pool.poll(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createRawConnection();
        }
        if (raw == null || raw.isClosed()) {
            raw = createRawConnection();
        }
        final Connection delegate = raw;
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                        try {
                            if (!delegate.isClosed()) pool.offer(delegate);
                        } catch (SQLException ignored) {}
                        return null;
                    }
                    return method.invoke(delegate, args);
                }
        );
    }

    public ChatStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversation (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL DEFAULT '新对话',
                    agent_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);

            // Migration: add agent_id column if upgrading from older schema
            try {
                stmt.execute("ALTER TABLE conversation ADD COLUMN agent_id TEXT");
            } catch (SQLException ignored) {}

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS message (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    sources_json TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_msg_conv ON message(conversation_id)");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to init chat database: " + e.getMessage(), e);
        }
    }

    // ===== Conversation CRUD =====

    public Conversation createConversation(String agentId) {
        String id = "conv-" + System.currentTimeMillis();
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO conversation (id, title, agent_id, created_at, updated_at) VALUES (?, '新对话', ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, agentId);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
            return new Conversation(id, "新对话", agentId, now, now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create conversation: " + e.getMessage(), e);
        }
    }

    public List<Conversation> listConversations() {
        String sql = "SELECT id, title, agent_id, created_at, updated_at FROM conversation ORDER BY updated_at DESC";
        List<Conversation> result = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new Conversation(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("agent_id"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list conversations: " + e.getMessage(), e);
        }
        return result;
    }

    public Conversation getConversation(String id) {
        String sql = "SELECT id, title, agent_id, created_at, updated_at FROM conversation WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Conversation(
                            rs.getString("id"),
                            rs.getString("title"),
                            rs.getString("agent_id"),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get conversation: " + e.getMessage(), e);
        }
        return null;
    }

    public void updateTitle(String conversationId, String title) {
        String sql = "UPDATE conversation SET title = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, conversationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update title: " + e.getMessage(), e);
        }
    }

    public void touchConversation(String id) {
        String sql = "UPDATE conversation SET updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to touch conversation: " + e.getMessage(), e);
        }
    }

    public void deleteConversation(String id) {
        String sql = "DELETE FROM conversation WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete conversation: " + e.getMessage(), e);
        }
    }

    // ===== Message CRUD =====

    public Message saveMessage(String conversationId, String role, String content, List<MessageSource> sources) {
        long now = System.currentTimeMillis();
        String sourcesJson = null;
        if (sources != null && !sources.isEmpty()) {
            try {
                sourcesJson = MAPPER.writeValueAsString(sources);
            } catch (Exception e) {
                sourcesJson = null;
            }
        }

        String sql = "INSERT INTO message (conversation_id, role, content, sources_json, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, conversationId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setString(4, sourcesJson);
            ps.setLong(5, now);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Message(rs.getInt(1), conversationId, role, content, sources, now);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save message: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Message> listMessages(String conversationId) {
        String sql = "SELECT id, conversation_id, role, content, sources_json, created_at FROM message WHERE conversation_id = ? ORDER BY created_at ASC";
        List<Message> result = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<MessageSource> sources = null;
                    String sourcesJson = rs.getString("sources_json");
                    if (sourcesJson != null && !sourcesJson.isBlank()) {
                        try {
                            sources = MAPPER.readValue(sourcesJson,
                                    MAPPER.getTypeFactory().constructCollectionType(List.class, MessageSource.class));
                        } catch (Exception ignored) {}
                    }
                    result.add(new Message(
                            rs.getInt("id"),
                            rs.getString("conversation_id"),
                            rs.getString("role"),
                            rs.getString("content"),
                            sources,
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list messages: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Save user + assistant messages, touch conversation, and auto-update title
     * in a single connection and transaction. Avoids 5 separate pool borrow/return cycles.
     */
    public void saveMessagesInTransaction(String convId, String question,
                                          String answer, List<MessageSource> sources) {
        long now = System.currentTimeMillis();

        // Serialize sources once
        String sourcesJson = null;
        if (sources != null && !sources.isEmpty()) {
            try {
                sourcesJson = MAPPER.writeValueAsString(sources);
            } catch (Exception ignored) {}
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Save user message
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO message (conversation_id, role, content, sources_json, created_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, convId);
                    ps.setString(2, "user");
                    ps.setString(3, question);
                    ps.setNull(4, Types.VARCHAR);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }

                // 2. Save assistant message
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO message (conversation_id, role, content, sources_json, created_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, convId);
                    ps.setString(2, "assistant");
                    ps.setString(3, answer);
                    ps.setString(4, sourcesJson);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }

                // 3. Touch conversation
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE conversation SET updated_at = ? WHERE id = ?")) {
                    ps.setLong(1, now);
                    ps.setString(2, convId);
                    ps.executeUpdate();
                }

                // 4. Auto-update title if still default
                String currentTitle;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT title FROM conversation WHERE id = ?")) {
                    ps.setString(1, convId);
                    try (ResultSet rs = ps.executeQuery()) {
                        currentTitle = rs.next() ? rs.getString("title") : null;
                    }
                }
                if ("新对话".equals(currentTitle)) {
                    String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE conversation SET title = ?, updated_at = ? WHERE id = ?")) {
                        ps.setString(1, title);
                        ps.setLong(2, now);
                        ps.setString(3, convId);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save messages in transaction: " + e.getMessage(), e);
        }
    }

    // ===== Data Records =====

    public record Conversation(String id, String title, String agentId, long createdAt, long updatedAt) {}
    public record Message(int id, String conversationId, String role, String content,
                          List<MessageSource> sources, long createdAt) {}
    public record MessageSource(int index, String text, String source, double rrfScore, double vectorScore,
                                 double confidence, String confidenceLabel, String explanation) {}
}
