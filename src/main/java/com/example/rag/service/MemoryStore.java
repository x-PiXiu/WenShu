package com.example.rag.service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 记忆持久化：基于 SQLite 存储，支持重要性评分和衰减机制
 */
public class MemoryStore {

    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static final int POOL_SIZE = 2;
    private static final BlockingQueue<Connection> pool = new LinkedBlockingQueue<>(POOL_SIZE);

    private static final double DECAY_LAMBDA = 0.05;

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                pool.offer(createRawConnection());
            } catch (SQLException e) {
                System.err.println("[WARN] Failed to init MemoryStore connection: " + e.getMessage());
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

    public MemoryStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memory (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    importance REAL NOT NULL DEFAULT 0.5,
                    access_count INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    last_accessed_at INTEGER NOT NULL,
                    decayed_importance REAL NOT NULL DEFAULT 0.5
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memory_conv ON memory(conversation_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memory_importance ON memory(decayed_importance DESC)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init memory table: " + e.getMessage(), e);
        }
    }

    public MemoryEntry storeMemory(String conversationId, String summary, double importance) {
        String id = "mem-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        double clamped = Math.max(0.2, Math.min(1.0, importance));
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO memory (id, conversation_id, summary, importance, access_count, created_at, last_accessed_at, decayed_importance) VALUES (?, ?, ?, ?, 0, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, conversationId);
            ps.setString(3, summary);
            ps.setDouble(4, clamped);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.setDouble(7, clamped);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to store memory: " + e.getMessage());
        }
        return new MemoryEntry(id, conversationId, summary, clamped, 0, now, now, clamped);
    }

    public MemoryEntry getMemory(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM memory WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public List<MemoryEntry> listByConversation(String conversationId) {
        List<MemoryEntry> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM memory WHERE conversation_id = ? ORDER BY created_at ASC")) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException ignored) {}
        return result;
    }

    public List<MemoryEntry> listAll() {
        List<MemoryEntry> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM memory ORDER BY decayed_importance DESC")) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException ignored) {}
        return result;
    }

    public void touchMemory(String id) {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE memory SET access_count = access_count + 1, last_accessed_at = ? WHERE id = ?")) {
            ps.setLong(1, now);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void deleteMemory(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM memory WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public int getMemoryCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM memory")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public int recalculateDecay() {
        List<MemoryEntry> all = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, importance, access_count, created_at FROM memory")) {
            while (rs.next()) {
                all.add(new MemoryEntry(
                        rs.getString("id"), null, null,
                        rs.getDouble("importance"), rs.getInt("access_count"),
                        rs.getLong("created_at"), 0, 0));
            }
        } catch (SQLException ignored) {}

        long now = System.currentTimeMillis();
        int updated = 0;
        for (MemoryEntry m : all) {
            double days = (now - m.createdAt) / (1000.0 * 86400.0);
            double decayed = m.importance * Math.exp(-DECAY_LAMBDA * days) * (1 + Math.log(1 + m.accessCount));
            decayed = Math.max(0.0, Math.min(1.0, decayed));
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE memory SET decayed_importance = ? WHERE id = ?")) {
                ps.setDouble(1, decayed);
                ps.setString(2, m.id);
                ps.executeUpdate();
                updated++;
            } catch (SQLException ignored) {}
        }
        return updated;
    }

    private MemoryEntry mapRow(ResultSet rs) throws SQLException {
        return new MemoryEntry(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("summary"),
                rs.getDouble("importance"),
                rs.getInt("access_count"),
                rs.getLong("created_at"),
                rs.getLong("last_accessed_at"),
                rs.getDouble("decayed_importance")
        );
    }

    public record MemoryEntry(String id, String conversationId, String summary,
                               double importance, int accessCount,
                               long createdAt, long lastAccessedAt, double decayedImportance) {}
}
