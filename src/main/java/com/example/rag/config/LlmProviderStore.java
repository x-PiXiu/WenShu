package com.example.rag.config;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LLM Provider 多配置管理：基于 SQLite 存储多个 LLM 服务商配置
 * 支持添加、编辑、删除、切换激活等操作
 */
public class LlmProviderStore {

    private final AppConfiguration config;

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public LlmProviderStore(AppConfiguration config) {
        this.config = config;
        initSchema();
        seedFromConfig();
    }

    private void initSchema() {
        try (Connection conn = getConnection()) {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS llm_provider (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    provider TEXT NOT NULL DEFAULT 'ollama',
                    base_url TEXT NOT NULL DEFAULT '',
                    api_key TEXT NOT NULL DEFAULT '',
                    model_name TEXT NOT NULL DEFAULT '',
                    temperature REAL,
                    max_tokens INTEGER,
                    streaming INTEGER NOT NULL DEFAULT 1,
                    is_default INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init llm_provider table: " + e.getMessage(), e);
        }
    }

    /** 首次运行时从 config.json 的 llm 配置自动 seed 一个默认 Provider */
    private void seedFromConfig() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM llm_provider")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
        } catch (SQLException e) {
            return;
        }

        var llm = config.getLlm();
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO llm_provider (id, name, provider, base_url, api_key, model_name, " +
                     "temperature, max_tokens, streaming, is_default, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, "provider-default");
            ps.setString(2, deriveName(llm.provider, llm.modelName));
            ps.setString(3, llm.provider);
            ps.setString(4, llm.baseUrl);
            ps.setString(5, llm.apiKey);
            ps.setString(6, llm.modelName);
            if (llm.temperature != null) ps.setDouble(7, llm.temperature);
            else ps.setNull(7, Types.REAL);
            if (llm.maxTokens != null) ps.setInt(8, llm.maxTokens);
            else ps.setNull(8, Types.INTEGER);
            ps.setInt(9, llm.streaming ? 1 : 0);
            ps.setInt(10, 1);
            ps.setLong(11, now);
            ps.executeUpdate();
            System.out.println("[INIT] Default LLM provider seeded from config.json");
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to seed default provider: " + e.getMessage());
        }
    }

    private static String deriveName(String provider, String modelName) {
        String p = provider != null ? provider.substring(0, 1).toUpperCase() + provider.substring(1) : "Custom";
        String m = modelName != null && !modelName.isBlank() ? " / " + modelName : "";
        return p + m;
    }

    // ===== CRUD =====

    public Provider create(String name, String provider, String baseUrl, String apiKey,
                           String modelName, Double temperature, Integer maxTokens,
                           boolean streaming) {
        String id = "provider-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO llm_provider (id, name, provider, base_url, api_key, model_name, " +
                     "temperature, max_tokens, streaming, is_default, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, provider);
            ps.setString(4, baseUrl);
            ps.setString(5, apiKey);
            ps.setString(6, modelName);
            if (temperature != null) ps.setDouble(7, temperature);
            else ps.setNull(7, Types.REAL);
            if (maxTokens != null) ps.setInt(8, maxTokens);
            else ps.setNull(8, Types.INTEGER);
            ps.setInt(9, streaming ? 1 : 0);
            ps.setInt(10, 0);
            ps.setLong(11, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create provider: " + e.getMessage(), e);
        }
        return new Provider(id, name, provider, baseUrl, apiKey, modelName,
                temperature, maxTokens, streaming, false, now);
    }

    public Provider update(String id, String name, String provider, String baseUrl, String apiKey,
                           String modelName, Double temperature, Integer maxTokens, boolean streaming) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE llm_provider SET name=?, provider=?, base_url=?, api_key=?, model_name=?, " +
                     "temperature=?, max_tokens=?, streaming=? WHERE id=?")) {
            ps.setString(1, name);
            ps.setString(2, provider);
            ps.setString(3, baseUrl);
            ps.setString(4, apiKey);
            ps.setString(5, modelName);
            if (temperature != null) ps.setDouble(6, temperature);
            else ps.setNull(6, Types.REAL);
            if (maxTokens != null) ps.setInt(7, maxTokens);
            else ps.setNull(7, Types.INTEGER);
            ps.setInt(8, streaming ? 1 : 0);
            ps.setString(9, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Provider not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update provider: " + e.getMessage(), e);
        }
        Provider updated = getById(id);
        // 如果更新的是当前激活的 Provider，同步 config.llm
        if (updated != null && updated.isDefault) {
            syncConfigFromProvider(updated);
        }
        return updated;
    }

    public void delete(String id) {
        Provider p = getById(id);
        if (p == null) return;
        if (p.isDefault) {
            throw new RuntimeException("Cannot delete the active provider. Switch to another first.");
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM llm_provider WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete provider: " + e.getMessage(), e);
        }
    }

    public Provider getById(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, provider, base_url, api_key, model_name, " +
                     "temperature, max_tokens, streaming, is_default, created_at FROM llm_provider WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public List<Provider> list() {
        List<Provider> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, name, provider, base_url, api_key, model_name, " +
                     "temperature, max_tokens, streaming, is_default, created_at FROM llm_provider " +
                     "ORDER BY is_default DESC, created_at ASC")) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException ignored) {}
        return result;
    }

    /** 设置激活的 Provider（事务保证唯一 is_default=1） */
    public Provider setActive(String id) {
        Provider p = getById(id);
        if (p == null) throw new RuntimeException("Provider not found: " + id);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                conn.createStatement().executeUpdate("UPDATE llm_provider SET is_default = 0 WHERE is_default = 1");
                try (PreparedStatement ps = conn.prepareStatement("UPDATE llm_provider SET is_default = 1 WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set active provider: " + e.getMessage(), e);
        }

        syncConfigFromProvider(p);
        return getById(id);
    }

    public Provider getActive() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, provider, base_url, api_key, model_name, " +
                     "temperature, max_tokens, streaming, is_default, created_at FROM llm_provider WHERE is_default = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException ignored) {}
        // fallback: 返回第一个
        List<Provider> all = list();
        return all.isEmpty() ? null : all.get(0);
    }

    /** 将 Provider 同步到 config.llm 并持久化 */
    private void syncConfigFromProvider(Provider p) {
        var llm = config.getLlm();
        llm.provider = p.provider;
        llm.baseUrl = p.baseUrl;
        llm.apiKey = p.apiKey;
        llm.modelName = p.modelName;
        llm.temperature = p.temperature;
        llm.maxTokens = p.maxTokens;
        llm.streaming = p.streaming;
        config.save();
    }

    /** 从 config.llm 同步到当前激活的 Provider（供 settings API 双向同步） */
    public void syncActiveFromConfig() {
        Provider active = getActive();
        if (active == null) return;
        var llm = config.getLlm();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE llm_provider SET provider=?, base_url=?, api_key=?, model_name=?, " +
                     "temperature=?, max_tokens=?, streaming=? WHERE id=?")) {
            ps.setString(1, llm.provider);
            ps.setString(2, llm.baseUrl);
            ps.setString(3, llm.apiKey);
            ps.setString(4, llm.modelName);
            if (llm.temperature != null) ps.setDouble(5, llm.temperature);
            else ps.setNull(5, Types.REAL);
            if (llm.maxTokens != null) ps.setInt(6, llm.maxTokens);
            else ps.setNull(6, Types.INTEGER);
            ps.setInt(7, llm.streaming ? 1 : 0);
            ps.setString(8, active.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to sync active provider from config: " + e.getMessage());
        }
    }

    private Provider mapRow(ResultSet rs) throws SQLException {
        double temp = rs.getDouble("temperature");
        boolean tempNull = rs.wasNull();
        int tokens = rs.getInt("max_tokens");
        boolean tokensNull = rs.wasNull();
        return new Provider(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("provider"),
                rs.getString("base_url"),
                rs.getString("api_key"),
                rs.getString("model_name"),
                tempNull ? null : temp,
                tokensNull ? null : tokens,
                rs.getInt("streaming") == 1,
                rs.getInt("is_default") == 1,
                rs.getLong("created_at")
        );
    }

    public record Provider(String id, String name, String provider, String baseUrl,
                           String apiKey, String modelName,
                           Double temperature, Integer maxTokens,
                           boolean streaming, boolean isDefault, long createdAt) {}
}
