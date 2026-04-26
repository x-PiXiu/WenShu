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

    List<EmbeddingMatch<TextSegment>> vectorSearch(String query) {
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
                .maxResults(vectorTopK)
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
     * RRF 融合
     */
    List<SearchResult> rrfFusion(List<EmbeddingMatch<TextSegment>> vectorResults,
                                List<KeywordMatch> keywordResults) {

        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, EmbeddingMatch<TextSegment>> matchMap = new HashMap<>();

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
        }

        return scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(vectorTopK)
                .map(e -> {
                    String id = e.getKey();
                    EmbeddingMatch<TextSegment> match = matchMap.get(id);
                    if (match != null) {
                        String source = match.embedded().metadata().getString("source");
                        return new SearchResult(id, e.getValue(),
                                match.embedded().text(),
                                source != null ? source : "unknown",
                                match.score());
                    }
                    // Pure keyword hit — no vector support
                    return new SearchResult(id, e.getValue(),
                            segmentTextMap.getOrDefault(id, ""),
                            segmentSourceMap.getOrDefault(id, "unknown"),
                            0.0);
                })
                // Final quality gate: reject pure keyword-only results (vectorScore=0)
                // Results must have at least some vector similarity support
                .filter(r -> r.vectorScore() > 0)
                .toList();
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
                        String text, String source, double vectorScore) {}
}
