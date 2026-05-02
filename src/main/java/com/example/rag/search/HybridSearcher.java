package com.example.rag.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器：向量 + 关键词（BM25）双路召回 -> RRF 融合
 */
public class HybridSearcher {

    private final EmbeddingStore<TextSegment> vectorStore;
    private final EmbeddingModel embeddingModel;
    private final int vectorTopK;
    private final int keywordTopK;
    private final double rrfK;
    private final double minScore;

    // 内存倒排索引：关键词 -> 片段ID列表
    private final Map<String, List<String>> invertedIndex = new HashMap<>();
    // 片段ID -> 片段文本（返回结果时携带原文）
    private final Map<String, String> segmentTextMap = new HashMap<>();
    // 片段ID -> 来源信息
    private final Map<String, String> segmentSourceMap = new HashMap<>();
    // 文档频率：每个词在多少个片段中出现过（用于 BM25 IDF 计算）
    private final Map<String, Integer> docFrequency = new HashMap<>();
    // 总片段数（用于 BM25 IDF 计算）
    private int totalSegments = 0;

    // LRU cache for query embeddings — avoids redundant embedding API calls
    private final LinkedHashMap<String, Embedding> embeddingCache = new LinkedHashMap<String, Embedding>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Embedding> eldest) {
            return size() > 100;
        }
    };

    public HybridSearcher(EmbeddingStore<TextSegment> vectorStore,
                         EmbeddingModel embeddingModel,
                         int vectorTopK, int keywordTopK, double rrfK,
                         double minScore) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.vectorTopK = vectorTopK;
        this.keywordTopK = keywordTopK;
        this.rrfK = rrfK;
        this.minScore = minScore;
    }

    /**
     * 注册一个片段到关键词索引
     * 在文档索引阶段调用，将每个 TextSegment 建立倒排索引
     */
    public void indexSegment(String segmentId, String text, String source) {
        segmentTextMap.put(segmentId, text);
        segmentSourceMap.put(segmentId, source);
        totalSegments++;

        Set<String> uniqueTerms = tokenize(text);
        for (String term : uniqueTerms) {
            invertedIndex.computeIfAbsent(term, k -> new ArrayList<>()).add(segmentId);
            docFrequency.merge(term, 1, Integer::sum);
        }
    }

    /**
     * 混合搜索入口
     */
    public List<SearchResult> search(String query) {
        List<EmbeddingMatch<TextSegment>> vectorResults = vectorSearch(query);
        List<KeywordMatch> keywordResults = keywordSearch(query);
        return rrfFusion(vectorResults, keywordResults);
    }

    /**
     * 混合搜索（按 source 前缀过滤）
     * 扩大搜索范围，避免目标来源的结果被知识库结果挤出 topK
     */
    public List<SearchResult> search(String query, String sourcePrefix) {
        int expandedTopK = vectorTopK * 10;
        List<EmbeddingMatch<TextSegment>> vectorResults = vectorSearch(query, expandedTopK);
        List<KeywordMatch> keywordResults = keywordSearch(query);
        // Keep BM25-only results for filtered search (some relevant segments may lack vector match)
        List<SearchResult> all = rrfFusion(vectorResults, keywordResults, expandedTopK, false);
        return all.stream()
                .filter(r -> r.source().startsWith(sourcePrefix))
                .toList();
    }

    List<EmbeddingMatch<TextSegment>> vectorSearch(String query) {
        return vectorSearch(query, vectorTopK);
    }

    List<EmbeddingMatch<TextSegment>> vectorSearch(String query, int maxResults) {
        Embedding queryEmbedding;
        synchronized (embeddingCache) {
            queryEmbedding = embeddingCache.get(query);
        }
        if (queryEmbedding == null) {
            queryEmbedding = embeddingModel.embed(query).content();
            synchronized (embeddingCache) {
                embeddingCache.put(query, queryEmbedding);
            }
        }
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        return vectorStore.search(request).matches();
    }

    /**
     * BM25 关键词检索
     */
    List<KeywordMatch> keywordSearch(String query) {
        if (totalSegments == 0) return Collections.emptyList();

        List<String> queryTerms = new ArrayList<>(tokenize(query));
        if (queryTerms.isEmpty()) return Collections.emptyList();

        double avgDocLen = segmentTextMap.values().stream()
                .mapToInt(String::length)
                .average()
                .orElse(1.0);
        double k1 = 1.2;
        double b = 0.75;

        Map<String, Double> bm25Scores = new HashMap<>();
        for (String term : queryTerms) {
            List<String> matchingIds = invertedIndex.getOrDefault(term, Collections.emptyList());
            double idf = Math.log((totalSegments - matchingIds.size() + 0.5)
                                  / (matchingIds.size() + 0.5));
            for (String segId : matchingIds) {
                String text = segmentTextMap.get(segId);
                int tf = countTermFrequency(text, term);
                double docLen = text.length();
                double tfNorm = (tf * (k1 + 1))
                        / (tf + k1 * (1 - b + b * docLen / avgDocLen));
                bm25Scores.merge(segId, idf * tfNorm, Double::sum);
            }
        }

        // BM25 min score: filter out near-zero noise matches
        double bm25MinScore = 0.1;

        return bm25Scores.entrySet().stream()
                .filter(e -> e.getValue() >= bm25MinScore)
                .sorted((a, b2) -> Double.compare(b2.getValue(), a.getValue()))
                .limit(keywordTopK)
                .map(e -> new KeywordMatch(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * RRF 融合（全局搜索，丢弃纯 BM25 结果）
     */
    List<SearchResult> rrfFusion(List<EmbeddingMatch<TextSegment>> vectorResults,
                                List<KeywordMatch> keywordResults) {
        return rrfFusion(vectorResults, keywordResults, vectorTopK, true);
    }

    /**
     * RRF 融合（可配置是否保留纯 BM25 结果）
     */
    List<SearchResult> rrfFusion(List<EmbeddingMatch<TextSegment>> vectorResults,
                                List<KeywordMatch> keywordResults, int limit, boolean requireVector) {

        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, EmbeddingMatch<TextSegment>> matchMap = new HashMap<>();
        Set<String> keywordIds = new HashSet<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            EmbeddingMatch<TextSegment> match = vectorResults.get(i);
            String id = match.embeddingId();
            double rrfScore = 1.0 / (rrfK + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            matchMap.put(id, match);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            String id = keywordResults.get(i).segmentId();
            double rrfScore = 1.0 / (rrfK + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            keywordIds.add(id);
        }

        double maxRrfScore = scoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        List<Map.Entry<String, Double>> sorted = scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .toList();

        List<SearchResult> results = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, Double> e : sorted) {
            rank++;
            String id = e.getKey();
            EmbeddingMatch<TextSegment> match = matchMap.get(id);
            boolean dualHit = match != null && keywordIds.contains(id);
            boolean vectorHit = match != null;
            boolean keywordHit = keywordIds.contains(id);
            double vectorScore = match != null ? match.score() : 0.0;

            String text, source, breadcrumb;
            if (match != null) {
                source = match.embedded().metadata().getString("source");
                breadcrumb = match.embedded().metadata().getString("breadcrumb");
                text = match.embedded().text();
            } else {
                text = segmentTextMap.getOrDefault(id, "");
                source = segmentSourceMap.getOrDefault(id, "unknown");
                breadcrumb = "";
            }

            if (requireVector && vectorScore <= 0) continue;

            // Lookup parent context for hierarchical enrichment
            String parentContext = "";
            if (match != null) {
                String parentBc = match.embedded().metadata().getString("parentBreadcrumb");
                if (parentBc != null && !parentBc.isEmpty()) {
                    String src = source != null ? source : "unknown";
                    parentContext = findParentContext(src, parentBc);
                }
            }

            results.add(new SearchResult(id, e.getValue(), text,
                    source != null ? source : "unknown",
                    vectorScore, breadcrumb != null ? breadcrumb : "",
                    parentContext,
                    calculateConfidence(dualHit, vectorHit, keywordHit, vectorScore, e.getValue(), maxRrfScore, rank),
                    generateExplanation(dualHit, vectorHit, keywordHit, vectorScore)));
        }
        return results;
    }

    /**
     * 计算检索置信度（0-1）
     * 综合考虑：双路命中、向量得分、RRF 排名、结果位置
     */
    static double calculateConfidence(boolean dualHit, boolean vectorHit, boolean keywordHit,
                                       double vectorScore, double rrfScore, double maxRrfScore, int rank) {
        double score = 0.0;

        // 双路命中加分（最强信号）
        if (dualHit) score += 0.30;
        else if (vectorHit) score += 0.10;
        else if (keywordHit) score += 0.10;

        // 向量绝对得分
        if (vectorScore >= 0.85) score += 0.30;
        else if (vectorScore >= 0.70) score += 0.20;
        else if (vectorScore >= 0.50) score += 0.10;

        // RRF 相对得分
        double rrfRatio = maxRrfScore > 0 ? rrfScore / maxRrfScore : 0;
        if (rrfRatio >= 0.8) score += 0.25;
        else if (rrfRatio >= 0.5) score += 0.15;
        else if (rrfRatio >= 0.3) score += 0.05;

        // 排名位置加分
        if (rank <= 2) score += 0.15;
        else if (rank <= 5) score += 0.05;

        return Math.min(1.0, score);
    }

    /**
     * 生成检索结果的人类可读解释
     */
    static String generateExplanation(boolean dualHit, boolean vectorHit, boolean keywordHit, double vectorScore) {
        if (dualHit) {
            if (vectorScore >= 0.85) return "语义高度匹配，且精确包含关键词";
            return "语义匹配，且包含关键词";
        }
        if (vectorHit) {
            if (vectorScore >= 0.85) return "语义高度匹配（相似度 " + String.format("%.0f%%", vectorScore * 100) + "）";
            if (vectorScore >= 0.70) return "语义相关（相似度 " + String.format("%.0f%%", vectorScore * 100) + "）";
            return "语义弱相关（相似度 " + String.format("%.0f%%", vectorScore * 100) + "）";
        }
        if (keywordHit) return "仅关键词匹配";
        return "检索结果";
    }

    /**
     * Find the text content of a parent section by source and parentBreadcrumb
     */
    private String findParentContext(String source, String parentBreadcrumb) {
        for (Map.Entry<String, String> entry : segmentSourceMap.entrySet()) {
            if (!entry.getValue().equals(source)) continue;
            String segId = entry.getKey();
            // Check if this segment's breadcrumb matches parentBreadcrumb
            String text = segmentTextMap.get(segId);
            if (text != null && text.contains(parentBreadcrumb)) {
                return text.length() > 200 ? text.substring(0, 200) + "..." : text;
            }
        }
        return "";
    }

    /**
     * 移除指定 source 前缀的所有段落索引
     */
    public void removeSegmentsBySource(String sourcePrefix) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, String> entry : segmentSourceMap.entrySet()) {
            if (entry.getValue().startsWith(sourcePrefix)) {
                toRemove.add(entry.getKey());
            }
        }
        for (String segId : toRemove) {
            String text = segmentTextMap.remove(segId);
            segmentSourceMap.remove(segId);
            totalSegments--;
            if (text != null) {
                Set<String> terms = tokenize(text);
                for (String term : terms) {
                    List<String> ids = invertedIndex.get(term);
                    if (ids != null) {
                        ids.remove(segId);
                        if (ids.isEmpty()) {
                            invertedIndex.remove(term);
                        }
                        docFrequency.merge(term, -1, (old, delta) -> old + delta <= 0 ? null : old + delta);
                    }
                }
            }
        }
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9\\u4e00-\\u9fa5]+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }

    private int countTermFrequency(String text, String term) {
        int count = 0;
        int idx = 0;
        String lower = text.toLowerCase();
        while ((idx = lower.indexOf(term, idx)) != -1) {
            count++;
            idx += term.length();
        }
        return count;
    }

    public record KeywordMatch(String segmentId, double bm25Score) {}

    public record SearchResult(String segmentId, double rrfScore,
                        String text, String source, double vectorScore,
                        String breadcrumb, String parentContext,
                        double confidence, String explanation) {

        public String confidenceLabel() {
            if (confidence >= 0.70) return "HIGH";
            if (confidence >= 0.40) return "MEDIUM";
            return "LOW";
        }
    }
}
