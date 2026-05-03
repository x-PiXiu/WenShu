package com.example.rag.blog;

import com.example.rag.config.DatabasePool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 媒体文件管理：上传、存储、查询、删除
 */
public class MediaStore {

    private static final String UPLOAD_DIR = "uploads";

    static {
        try {
            Path dir = Path.of(UPLOAD_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("[WARN] Failed to create uploads dir: " + e.getMessage());
        }
    }

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public MediaStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blog_media (
                    id TEXT PRIMARY KEY,
                    filename TEXT NOT NULL,
                    stored_name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    mime_type TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init blog_media table: " + e.getMessage(), e);
        }
    }

    public MediaFile upload(String filename, String mimeType, InputStream data) throws IOException {
        String ext = "";
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx >= 0) ext = filename.substring(dotIdx).toLowerCase();

        String id = "media-" + UUID.randomUUID().toString().substring(0, 8);
        String storedName = id + ext;
        Path target = Path.of(UPLOAD_DIR, storedName);

        long size;
        try {
            size = Files.copy(data, target);
        } catch (IOException e) {
            throw new IOException("Failed to save file: " + e.getMessage(), e);
        }

        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO blog_media (id, filename, stored_name, file_path, file_size, mime_type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, filename);
            ps.setString(3, storedName);
            ps.setString(4, target.toString());
            ps.setLong(5, size);
            ps.setString(6, mimeType);
            ps.setLong(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            Files.deleteIfExists(target);
            throw new IOException("Failed to save media record: " + e.getMessage(), e);
        }

        return new MediaFile(id, filename, storedName, "/uploads/" + storedName, size, mimeType, now);
    }

    public List<MediaFile> listAll() {
        List<MediaFile> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM blog_media ORDER BY created_at DESC")) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to list media: " + e.getMessage());
        }
        return result;
    }

    public MediaFile getById(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM blog_media WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean delete(String id) {
        String storedName = null;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT stored_name FROM blog_media WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) storedName = rs.getString("stored_name");
            }
        } catch (SQLException e) {
            return false;
        }
        if (storedName == null) return false;

        try {
            Files.deleteIfExists(Path.of(UPLOAD_DIR, storedName));
        } catch (IOException ignored) {}

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM blog_media WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private MediaFile mapRow(ResultSet rs) throws SQLException {
        String storedName = rs.getString("stored_name");
        return new MediaFile(
                rs.getString("id"),
                rs.getString("filename"),
                storedName,
                "/uploads/" + storedName,
                rs.getLong("file_size"),
                rs.getString("mime_type"),
                rs.getLong("created_at")
        );
    }

    public record MediaFile(String id, String filename, String storedName,
                            String url, long fileSize, String mimeType, long createdAt) {}
}
