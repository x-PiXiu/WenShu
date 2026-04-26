package com.example.rag.eval;

import com.example.rag.prompt.RagPromptTemplate;
import com.example.rag.prompt.RagPromptTemplate.Reference;
import com.example.rag.search.HybridSearcher;
import com.example.rag.search.HybridSearcher.SearchResult;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.List;
import java.util.Set;

/**
 * 自动化 RAG 评估
 */
public class RagEvaluator {

    private final ChatLanguageModel chatModel;
    private final HybridSearcher searcher;

    public RagEvaluator(ChatLanguageModel chatModel, HybridSearcher searcher) {
        this.chatModel = chatModel;
        this.searcher = searcher;
    }

    /**
     * 在测试集上跑一遍，输出评估报告
     */
    public EvaluationReport evaluate(List<TestCase> testCases) {
        int total = testCases.size();
        int retrievalHits = 0;
        int generationCorrect = 0;

        for (TestCase tc : testCases) {
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
        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String prompt = RagPromptTemplate.build(question, refs);
        return chatModel.generate(prompt);
    }

    /**
     * 判断答案是否正确：期望关键词列表中的每个词都在答案中出现
     */
    boolean isCorrectAnswer(String answer, Set<String> expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) return false;
        String lower = answer.toLowerCase();
        return expectedKeywords.stream()
                .allMatch(kw -> lower.contains(kw.toLowerCase()));
    }

    public record TestCase(String question, Set<String> relevantIds,
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
