package com.example.rag.blog;

import com.example.rag.service.RagService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 博客文章向量化索引管理
 * 支持增量索引：通过 SHA-256 内容哈希检测变更，避免重复索引
 */
public class BlogIndexer {

    private final RagService ragService;
    private final BlogStore blogStore;

    public BlogIndexer(RagService ragService, BlogStore blogStore) {
        this.ragService = ragService;
        this.blogStore = blogStore;
    }

    /**
     * 增量索引：计算内容哈希，与存储的哈希比较
     * - 哈希相同则跳过
     * - 哈希不同则移除旧索引再重新索引
     */
    public void indexArticle(BlogStore.Article article) {
        String contentHash = sha256(article.title() + "|" + article.content());
        String storedHash = blogStore.getIndexHash(article.slug());

        if (contentHash.equals(storedHash)) {
            System.out.println("[BLOG-INDEX] Article unchanged, skipping: " + article.slug());
            return;
        }

        // Content changed or new — remove old segments first
        if (storedHash != null) {
            removeArticle(article.slug());
        }

        try {
            ragService.indexBlogArticle(
                    article.id(),
                    article.slug(),
                    article.title(),
                    article.content(),
                    article.category()
            );
            blogStore.saveIndexHash(article.slug(), contentHash, 0);
            System.out.println("[BLOG-INDEX] Indexed article " + article.slug());
        } catch (Exception e) {
            System.err.println("[BLOG-INDEX] Failed to index article " + article.slug() + ": " + e.getMessage());
        }
    }

    /**
     * 从向量库移除文章索引，同时清理哈希记录
     */
    public void removeArticle(String slug) {
        try {
            ragService.removeBlogSegments(slug);
        } catch (Exception e) {
            System.err.println("[BLOG-INDEX] Failed to remove article " + slug + ": " + e.getMessage());
        }
        blogStore.deleteIndexHash(slug);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
