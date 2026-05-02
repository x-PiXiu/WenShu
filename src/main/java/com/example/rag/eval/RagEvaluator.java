package com.example.rag.eval;

import com.example.rag.eval.EvalTestCaseStore.TestCase;
import com.example.rag.prompt.RagPromptTemplate;
import com.example.rag.prompt.RagPromptTemplate.Reference;
import com.example.rag.search.HybridSearcher;
import com.example.rag.search.HybridSearcher.SearchResult;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自动化 RAG 评估
 */
public class RagEvaluator {

    private final ChatModel chatModel;
    private final HybridSearcher searcher;

    public RagEvaluator(ChatModel chatModel, HybridSearcher searcher) {
        this.chatModel = chatModel;
        this.searcher = searcher;
    }

    public RagEvaluator(HybridSearcher searcher) {
        this.chatModel = null;
        this.searcher = searcher;
    }

    // ==================== 检索评估（不调用 LLM） ====================

    /**
     * 纯检索质量评估：Recall@K / Precision@K / MRR / 按分类统计
     */
    public RetrievalReport evaluateRetrieval(List<TestCase> testCases, int topK) {
        List<CaseResult> caseResults = new ArrayList<>();
        Map<String, List<CaseResult>> byCategory = new LinkedHashMap<>();

        for (TestCase tc : testCases) {
            List<SearchResult> results = searcher.search(tc.question());
            List<SearchResult> topResults = results.subList(0, Math.min(topK, results.size()));

            Set<String> retrievedIds = topResults.stream()
                    .map(SearchResult::segmentId)
                    .collect(Collectors.toSet());
            Set<String> retrievedSources = topResults.stream()
                    .map(SearchResult::source)
                    .collect(Collectors.toSet());

            // 用 source 匹配（比 segmentId 稳定）
            boolean hit = tc.relevantSources().stream()
                    .anyMatch(src -> retrievedSources.stream()
                            .anyMatch(rs -> rs.contains(src)));

            double recall = RetrievalEvaluator.recallAtK(
                    topResults.stream().map(SearchResult::source).toList(),
                    tc.relevantSources(), topK);
            double precision = RetrievalEvaluator.precisionAtK(
                    topResults.stream().map(SearchResult::source).toList(),
                    tc.relevantSources(), topK);

            double topScore = topResults.isEmpty() ? 0.0 : topResults.get(0).rrfScore();

            List<String> topSources = topResults.stream()
                    .map(SearchResult::source)
                    .toList();

            CaseResult cr = new CaseResult(tc.id(), tc.question(), tc.category(),
                    hit, topSources, topScore, recall, precision);
            caseResults.add(cr);
            byCategory.computeIfAbsent(tc.category(), k -> new ArrayList<>()).add(cr);
        }

        // Aggregate
        double hitRate = (double) caseResults.stream().filter(CaseResult::hit).count() / testCases.size();
        double avgRecall = caseResults.stream().mapToDouble(CaseResult::recallAtK).average().orElse(0);
        double avgPrecision = caseResults.stream().mapToDouble(CaseResult::precisionAtK).average().orElse(0);

        // MRR: first relevant source position
        double mrr = computeMRR(testCases, topK);

        Map<String, CategoryStats> categoryStats = new LinkedHashMap<>();
        for (var entry : byCategory.entrySet()) {
            List<CaseResult> cases = entry.getValue();
            double catHitRate = (double) cases.stream().filter(CaseResult::hit).count() / cases.size();
            double catRecall = cases.stream().mapToDouble(CaseResult::recallAtK).average().orElse(0);
            double catPrecision = cases.stream().mapToDouble(CaseResult::precisionAtK).average().orElse(0);
            categoryStats.put(entry.getKey(), new CategoryStats(cases.size(), catHitRate, catRecall, catPrecision));
        }

        return new RetrievalReport(testCases.size(), topK, hitRate, avgRecall, avgPrecision, mrr,
                categoryStats, caseResults);
    }

    private double computeMRR(List<TestCase> testCases, int topK) {
        double totalMrr = 0.0;
        for (TestCase tc : testCases) {
            List<SearchResult> results = searcher.search(tc.question());
            List<SearchResult> topResults = results.subList(0, Math.min(topK, results.size()));

            for (int i = 0; i < topResults.size(); i++) {
                String source = topResults.get(i).source();
                boolean relevant = tc.relevantSources().stream().anyMatch(source::contains);
                if (relevant) {
                    totalMrr += 1.0 / (i + 1);
                    break;
                }
            }
        }
        return testCases.isEmpty() ? 0.0 : totalMrr / testCases.size();
    }

    // ==================== 原有 LLM 评估（保留） ====================

    /**
     * 在测试集上跑一遍，输出评估报告（含 LLM 生成评估）
     */
    public EvaluationReport evaluate(List<LegacyTestCase> testCases) {
        int total = testCases.size();
        int retrievalHits = 0;
        int generationCorrect = 0;

        for (LegacyTestCase tc : testCases) {
            List<SearchResult> results = searcher.search(tc.question());
            boolean hit = results.stream()
                    .anyMatch(r -> tc.relevantIds().contains(r.segmentId()));
            if (hit) retrievalHits++;

            String answer = generateAnswer(tc.question(), results);
            if (isCorrectAnswer(answer, tc.expectedKeywords())) {
                generationCorrect++;
            }
        }

        return new EvaluationReport(
                (double) retrievalHits / total,
                (double) generationCorrect / total,
                total
        );
    }

    String generateAnswer(String question, List<SearchResult> results) {
        if (chatModel == null) return "";
        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();
        String prompt = RagPromptTemplate.build(question, refs);
        return chatModel.chat(prompt);
    }

    boolean isCorrectAnswer(String answer, Set<String> expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) return false;
        String lower = answer.toLowerCase();
        return expectedKeywords.stream()
                .allMatch(kw -> lower.contains(kw.toLowerCase()));
    }

    // ==================== 数据结构 ====================

    public record CaseResult(
            String id, String question, String category,
            boolean hit,
            List<String> retrievedSources,
            double topScore,
            double recallAtK,
            double precisionAtK
    ) {}

    public record CategoryStats(int cases, double hitRate, double avgRecall, double avgPrecision) {}

    public record RetrievalReport(
            int totalCases, int topK,
            double hitRate,
            double avgRecallAtK,
            double avgPrecisionAtK,
            double mrr,
            Map<String, CategoryStats> byCategory,
            List<CaseResult> caseResults
    ) {}

    // Legacy records (保留向后兼容)
    public record LegacyTestCase(String question, Set<String> relevantIds,
                                  Set<String> expectedKeywords) {}

    public record EvaluationReport(double retrievalHitRate,
                                   double generationCorrectRate,
                                   int totalCases) {
        public String summary() {
            return String.format(
                    "检索命中率: %.1f%% | 生成正确率: %.1f%% | 总测试用例: %d",
                    retrievalHitRate * 100, generationCorrectRate * 100, totalCases
            );
        }
    }
}
