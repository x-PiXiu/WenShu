package com.example.rag.blog;

import com.example.rag.config.DatabasePool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 博客文章持久化：基于 SQLite 存储
 */
public class BlogStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private volatile BlogIndexer blogIndexer;

    public void setBlogIndexer(BlogIndexer blogIndexer) {
        this.blogIndexer = blogIndexer;
    }

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public BlogStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blog_article (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    slug TEXT NOT NULL UNIQUE,
                    summary TEXT,
                    content TEXT NOT NULL,
                    content_type TEXT NOT NULL DEFAULT 'md',
                    category TEXT,
                    tags TEXT,
                    cover_image TEXT,
                    status TEXT NOT NULL DEFAULT 'draft',
                    is_top INTEGER NOT NULL DEFAULT 0,
                    view_count INTEGER NOT NULL DEFAULT 0,
                    word_count INTEGER NOT NULL DEFAULT 0,
                    published_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_article_status ON blog_article(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_article_slug ON blog_article(slug)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_article_category ON blog_article(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_article_published ON blog_article(published_at DESC)");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blog_category (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    slug TEXT NOT NULL UNIQUE,
                    description TEXT,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blog_index_hash (
                    slug TEXT PRIMARY KEY,
                    content_hash TEXT NOT NULL,
                    segment_count INTEGER NOT NULL DEFAULT 0,
                    indexed_at INTEGER NOT NULL
                )
            """);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to init blog tables: " + e.getMessage(), e);
        }
    }

    // ===== Article CRUD =====

    public Article createArticle(String title, String content, String contentType, String category, List<String> tags, String summary, String coverImage) {
        String id = "post-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = generateSlug(title);
        long now = System.currentTimeMillis();
        int wordCount = content != null ? content.length() : 0;
        String tagsJson = serializeTags(tags);

        String sql = "INSERT INTO blog_article (id, title, slug, summary, content, content_type, category, tags, cover_image, status, word_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, title);
            ps.setString(3, slug);
            ps.setString(4, summary);
            ps.setString(5, content);
            ps.setString(6, contentType != null ? contentType : "md");
            ps.setString(7, category);
            ps.setString(8, tagsJson);
            ps.setString(9, coverImage);
            ps.setInt(10, wordCount);
            ps.setLong(11, now);
            ps.setLong(12, now);
            ps.executeUpdate();
            return new Article(id, title, slug, summary, content, contentType != null ? contentType : "md",
                    category, tags, coverImage, "draft", false, 0, wordCount, null, now, now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create article: " + e.getMessage(), e);
        }
    }

    public Article updateArticle(String id, String title, String content, String category, List<String> tags, String summary, String coverImage) {
        long now = System.currentTimeMillis();
        int wordCount = content != null ? content.length() : 0;
        String tagsJson = serializeTags(tags);

        String sql = "UPDATE blog_article SET title = ?, summary = ?, content = ?, category = ?, tags = ?, cover_image = ?, word_count = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, summary);
            ps.setString(3, content);
            ps.setString(4, category);
            ps.setString(5, tagsJson);
            ps.setString(6, coverImage);
            ps.setInt(7, wordCount);
            ps.setLong(8, now);
            ps.setString(9, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Article not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update article: " + e.getMessage(), e);
        }
        Article article = getById(id);
        if (article != null && "published".equals(article.status()) && blogIndexer != null) {
            blogIndexer.removeArticle(article.slug());
            blogIndexer.indexArticle(article);
        }
        return article;
    }

    public void deleteArticle(String id) {
        Article article = getById(id);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM blog_article WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete article: " + e.getMessage(), e);
        }
        if (article != null && "published".equals(article.status()) && blogIndexer != null) {
            blogIndexer.removeArticle(article.slug());
        }
    }

    // ===== Status Management =====

    public Article publishArticle(String id) {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE blog_article SET status = 'published', published_at = ?, updated_at = ? WHERE id = ?")) {
            ps.setLong(1, now);
            ps.setLong(2, now);
            ps.setString(3, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Article not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to publish article: " + e.getMessage(), e);
        }
        Article article = getById(id);
        if (article != null && blogIndexer != null) {
            blogIndexer.indexArticle(article);
        }
        return article;
    }

    public Article unpublishArticle(String id) {
        Article article = getById(id);
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE blog_article SET status = 'draft', updated_at = ? WHERE id = ?")) {
            ps.setLong(1, now);
            ps.setString(2, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Article not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unpublish article: " + e.getMessage(), e);
        }
        if (article != null && blogIndexer != null) {
            blogIndexer.removeArticle(article.slug());
        }
        return getById(id);
    }

    // ===== Queries =====

    public Article getById(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM blog_article WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get article: " + e.getMessage(), e);
        }
    }

    public Article getBySlug(String slug) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM blog_article WHERE slug = ?")) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get article by slug: " + e.getMessage(), e);
        }
    }

    public void incrementViewCount(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE blog_article SET view_count = view_count + 1 WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public PageResult<Article> listPublished(int page, int size, String category, String tag) {
        StringBuilder where = new StringBuilder("WHERE status = 'published'");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            params.add(category);
        }
        if (tag != null && !tag.isBlank()) {
            where.append(" AND tags LIKE ?");
            params.add("%\"" + tag + "\"%");
        }

        int total = countWhere(where.toString(), params);
        List<Article> items = queryArticles(where + " ORDER BY is_top DESC, published_at DESC LIMIT ? OFFSET ?", params, size, (page - 1) * size);

        return new PageResult<>(items, total, page, size);
    }

    public PageResult<Article> listAll(int page, int size) {
        int total = countWhere("", Collections.emptyList());
        List<Article> items = queryArticles("ORDER BY updated_at DESC LIMIT ? OFFSET ?", Collections.emptyList(), size, (page - 1) * size);
        return new PageResult<>(items, total, page, size);
    }

    public List<Article> searchArticles(String keyword) {
        String like = "%" + keyword + "%";
        String where = "WHERE status = 'published' AND (title LIKE ? OR content LIKE ? OR tags LIKE ?)";
        List<Object> params = List.of(like, like, like);
        return queryArticles(where + " ORDER BY published_at DESC LIMIT 20", params, 0, 0);
    }

    public int countPublished() {
        return countWhere("WHERE status = 'published'", Collections.emptyList());
    }

    public List<Article> listAllPublished() {
        return queryArticles("WHERE status = 'published' ORDER BY published_at DESC", Collections.emptyList(), 0, 0);
    }

    public int countByCategory(String category) {
        return countWhere("WHERE status = 'published' AND category = ?", List.of(category));
    }

    // ===== Category Queries =====

    public List<Category> listCategories() {
        List<Category> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM blog_category ORDER BY sort_order ASC, name ASC")) {
            while (rs.next()) result.add(mapCategoryRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list categories: " + e.getMessage(), e);
        }
        return result;
    }

    public Category createCategory(String name, String slug, String description) {
        String id = "cat-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO blog_category (id, name, slug, description, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, 0, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, slug);
            ps.setString(4, description != null ? description : "");
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
            return new Category(id, name, slug, description != null ? description : "", 0, now, now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create category: " + e.getMessage(), e);
        }
    }

    public Category updateCategory(String id, String name, String slug, String description) {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE blog_category SET name = ?, slug = ?, description = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, slug);
            ps.setString(3, description);
            ps.setLong(4, now);
            ps.setString(5, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Category not found: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update category: " + e.getMessage(), e);
        }
        return new Category(id, name, slug, description, 0, 0, now);
    }

    public void deleteCategory(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM blog_category WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete category: " + e.getMessage(), e);
        }
    }

    public List<String> getAllTags() {
        Set<String> tagSet = new LinkedHashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT tags FROM blog_article WHERE status = 'published' AND tags IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> tags = deserializeTags(rs.getString("tags"));
                tagSet.addAll(tags);
            }
        } catch (SQLException ignored) {}
        return new ArrayList<>(tagSet);
    }

    // ===== Internal Helpers =====

    private List<Article> queryArticles(String suffix, List<Object> params, int limit, int offset) {
        List<Article> result = new ArrayList<>();
        String sql = "SELECT * FROM blog_article " + suffix;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            if (limit > 0) {
                int pIdx = params.size() + 1;
                ps.setInt(pIdx, limit);
                ps.setInt(pIdx + 1, offset);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query articles: " + e.getMessage(), e);
        }
        return result;
    }

    private int countWhere(String where, List<Object> params) {
        String sql = "SELECT COUNT(*) FROM blog_article " + where;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private Article mapRow(ResultSet rs) throws SQLException {
        return new Article(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("slug"),
                rs.getString("summary"),
                rs.getString("content"),
                rs.getString("content_type"),
                rs.getString("category"),
                deserializeTags(rs.getString("tags")),
                rs.getString("cover_image"),
                rs.getString("status"),
                rs.getInt("is_top") == 1,
                rs.getInt("view_count"),
                rs.getInt("word_count"),
                rs.getObject("published_at") != null ? rs.getLong("published_at") : null,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private Category mapCategoryRow(ResultSet rs) throws SQLException {
        return new Category(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                rs.getInt("sort_order"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private String generateSlug(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "post";
        slug = slug + "-" + System.currentTimeMillis() % 10000;
        return slug;
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "[]";
        try { return MAPPER.writeValueAsString(tags); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> deserializeTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, STRING_LIST_TYPE); }
        catch (Exception e) { return List.of(); }
    }

    // ===== Records =====

    public record Article(String id, String title, String slug, String summary,
                          String content, String contentType, String category,
                          List<String> tags, String coverImage, String status,
                          boolean isTop, int viewCount, int wordCount,
                          Long publishedAt, long createdAt, long updatedAt) {}

    public record Category(String id, String name, String slug, String description,
                           int sortOrder, long createdAt, long updatedAt) {}

    public record PageResult<T>(List<T> items, int total, int page, int size) {}

    // ===== Incremental Index Hash Support =====

    public String getIndexHash(String slug) {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("SELECT content_hash FROM blog_index_hash WHERE slug = ?")) {
            ps.setString(1, slug);
            var rs = ps.executeQuery();
            return rs.next() ? rs.getString("content_hash") : null;
        } catch (SQLException e) {
            return null;
        }
    }

    public void saveIndexHash(String slug, String contentHash, int segmentCount) {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("""
                 INSERT INTO blog_index_hash (slug, content_hash, segment_count, indexed_at)
                 VALUES (?, ?, ?, ?)
                 ON CONFLICT(slug) DO UPDATE SET content_hash = excluded.content_hash,
                     segment_count = excluded.segment_count, indexed_at = excluded.indexed_at
             """)) {
            ps.setString(1, slug);
            ps.setString(2, contentHash);
            ps.setInt(3, segmentCount);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to save index hash for " + slug + ": " + e.getMessage());
        }
    }

    public void deleteIndexHash(String slug) {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("DELETE FROM blog_index_hash WHERE slug = ?")) {
            ps.setString(1, slug);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
