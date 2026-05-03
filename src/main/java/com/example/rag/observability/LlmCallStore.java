package com.example.rag.observability;

import com.example.rag.config.DatabasePool;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM 调用日志持久化：记录每次 LLM 调用的 Token、延迟、模型等信息
 */
public class LlmCallStore {

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public LlmCallStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS llm_call_log (
                    id TEXT PRIMARY KEY,
                    call_type TEXT NOT NULL,
                    model TEXT,
                    input_tokens INTEGER,
                    output_tokens INTEGER,
                    duration_ms INTEGER,
                    finish_reason TEXT,
                    error TEXT,
                    created_at INTEGER NOT NULL
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_llm_call_time ON llm_call_log(created_at DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_llm_call_type ON llm_call_log(call_type)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init llm_call_log table: " + e.getMessage(), e);
        }
    }

    public void logCall(String callType, String model, Integer inputTokens, Integer outputTokens,
                        Long durationMs, String finishReason, String error) {
        String id = "llm-" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO llm_call_log (id, call_type, model, input_tokens, output_tokens, duration_ms, finish_reason, error, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, callType);
            ps.setString(3, model);
            ps.setObject(4, inputTokens);
            ps.setObject(5, outputTokens);
            ps.setObject(6, durationMs);
            ps.setString(7, finishReason);
            ps.setString(8, error);
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public List<LlmCall> listRecent(int limit) {
        List<LlmCall> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM llm_call_log ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException ignored) {}
        return result;
    }

    public LlmStats getTotalStats() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT COUNT(*) as total_calls,
                        COALESCE(SUM(input_tokens), 0) as total_input,
                        COALESCE(SUM(output_tokens), 0) as total_output,
                        COALESCE(AVG(duration_ms), 0) as avg_duration
                 FROM llm_call_log
             """)) {
            if (rs.next()) {
                return new LlmStats(rs.getInt("total_calls"),
                        rs.getLong("total_input"), rs.getLong("total_output"),
                        rs.getDouble("avg_duration"));
            }
        } catch (SQLException ignored) {}
        return new LlmStats(0, 0, 0, 0);
    }

    public LlmStats getTodayStats() {
        long dayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT COUNT(*) as total_calls,
                        COALESCE(SUM(input_tokens), 0) as total_input,
                        COALESCE(SUM(output_tokens), 0) as total_output,
                        COALESCE(AVG(duration_ms), 0) as avg_duration
                 FROM llm_call_log WHERE created_at >= ?
             """)) {
            ps.setLong(1, dayStart);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LlmStats(rs.getInt("total_calls"),
                            rs.getLong("total_input"), rs.getLong("total_output"),
                            rs.getDouble("avg_duration"));
                }
            }
        } catch (SQLException ignored) {}
        return new LlmStats(0, 0, 0, 0);
    }

    public List<DailyStat> getDailyStats(int days) {
        List<DailyStat> result = new ArrayList<>();
        long startTime = System.currentTimeMillis() - (long) days * 86400000;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT (created_at / 86400000) as day_bucket,
                        COUNT(*) as calls,
                        COALESCE(SUM(input_tokens), 0) as input_tokens,
                        COALESCE(SUM(output_tokens), 0) as output_tokens,
                        COALESCE(AVG(duration_ms), 0) as avg_duration
                 FROM llm_call_log WHERE created_at >= ?
                 GROUP BY day_bucket ORDER BY day_bucket ASC
             """)) {
            ps.setLong(1, startTime);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DailyStat(
                            rs.getLong("day_bucket") * 86400000,
                            rs.getInt("calls"),
                            rs.getLong("input_tokens"),
                            rs.getLong("output_tokens"),
                            rs.getDouble("avg_duration")));
                }
            }
        } catch (SQLException ignored) {}
        return result;
    }

    public int cleanupBefore(long timestamp) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM llm_call_log WHERE created_at < ?")) {
            ps.setLong(1, timestamp);
            return ps.executeUpdate();
        } catch (SQLException e) {
            return 0;
        }
    }

    private LlmCall mapRow(ResultSet rs) throws SQLException {
        return new LlmCall(
                rs.getString("id"),
                rs.getString("call_type"),
                rs.getString("model"),
                rs.getObject("input_tokens") != null ? rs.getInt("input_tokens") : null,
                rs.getObject("output_tokens") != null ? rs.getInt("output_tokens") : null,
                rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null,
                rs.getString("finish_reason"),
                rs.getString("error"),
                rs.getLong("created_at")
        );
    }

    public record LlmCall(String id, String callType, String model,
                           Integer inputTokens, Integer outputTokens,
                           Long durationMs, String finishReason, String error,
                           long createdAt) {}

    public record LlmStats(int totalCalls, long totalInputTokens, long totalOutputTokens,
                            double avgDurationMs) {}

    public record DailyStat(long dayStart, int calls, long inputTokens, long outputTokens,
                             double avgDurationMs) {}

    public record HourlyStat(int hour, int calls, long inputTokens, long outputTokens,
                              double avgDurationMs) {}

    public record TypeCount(String callType, int count) {}

    public record LatencyBucket(String range, int count) {}

    public List<HourlyStat> getHourlyStats() {
        long todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000);
        Map<Integer, HourlyStat> map = new java.util.TreeMap<>();
        for (int h = 0; h < 24; h++) map.put(h, new HourlyStat(h, 0, 0, 0, 0));
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT CAST(((created_at - ?) / 3600000) AS INTEGER) as hour_offset,
                        COUNT(*) as calls,
                        COALESCE(SUM(input_tokens), 0) as input_tokens,
                        COALESCE(SUM(output_tokens), 0) as output_tokens,
                        COALESCE(AVG(duration_ms), 0) as avg_duration
                 FROM llm_call_log WHERE created_at >= ?
                 GROUP BY hour_offset
             """)) {
            ps.setLong(1, todayStart);
            ps.setLong(2, todayStart);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int h = rs.getInt("hour_offset");
                    if (h >= 0 && h < 24) {
                        map.put(h, new HourlyStat(h, rs.getInt("calls"),
                                rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                                rs.getDouble("avg_duration")));
                    }
                }
            }
        } catch (SQLException ignored) {}
        return new ArrayList<>(map.values());
    }

    public List<TypeCount> getTypeDistribution() {
        List<TypeCount> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT call_type, COUNT(*) as cnt FROM llm_call_log GROUP BY call_type ORDER BY cnt DESC")) {
            while (rs.next()) result.add(new TypeCount(rs.getString("call_type"), rs.getInt("cnt")));
        } catch (SQLException ignored) {}
        return result;
    }

    public List<LatencyBucket> getLatencyDistribution() {
        List<LatencyBucket> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT CASE
                     WHEN duration_ms < 500 THEN '< 500ms'
                     WHEN duration_ms < 1000 THEN '500ms-1s'
                     WHEN duration_ms < 3000 THEN '1s-3s'
                     WHEN duration_ms < 5000 THEN '3s-5s'
                     WHEN duration_ms < 10000 THEN '5s-10s'
                     ELSE '> 10s'
                 END as range_label,
                 COUNT(*) as cnt
                 FROM llm_call_log WHERE duration_ms IS NOT NULL AND error IS NULL
                 GROUP BY range_label
                 ORDER BY MIN(duration_ms)
             """)) {
            while (rs.next()) result.add(new LatencyBucket(rs.getString("range_label"), rs.getInt("cnt")));
        } catch (SQLException ignored) {}
        return result;
    }
}
