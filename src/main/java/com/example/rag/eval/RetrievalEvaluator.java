package com.example.rag.eval;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 检索质量评估
 */
public class RetrievalEvaluator {

    /**
     * 计算 Recall@K
     */
    public static double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
        if (retrieved.isEmpty() || relevant.isEmpty() || k == 0) return 0.0;

        List<String> topK = retrieved.subList(0, Math.min(k, retrieved.size()));
        long hitCount = topK.stream()
                .filter(relevant::contains)
                .count();

        return (double) hitCount / relevant.size();
    }

    /**
     * 计算 Precision@K
     */
    public static double precisionAtK(List<String> retrieved, Set<String> relevant, int k) {
        if (retrieved.isEmpty() || k == 0) return 0.0;

        List<String> topK = retrieved.subList(0, Math.min(k, retrieved.size()));
        long hitCount = topK.stream()
                .filter(relevant::contains)
                .count();

        return (double) hitCount / topK.size();
    }

    /**
     * 计算 MRR（Mean Reciprocal Rank）
     */
    public static double meanReciprocalRank(List<List<String>> queries,
                                            Predicate<String> isRelevant) {
        double totalMrr = 0.0;

        for (List<String> retrieved : queries) {
            for (int i = 0; i < retrieved.size(); i++) {
                if (isRelevant.test(retrieved.get(i))) {
                    totalMrr += 1.0 / (i + 1);
                    break;
                }
            }
        }

        return queries.isEmpty() ? 0.0 : totalMrr / queries.size();
    }
}
