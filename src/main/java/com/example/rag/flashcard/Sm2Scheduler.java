package com.example.rag.flashcard;

/**
 * 简化版 SM-2 间隔重复算法
 * 记住 → 间隔 × 2.5
 * 模糊 → 间隔 × 1.2（缓慢增长）
 * 忘了 → 重置为 1 天
 *
 * 进度分段：新卡(0) → 学习中(1~6天) → 熟悉(7~13天) → 掌握(14天+)
 */
public class Sm2Scheduler {

    private static final double EASE_FACTOR = 2.5;
    private static final double FUZZY_FACTOR = 1.2;
    private static final double MASTERY_THRESHOLD = 14.0;

    public record ScheduleResult(double intervalDays, long nextReviewAt) {}

    public static ScheduleResult nextInterval(double currentInterval, String grade) {
        double next = switch (grade) {
            case "remembered" -> currentInterval * EASE_FACTOR;
            case "fuzzy" -> currentInterval * FUZZY_FACTOR;
            case "forgot" -> 1.0;
            default -> currentInterval;
        };
        long nextAt = System.currentTimeMillis() + (long) (next * 86_400_000);
        return new ScheduleResult(next, nextAt);
    }

    public static boolean isMastered(double intervalDays) {
        return intervalDays >= MASTERY_THRESHOLD;
    }
}
