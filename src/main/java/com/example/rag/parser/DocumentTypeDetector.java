package com.example.rag.parser;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 基于内容结构分析自动检测文档类型
 *
 * 核心思路：不依赖文件名猜测，而是分析文本本身的结构特征比例
 * - 代码密度：代码相关内容占总文本的比例
 * - 问答密度：Q&A 格式的占比
 * - 日志密度：时间戳+日志级别的占比
 * - 段落结构：长段落 vs 短行的分布
 */
public class DocumentTypeDetector {

    public static final String AUTO_DETECT = "AUTO";

    public record DetectionResult(String type, double confidence, String method) {}

    /**
     * 检测结果 + 结构分析详情
     */
    public record DetailedDetection(DetectionResult result, StructureProfile profile) {}

    /**
     * 自动检测文档类型
     */
    public static DetectionResult detect(String filename, String content) {
        return detectDetailed(filename, content).result();
    }

    /**
     * 自动检测文档类型，同时返回结构分析详情
     */
    public static DetailedDetection detectDetailed(String filename, String content) {
        if (content == null || content.isBlank()) {
            StructureProfile empty = new StructureProfile(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            return new DetailedDetection(new DetectionResult("GENERAL", 0.3, "empty-content"), empty);
        }

        String preview = content.length() > 10000 ? content.substring(0, 10000) : content;
        String[] lines = preview.split("\n");
        int totalLines = lines.length;

        // === 结构分析 ===
        StructureProfile profile = analyzeStructure(preview, lines);

        // === 决策树：基于结构比例判断 ===
        DetectionResult result = decideByStructure(profile, totalLines, preview.length());
        return new DetailedDetection(result, profile);
    }

    // ==================== 结构分析 ====================

    private static StructureProfile analyzeStructure(String text, String[] lines) {
        int totalLines = lines.length;
        int codeLines = 0;
        int logLines = 0;
        int faqLines = 0;
        int headingLines = 0;
        int listLines = 0;
        int shortLines = 0;       // < 30 字符的非空行
        int longParagraphs = 0;   // > 100 字符的行
        int emptyLines = 0;
        int techKeywordLines = 0;

        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                emptyLines++;
                continue;
            }

            // 代码块追踪
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                codeLines++;
                continue;
            }
            if (inCodeBlock) {
                codeLines++;
                continue;
            }

            // 缩进代码行（4空格或tab开头，且不是列表）——必须用原始行检查缩进
            if ((line.startsWith("    ") || line.startsWith("\t"))
                    && !trimmed.matches("^[-*+\\d].*")) {
                codeLines++;
                continue;
            }

            // 日志行：时间戳 + 日志级别组合
            if (isLogLine(trimmed)) {
                logLines++;
                continue;
            }

            // FAQ 行
            if (isFaqLine(trimmed)) {
                faqLines++;
                continue;
            }

            // 标题行
            if (isHeadingLine(trimmed)) {
                headingLines++;
                continue;
            }

            // 列表行
            if (isListLine(trimmed)) {
                listLines++;
                continue;
            }

            // 技术关键词行（即使没有代码格式，DOCX 解析后的代码也会保留关键字）
            if (isTechKeywordLine(trimmed)) {
                techKeywordLines++;
                // 含技术关键字的行也计入代码行（因为它们本质上是代码片段）
                codeLines++;
                continue;
            }

            // 长度分类
            if (trimmed.length() < 30) {
                shortLines++;
            } else if (trimmed.length() > 100) {
                longParagraphs++;
            }
        }

        int nonEmptyLines = totalLines - emptyLines;

        return new StructureProfile(
            totalLines, nonEmptyLines, emptyLines,
            codeLines, logLines, faqLines,
            headingLines, listLines,
            shortLines, longParagraphs,
            techKeywordLines
        );
    }

    // ==================== 决策树 ====================

    private static DetectionResult decideByStructure(StructureProfile p, int totalLines, int totalChars) {
        // 有效行数太少，无法判断
        if (p.nonEmptyLines() < 3) {
            return new DetectionResult("GENERAL", 0.3, "too-short");
        }

        // --- 1. LOG 检测：时间戳 + 日志级别是极强信号 ---
        double logRatio = (double) p.logLines() / p.nonEmptyLines();
        if (logRatio > 0.3) {
            return new DetectionResult("LOG", Math.min(0.95, 0.5 + logRatio), "structure");
        }

        // --- 2. FAQ 检测：Q&A 问答标记 ---
        double faqRatio = (double) p.faqLines() / p.nonEmptyLines();
        if (faqRatio > 0.15) {
            return new DetectionResult("FAQ", Math.min(0.95, 0.5 + faqRatio * 2), "structure");
        }

        // --- 3. TECHNICAL vs ARTICLE 判定 ---
        // 核心指标：代码行占比
        double codeRatio = (double) p.codeLines() / p.nonEmptyLines();

        if (codeRatio > 0.2) {
            // 代码行 > 20% → 明确的技术文档
            double confidence = Math.min(0.95, 0.6 + codeRatio);
            return new DetectionResult("TECHNICAL", confidence, "structure");
        }

        if (codeRatio > 0.05) {
            // 代码行 5%-20% → 有代码内容，需要进一步区分
            // 看标题和列表的密度：有结构化标题 → 技术教程；没有 → 偏通用
            double headingRatio = (double) p.headingLines() / p.nonEmptyLines();
            if (headingRatio > 0.05 || p.listLines() >= 3) {
                return new DetectionResult("TECHNICAL", 0.65, "structure");
            }
            // 有一些代码但结构松散，保守归为 TECHNICAL
            return new DetectionResult("TECHNICAL", 0.55, "structure");
        }

        // --- 4. 纯文本分类 ---
        // 没有代码 → ARTICLE 或 GENERAL
        double headingRatio = (double) p.headingLines() / p.nonEmptyLines();
        double longParaRatio = (double) p.longParagraphs() / p.nonEmptyLines();

        if (headingRatio > 0.1 && totalChars > 300 && p.nonEmptyLines() >= 6) {
            // 明确的标题结构 + 一定篇幅 → ARTICLE
            double confidence = Math.min(0.85, 0.55 + headingRatio);
            return new DetectionResult("ARTICLE", confidence, "structure");
        }

        if (headingRatio > 0.03 && totalChars > 800) {
            // 有标题 + 一定篇幅 → ARTICLE
            return new DetectionResult("ARTICLE", 0.6, "structure");
        }

        if (totalChars > 1000 && longParaRatio > 0.2) {
            // 长段落为主 → ARTICLE
            return new DetectionResult("ARTICLE", 0.5, "structure");
        }

        // --- 5. 默认 ---
        return new DetectionResult("GENERAL", 0.4, "structure");
    }

    // ==================== 行类型判断 ====================

    private static boolean isLogLine(String line) {
        // 时间戳模式
        boolean hasTimestamp = line.matches(".*\\d{4}[-/]\\d{2}[-/]\\d{2}[T ]\\d{2}:\\d{2}.*");
        // 日志级别
        boolean hasLogLevel = line.matches(".*(ERROR|WARN|WARNING|INFO|DEBUG|TRACE|FATAL|SEVERE)\\s*[:\\]].*");
        // 行首就是时间戳或日志级别
        boolean startsWithLog = line.matches("^(\\d{4}[-/]\\d{2}[-/]\\d{2}|\\[?ERROR|\\[?WARN|\\[?INFO|\\[?DEBUG|\\[?TRACE|\\[?FATAL).*");

        // 时间戳 + 日志级别 = 极强信号
        if (hasTimestamp && hasLogLevel) return true;
        // 行首是时间戳
        if (startsWithLog && hasTimestamp) return true;
        // 连续多行都有日志级别标记
        if (hasLogLevel && line.length() < 200) return true;

        return false;
    }

    private static boolean isFaqLine(String line) {
        return line.matches("(?i)^(Q[:：]|A[:：]|问题[:：]|答案[:：]|问[:：]|答[:：]).*")
            || line.matches("^\\d+\\.\\s*(问题|提问|疑问|Q\\s*[:：]).*")
            || line.endsWith("？") && line.length() < 80;
    }

    private static boolean isHeadingLine(String line) {
        // Markdown 标题：要求 ## 或更多（# 在非代码上下文中可能是 shell 注释）
        // 单 # 可能是 shell 注释，但也可能是标题——通过长度区分
        if (line.startsWith("### ")) return true;
        if (line.startsWith("## ")) return true;
        if (line.startsWith("# ") && line.length() < 50) return true;

        // 多级数字编号标题：1.1、2.1.3 等（至少两级编号）
        if (line.matches("^\\d+\\.\\d+(\\.\\d+)*\\.?\\s+[\\u4e00-\\u9fa5a-zA-Z].*") && line.length() < 80) {
            return true;
        }

        // 中文编号标题：一、二、三、
        if (line.matches("^[一二三四五六七八九十百]+[、．.].*") && line.length() < 60) {
            return true;
        }

        return false;
    }

    private static boolean isListLine(String line) {
        return line.matches("^[-*+]\\s+.*")
            || line.matches("^\\d+[).]\\s+.*");
    }

    private static boolean isTechKeywordLine(String line) {
        // 编程语言关键字（出现在行首或独立存在）
        // 只匹配高置信度的模式，避免误判
        return line.matches("^(import |from \\w+ import |package |require\\(|#include\\s|using\\s).*")
            || line.matches("^(public |private |protected |static |final |abstract |class |interface |enum |void |return |if \\(|for \\(|while \\(|switch \\(|try \\{|catch \\().+")
            || line.matches("^(def |async def |class |print\\(|console\\.log|System\\.out|logger\\.|fmt\\.).*")
            || line.matches("^(func |let |var |const |type |struct |interface |map\\[|func\\(|go ).*")
            || line.matches("^\\}.*$")
            || line.matches(".*\\{$")
            || line.matches("^(npm |pip |cargo |go |mvn |gradle |docker |kubectl |git ).*")
            || line.matches("^\\$\\s+.*")  // shell 命令
            || line.matches("^>\\s+.*")     // 引用命令输出
            ;
    }

    // ==================== 数据结构 ====================

    public record StructureProfile(
        int totalLines,
        int nonEmptyLines,
        int emptyLines,
        int codeLines,
        int logLines,
        int faqLines,
        int headingLines,
        int listLines,
        int shortLines,
        int longParagraphs,
        int techKeywordLines
    ) {
        public double codeRatio() {
            return nonEmptyLines > 0 ? (double) codeLines / nonEmptyLines : 0;
        }
        public double headingRatio() {
            return nonEmptyLines > 0 ? (double) headingLines / nonEmptyLines : 0;
        }
        public double longParaRatio() {
            return nonEmptyLines > 0 ? (double) longParagraphs / nonEmptyLines : 0;
        }
    }
}
