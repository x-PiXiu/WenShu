package com.example.rag.blog;

import com.example.rag.service.RagService;

/**
 * 博客文章向量化索引管理
 * 作为 BlogStore 和 RagService 之间的桥梁
 */
public class BlogIndexer {

    private final RagService ragService;

    public BlogIndexer(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 将已发布文章索引到向量库
     */
    public void indexArticle(BlogStore.Article article) {
        try {
            ragService.indexBlogArticle(
                    article.id(),
                    article.slug(),
                    article.title(),
                    article.content(),
                    article.category()
            );
        } catch (Exception e) {
            System.err.println("[BLOG-INDEX] Failed to index article " + article.slug() + ": " + e.getMessage());
        }
    }

    /**
     * 从向量库移除文章索引
     */
    public void removeArticle(String slug) {
        try {
            ragService.removeBlogSegments(slug);
        } catch (Exception e) {
            System.err.println("[BLOG-INDEX] Failed to remove article " + slug + ": " + e.getMessage());
        }
    }
}
