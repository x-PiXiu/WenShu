package com.example.rag.chat;

import com.example.rag.config.AppConfiguration;
import com.example.rag.config.DatabasePool;
import com.example.rag.prompt.PromptRegistry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 智能体管理：基于 SQLite 存储 Agent 定义
 * system_prompt 已迁移至 PromptRegistry 统一管理，此处只存 prompt_key 引用
 */
public class AgentStore {

    private final AppConfiguration config;

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public AgentStore(AppConfiguration config) {
        this.config = config;
        initSchema();
        seedDefaultAgent();
    }

    private void initSchema() {
        try (Connection conn = getConnection()) {
            migrateFromSystemPrompt(conn);
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS agent (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    prompt_key TEXT NOT NULL,
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

    /**
     * 检测旧 schema（system_prompt 列），迁移数据到新 schema（prompt_key）
     * 迁移后将每个 agent 的 system_prompt 写入 PromptRegistry，category = "agent"
     */
    private void migrateFromSystemPrompt(Connection conn) throws SQLException {
        if (!hasColumn(conn, "agent", "system_prompt")) return;
        if (!tableExists(conn, "agent")) return;

        System.out.println("[MIGRATE] Detected old agent schema with system_prompt, migrating...");

        List<Object[]> oldRows = new ArrayList<>();
        try (var ps = conn.prepareStatement(
                "SELECT id, name, description, system_prompt, avatar, is_default, created_at, updated_at FROM agent");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                oldRows.add(new Object[]{
                        rs.getString("id"), rs.getString("name"), rs.getString("description"),
                        rs.getString("system_prompt"), rs.getString("avatar"),
                        rs.getInt("is_default") == 1,
                        rs.getLong("created_at"), rs.getLong("updated_at")
                });
            }
        }

        String[] promptKeys = new String[oldRows.size()];
        for (int i = 0; i < oldRows.size(); i++) {
            Object[] row = oldRows.get(i);
            String agentId = (String) row[0];
            boolean isDefault = (boolean) row[5];
            String systemPrompt = (String) row[3];

            if (isDefault) {
                promptKeys[i] = "rag_qa";
                // 同步默认 agent 的 prompt 到 PromptRegistry（以防用户曾修改过）
                PromptRegistry.putTemplate("rag_qa", systemPrompt, "RAG 知识库问答", "core");
            } else {
                promptKeys[i] = "agent_" + agentId;
                String agentName = (String) row[1];
                PromptRegistry.putTemplate("agent_" + agentId, systemPrompt,
                        "Agent: " + agentName, "agent");
            }
        }

        conn.createStatement().execute("DROP TABLE agent");
        conn.createStatement().execute("""
            CREATE TABLE agent (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                prompt_key TEXT NOT NULL,
                avatar TEXT NOT NULL DEFAULT '',
                is_default INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """);

        try (var ps = conn.prepareStatement(
                "INSERT INTO agent (id, name, description, prompt_key, avatar, is_default, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)")) {
            for (int i = 0; i < oldRows.size(); i++) {
                Object[] row = oldRows.get(i);
                ps.setString(1, (String) row[0]);
                ps.setString(2, (String) row[1]);
                ps.setString(3, (String) row[2]);
                ps.setString(4, promptKeys[i]);
                ps.setString(5, (String) row[4]);
                ps.setInt(6, (boolean) row[5] ? 1 : 0);
                ps.setLong(7, (long) row[6]);
                ps.setLong(8, (long) row[7]);
                ps.executeUpdate();
            }
        }

        PromptRegistry.persist(config);
        System.out.println("[MIGRATE] Migrated " + oldRows.size() + " agents from system_prompt to prompt_key");
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (var rs = conn.createStatement().executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        try (var rs = conn.createStatement()
                .executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }

    private void seedDefaultAgent() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM agent WHERE is_default = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        } catch (SQLException ignored) {}

        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO agent (id, name, description, prompt_key, avatar, is_default, created_at, updated_at) " +
                             "VALUES ('default', '文枢知识助手', '默认的知识库问答助手', 'rag_qa', '📚', 1, ?, ?)")) {
            ps.setLong(1, now);
            ps.setLong(2, now);
            ps.executeUpdate();
            System.out.println("[INIT] Default agent seeded (prompt_key=rag_qa)");
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to seed default agent: " + e.getMessage());
        }
    }

    // ===== CRUD =====

    public Agent create(String name, String description, String systemPrompt, String avatar) {
        String id = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        String promptKey = "agent_" + id;
        long now = System.currentTimeMillis();

        // 先写入 PromptRegistry
        PromptRegistry.putTemplate(promptKey, systemPrompt, "Agent: " + name, "agent");
        PromptRegistry.persist(config);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO agent (id, name, description, prompt_key, avatar, is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, description != null ? description : "");
            ps.setString(4, promptKey);
            ps.setString(5, avatar != null ? avatar : "");
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            PromptRegistry.removeTemplate(promptKey);
            PromptRegistry.persist(config);
            throw new RuntimeException("Failed to create agent: " + e.getMessage(), e);
        }
        return new Agent(id, name, description != null ? description : "",
                promptKey, avatar != null ? avatar : "", false, now, now);
    }

    public Agent update(String id, String name, String description, String systemPrompt, String avatar) {
        Agent existing = getById(id);
        if (existing == null) throw new RuntimeException("Agent not found: " + id);

        long now = System.currentTimeMillis();

        // 更新 PromptRegistry 中的提示词
        PromptRegistry.putTemplate(existing.promptKey(), systemPrompt, "Agent: " + name, "agent");
        PromptRegistry.persist(config);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE agent SET name = ?, description = ?, avatar = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, avatar);
            ps.setLong(4, now);
            ps.setString(5, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Agent not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update agent: " + e.getMessage(), e);
        }
        return new Agent(id, name, description, existing.promptKey(), avatar, existing.isDefault(),
                existing.createdAt(), now);
    }

    public void delete(String id) {
        Agent agent = getById(id);
        if (agent == null) return;
        if (agent.isDefault()) {
            throw new RuntimeException("Cannot delete the default agent");
        }

        // 先从 PromptRegistry 移除提示词
        PromptRegistry.removeTemplate(agent.promptKey());
        PromptRegistry.persist(config);

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
                     "SELECT id, name, description, prompt_key, avatar, is_default, created_at, updated_at FROM agent WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public List<Agent> list() {
        List<Agent> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, name, description, prompt_key, avatar, is_default, created_at, updated_at FROM agent ORDER BY is_default DESC, created_at ASC")) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException ignored) {}
        return result;
    }

    private Agent mapRow(ResultSet rs) throws SQLException {
        return new Agent(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("prompt_key"),
                rs.getString("avatar"),
                rs.getInt("is_default") == 1,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    public record Agent(String id, String name, String description,
                        String promptKey, String avatar, boolean isDefault,
                        long createdAt, long updatedAt) {}
}
