package com.example.rag.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 记忆重要性评分：基于规则（不调 LLM），零延迟
 */
public final class MemoryScorer {

    private MemoryScorer() {}

    private static final String[] DEPTH_KEYWORDS = {"为什么", "如何", "原理", "区别", "对比", "分析", "设计", "架构"};
    private static final String[] MEDIUM_KEYWORDS = {"怎么", "如何", "方法", "实现"};
    private static final String[] SIGNAL_KEYWORDS = {"记住", "重要", "记下来", "别忘了", "注意"};

    public static double score(String summary, List<RagService.HistoryEntry> history,
                               List<MemoryStore.MemoryEntry> existingMemories) {
        double score = 0.5;

        // Factor 1: question depth from history
        score = applyQuestionDepth(score, history);

        // Factor 2: summary richness
        score = applyRichness(score, summary);

        // Factor 3: topic novelty
        score = applyNovelty(score, summary, existingMemories);

        // Factor 4: user explicit signal
        score = applyUserSignal(score, history);

        return Math.max(0.2, Math.min(1.0, score));
    }

    private static double applyQuestionDepth(double score, List<RagService.HistoryEntry> history) {
        // Look at the last few user messages for depth signals
        int checked = 0;
        for (int i = history.size() - 1; i >= 0 && checked < 4; i--) {
            RagService.HistoryEntry entry = history.get(i);
            if (!"user".equals(entry.role())) continue;
            checked++;
            String content = entry.content();
            for (String kw : DEPTH_KEYWORDS) {
                if (content.contains(kw)) return score + 0.3;
            }
            for (String kw : MEDIUM_KEYWORDS) {
                if (content.contains(kw)) return score + 0.15;
            }
        }
        return score;
    }

    private static double applyRichness(double score, String summary) {
        if (summary == null) return score;
        int len = summary.length();
        if (len >= 50 && len <= 300) return score + 0.15;
        if (len >= 20) return score + 0.08;
        return score;
    }

    private static double applyNovelty(double score, String summary, List<MemoryStore.MemoryEntry> existing) {
        if (existing == null || existing.isEmpty()) return score + 0.1;
        Set<String> summaryTokens = tokenize(summary);
        if (summaryTokens.isEmpty()) return score;

        double maxSimilarity = 0;
        for (MemoryStore.MemoryEntry mem : existing) {
            if (mem.summary() == null) continue;
            Set<String> memTokens = tokenize(mem.summary());
            if (memTokens.isEmpty()) continue;
            Set<String> intersection = new HashSet<>(summaryTokens);
            intersection.retainAll(memTokens);
            Set<String> union = new HashSet<>(summaryTokens);
            union.addAll(memTokens);
            double jaccard = (double) intersection.size() / union.size();
            maxSimilarity = Math.max(maxSimilarity, jaccard);
        }

        // Low similarity = high novelty = bonus
        double novelty = 1.0 - maxSimilarity;
        return score + novelty * 0.1;
    }

    private static double applyUserSignal(double score, List<RagService.HistoryEntry> history) {
        for (int i = history.size() - 1; i >= Math.max(0, history.size() - 3); i--) {
            RagService.HistoryEntry entry = history.get(i);
            if (!"user".equals(entry.role())) continue;
            for (String kw : SIGNAL_KEYWORDS) {
                if (entry.content().contains(kw)) return score + 0.2;
            }
        }
        return score;
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        // Simple bigram tokenization for Chinese + word splitting for English
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() >= 2) {
                for (int i = 0; i <= word.length() - 2; i++) {
                    tokens.add(word.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }
}
