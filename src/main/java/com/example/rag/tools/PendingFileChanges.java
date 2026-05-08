package com.example.rag.tools;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件变更暂存：LLM 工具修改文件时先暂存，等待用户确认后再实际写入
 */
public class PendingFileChanges {

    private static final ConcurrentHashMap<String, PendingChange> PENDING = new ConcurrentHashMap<>();

    public record PendingChange(
            String id, String path, String oldContent, String newContent,
            String type, long createdAt
    ) {}

    public static PendingChange stage(String path, String oldContent, String newContent, String type) {
        String id = "chg-" + UUID.randomUUID().toString().substring(0, 8);
        PendingChange change = new PendingChange(id, path, oldContent, newContent, type, System.currentTimeMillis());
        PENDING.put(id, change);
        return change;
    }

    public static PendingChange get(String id) {
        return PENDING.get(id);
    }

    public static PendingChange apply(String id) {
        PendingChange change = PENDING.remove(id);
        if (change == null) return null;
        try {
            java.nio.file.Path file = java.nio.file.Path.of(change.path());
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, change.newContent());
        } catch (Exception e) {
            // 重新放回，允许重试
            PENDING.put(id, change);
            throw new RuntimeException("写入失败: " + e.getMessage(), e);
        }
        return change;
    }

    public static PendingChange reject(String id) {
        return PENDING.remove(id);
    }

    /** 获取完整 diff 数据（含 oldContent / newContent） */
    public static Map<String, Object> getDiffData(String id) {
        PendingChange change = PENDING.get(id);
        if (change == null) return null;
        return Map.of(
                "id", change.id(),
                "path", change.path(),
                "oldContent", change.oldContent() != null ? change.oldContent() : "",
                "newContent", change.newContent() != null ? change.newContent() : "",
                "type", change.type(),
                "oldLines", change.oldContent() != null ? change.oldContent().split("\n").length : 0,
                "newLines", change.newContent() != null ? change.newContent().split("\n").length : 0
        );
    }
}
