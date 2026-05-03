package com.example.rag.chat;

import com.example.rag.config.DatabasePool;
import com.example.rag.prompt.PromptRegistry;
import com.example.rag.prompt.RagPromptTemplate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 智能体管理：基于 SQLite 存储 Agent 定义（名称、系统提示词等）
 */
public class AgentStore {

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public AgentStore() {
        initSchema();
        seedDefaultAgent();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agent (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    system_prompt TEXT NOT NULL,
                    avatar TEXT NOT NULL DEFAULT '',
                    is_default INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init agent table: " + e.getMessage(), e);
        }
    }

    private void seedDefaultAgent() {
        // Check if default agent already exists
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM agent WHERE is_default = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return; // already seeded
            }
        } catch (SQLException ignored) {}

        String defaultPrompt = RagPromptTemplate.getSystemPrompt();

        long now = System.currentTimeMillis();
        String sql = "INSERT INTO agent (id, name, description, system_prompt, avatar, is_default, created_at, updated_at) " +
                "VALUES ('default', '文枢知识助手', '默认的知识库问答助手', ?, '📚', 1, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, defaultPrompt.strip());
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
            System.out.println("[INIT] Default agent seeded");
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to seed default agent: " + e.getMessage());
        }
    }

    // ===== CRUD =====

    public Agent create(String name, String description, String systemPrompt, String avatar) {
        String id = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO agent (id, name, description, system_prompt, avatar, is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, description != null ? description : "");
            ps.setString(4, systemPrompt);
            ps.setString(5, avatar != null ? avatar : "");
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create agent: " + e.getMessage(), e);
        }
        return new Agent(id, name, description != null ? description : "",
                systemPrompt, avatar != null ? avatar : "", false, now, now);
    }

    public Agent update(String id, String name, String description, String systemPrompt, String avatar) {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE agent SET name = ?, description = ?, system_prompt = ?, avatar = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, systemPrompt);
            ps.setString(4, avatar);
            ps.setLong(5, now);
            ps.setString(6, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Agent not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update agent: " + e.getMessage(), e);
        }
        return new Agent(id, name, description, systemPrompt, avatar, false, 0, now);
    }

    public void delete(String id) {
        Agent agent = getById(id);
        if (agent != null && agent.isDefault()) {
            throw new RuntimeException("Cannot delete the default agent");
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM agent WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete agent: " + e.getMessage(), e);
        }
    }

    public Agent getById(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, description, system_prompt, avatar, is_default, created_at, updated_at FROM agent WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get agent: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Agent> list() {
        List<Agent> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT id, name, description, system_prompt, avatar, is_default, created_at, updated_at FROM agent ORDER BY is_default DESC, created_at ASC")) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list agents: " + e.getMessage(), e);
        }
        return result;
    }

    private Agent mapRow(ResultSet rs) throws SQLException {
        return new Agent(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("system_prompt"),
                rs.getString("avatar"),
                rs.getInt("is_default") == 1,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    public record Agent(String id, String name, String description,
                        String systemPrompt, String avatar, boolean isDefault,
                        long createdAt, long updatedAt) {}
}
