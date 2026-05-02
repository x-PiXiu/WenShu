package com.example.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义分块器：在段落/句子边界处切分，不切断语义
 *
 * 核心改进：
 * 1. isHeading() 与 DocumentTypeDetector 对齐，避免误判
 * 2. 每个语义单元标记 ElementType，按元素类型自适应分块
 * 3. 代码块用小 chunk，表格不切，普通段落用配置的 chunkSize
 * 4. 利用 StructureProfile 动态调整参数
 */
public class SemanticSplitter implements DocumentSplitter {

    private final int maxChunkSize;
    private final int overlapSentences;
    private final DocumentTypeDetector.StructureProfile profile;

    public SemanticSplitter(int maxChunkSize, int overlapSentences) {
        this(maxChunkSize, overlapSentences, null);
    }

    public SemanticSplitter(int maxChunkSize, int overlapSentences,
                            DocumentTypeDetector.StructureProfile profile) {
        this.maxChunkSize = maxChunkSize;
        this.overlapSentences = overlapSentences;
        this.profile = profile;
    }

    // ==================== 元素类型 ====================

    public enum ElementType {
        CODE_BLOCK,    // 缩进代码或围栏代码
        TABLE,         // 表格行（tab/| 分隔）
        LIST,          // 列表项集合
        PARAGRAPH      // 普通段落
    }

    // ==================== 主流程 ====================

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        Metadata metadata = document.metadata();

        // 1. 按语义边界识别单元，标记元素类型
        List<SemanticUnit> units = identifySemanticUnits(text);

        // 2. 合并小单元（同类型优先合并）
        List<SemanticUnit> merged = mergeUnits(units);

        // 3. 按元素类型自适应细分过大单元
        List<SemanticUnit> refined = refineLargeUnits(merged);

        // 4. 转为 TextSegment
        List<TextSegment> segments = new ArrayList<>();
        for (SemanticUnit unit : refined) {
            Metadata segMeta = new Metadata();
            if (metadata.getString("source") != null) {
                segMeta.put("source", metadata.getString("source"));
            }
            if (unit.heading != null && !unit.heading.isEmpty()) {
                segMeta.put("heading", unit.heading);
            }
            if (unit.breadcrumb != null && !unit.breadcrumb.isEmpty()) {
                segMeta.put("breadcrumb", unit.breadcrumb);
            }
            segMeta.put("elementType", unit.type.name());
            // Code language detection
            if (unit.type == ElementType.CODE_BLOCK) {
                String lang = detectCodeLanguage(unit.text);
                if (lang != null) segMeta.put("codeLanguage", lang);
            }
            segments.add(new TextSegment(unit.text, segMeta));
        }

        // 4.1 Enrich hierarchical metadata
        enrichHierarchyMetadata(segments);

        // 5. 应用句子级重叠
        if (overlapSentences > 0 && segments.size() > 1) {
            segments = applyOverlap(segments);
        }

        return segments;
    }

    // ==================== 语义单元识别 ====================

    private List<SemanticUnit> identifySemanticUnits(String text) {
        List<SemanticUnit> units = new ArrayList<>();
        String[] lines = text.split("\n");
        List<String> currentLines = new ArrayList<>();
        String currentHeading = "";
        String currentBreadcrumb = "";

        // Track heading hierarchy by level (h1, h2, h3, etc.)
        String[] headingStack = new String[6];
        int[] headingLevels = new int[6];
        int stackTop = -1;

        for (String line : lines) {
            if (isHeading(line)) {
                if (!currentLines.isEmpty()) {
                    String content = String.join("\n", currentLines).trim();
                    if (!content.isEmpty()) {
                        ElementType type = classifyLines(currentLines);
                        units.add(new SemanticUnit(currentHeading, content, type, currentBreadcrumb));
                    }
                }
                String trimmed = line.trim();
                int level = headingLevel(trimmed);

                // Pop stack entries with >= level
                while (stackTop >= 0 && headingLevels[stackTop] >= level) {
                    stackTop--;
                }
                // Push new heading
                stackTop++;
                headingStack[stackTop] = stripHeadingMarker(trimmed);
                headingLevels[stackTop] = level;

                currentHeading = trimmed;
                currentBreadcrumb = buildBreadcrumb(headingStack, stackTop);
                currentLines = new ArrayList<>();
            } else {
                currentLines.add(line);
            }
        }

        if (!currentLines.isEmpty()) {
            String content = String.join("\n", currentLines).trim();
            if (!content.isEmpty()) {
                ElementType type = classifyLines(currentLines);
                units.add(new SemanticUnit(currentHeading, content, type, currentBreadcrumb));
            }
        }

        return units;
    }

    private int headingLevel(String heading) {
        if (heading.startsWith("### ")) return 3;
        if (heading.startsWith("## ")) return 2;
        if (heading.startsWith("# ")) return 1;
        // Numbered: 1. → 1, 1.1 → 2, 1.1.1 → 3, etc.
        String trimmed = heading.replaceAll("^\\s+", "");
        var m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)*)").matcher(trimmed);
        if (m.find()) {
            return m.group(1).split("\\.").length;
        }
        // Chinese numbered: 一、 → 1
        if (trimmed.matches("^[一二三四五六七八九十百]+[、．.].*")) return 1;
        return 1;
    }

    private String stripHeadingMarker(String heading) {
        if (heading.startsWith("### ")) return heading.substring(4);
        if (heading.startsWith("## ")) return heading.substring(3);
        if (heading.startsWith("# ")) return heading.substring(2);
        return heading.replaceAll("^\\d+(?:\\.\\d+)*\\.?\\s+", "")
                      .replaceAll("^[一二三四五六七八九十百]+[、．.]\\s*", "");
    }

    private String buildBreadcrumb(String[] stack, int top) {
        if (top < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= top; i++) {
            if (stack[i] != null && !stack[i].isEmpty()) {
                if (sb.length() > 0) sb.append(" > ");
                sb.append(stack[i]);
            }
        }
        return sb.toString();
    }

    /**
     * 判断是否为标题行（与 DocumentTypeDetector.isHeadingLine 对齐）
     */
    private boolean isHeading(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return false;

        // Markdown 标题：## 及以上直接识别，单 # 要求长度 < 50
        if (trimmed.startsWith("### ")) return true;
        if (trimmed.startsWith("## ")) return true;
        if (trimmed.startsWith("# ") && trimmed.length() < 50) return true;

        // 多级数字编号标题：1.1、2.1.3 等（至少两级编号）
        if (trimmed.matches("^\\d+\\.\\d+(\\.\\d+)*\\.?\\s+[\\u4e00-\\u9fa5a-zA-Z].*")
                && trimmed.length() < 80) {
            return true;
        }

        // 中文编号标题：一、二、三、
        if (trimmed.matches("^[一二三四五六七八九十百]+[、．.].*")
                && trimmed.length() < 60) {
            return true;
        }

        return false;
    }

    /**
     * 根据行内容判断语义单元的元素类型
     */
    private ElementType classifyLines(List<String> lines) {
        int codeLines = 0;
        int tableLines = 0;
        int listLines = 0;
        int totalNonEmpty = 0;
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            totalNonEmpty++;

            // 围栏代码块
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                codeLines++;
                continue;
            }
            if (inCodeBlock) {
                codeLines++;
                continue;
            }

            // 缩进代码行
            if ((line.startsWith("    ") || line.startsWith("\t"))
                    && !trimmed.matches("^[-*+\\d].*")) {
                codeLines++;
                continue;
            }

            // 技术关键词行
            if (isTechKeywordLine(trimmed)) {
                codeLines++;
                continue;
            }

            // 表格行（tab 分隔或 | 分隔）
            if (isTableLine(trimmed)) {
                tableLines++;
                continue;
            }

            // 列表行
            if (isListLine(trimmed)) {
                listLines++;
                continue;
            }
        }

        if (totalNonEmpty == 0) return ElementType.PARAGRAPH;

        double codeRatio = (double) codeLines / totalNonEmpty;
        double tableRatio = (double) tableLines / totalNonEmpty;
        double listRatio = (double) listLines / totalNonEmpty;

        // 优先级：代码 > 表格 > 列表 > 段落
        if (codeRatio > 0.4) return ElementType.CODE_BLOCK;
        if (tableRatio > 0.5) return ElementType.TABLE;
        if (listRatio > 0.5) return ElementType.LIST;

        return ElementType.PARAGRAPH;
    }

    // ==================== 合并小单元 ====================

    private List<SemanticUnit> mergeUnits(List<SemanticUnit> units) {
        if (units.isEmpty()) return units;

        List<SemanticUnit> result = new ArrayList<>();
        StringBuilder merged = new StringBuilder();
        String mergedHeading = "";
        String mergedBreadcrumb = "";
        ElementType mergedType = ElementType.PARAGRAPH;

        for (SemanticUnit unit : units) {
            int targetSize = getTargetChunkSize(mergedType);

            if (merged.isEmpty()) {
                mergedHeading = unit.heading;
                mergedBreadcrumb = unit.breadcrumb;
                mergedType = unit.type;
                merged.append(unit.text);
            } else if (unit.type == mergedType
                    && merged.length() + unit.text.length() + 2 <= targetSize) {
                // 同类型 + 不超长 → 合并
                merged.append("\n\n").append(unit.text);
            } else {
                // 类型不同或超长 → 保存当前，开始新单元
                result.add(new SemanticUnit(mergedHeading, merged.toString().trim(), mergedType, mergedBreadcrumb));
                mergedHeading = unit.heading;
                mergedBreadcrumb = unit.breadcrumb;
                mergedType = unit.type;
                merged = new StringBuilder(unit.text);
            }
        }

        if (!merged.isEmpty()) {
            result.add(new SemanticUnit(mergedHeading, merged.toString().trim(), mergedType, mergedBreadcrumb));
        }

        return result;
    }

    // ==================== 细分过大单元 ====================

    private List<SemanticUnit> refineLargeUnits(List<SemanticUnit> units) {
        List<SemanticUnit> result = new ArrayList<>();

        for (SemanticUnit unit : units) {
            int targetSize = getTargetChunkSize(unit.type);

            if (unit.text.length() <= targetSize) {
                result.add(unit);
            } else if (unit.type == ElementType.TABLE) {
                result.add(unit);
            } else if (unit.type == ElementType.CODE_BLOCK) {
                result.addAll(refineCodeBlock(unit, targetSize));
            } else {
                result.addAll(refineBySentences(unit, targetSize));
            }
        }

        return result;
    }

    /**
     * 代码块自适应细分：
     * - 未超过 2x targetSize → 保持完整，不切断围栏代码块
     * - 超过 2x → 按围栏边界或空行拆分，保证每个围栏代码块完整
     */
    private List<SemanticUnit> refineCodeBlock(SemanticUnit unit, int targetSize) {
        int maxIntactSize = targetSize * 2;

        if (unit.text.length() <= maxIntactSize) {
            return List.of(unit);
        }

        List<String> parts = splitCodeAtFenceOrBlankLine(unit.text);
        List<SemanticUnit> result = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();

        for (String part : parts) {
            if (chunk.length() > 0 && chunk.length() + part.length() > maxIntactSize) {
                result.add(new SemanticUnit(unit.heading, chunk.toString().trim(), unit.type, unit.breadcrumb));
                chunk = new StringBuilder();
            }
            if (chunk.length() > 0) chunk.append("\n\n");
            chunk.append(part);
        }
        if (chunk.length() > 0) {
            result.add(new SemanticUnit(unit.heading, chunk.toString().trim(), unit.type, unit.breadcrumb));
        }

        return result.isEmpty() ? List.of(unit) : result;
    }

    /**
     * 按围栏代码块边界和空行拆分代码内容，确保每个围栏块完整
     */
    private List<String> splitCodeAtFenceOrBlankLine(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;

        for (String line : text.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inFence) {
                    current.append(line).append("\n");
                    String block = current.toString().trim();
                    if (!block.isEmpty()) parts.add(block);
                    current = new StringBuilder();
                    inFence = false;
                } else {
                    String before = current.toString().trim();
                    if (!before.isEmpty()) parts.add(before);
                    current = new StringBuilder();
                    current.append(line).append("\n");
                    inFence = true;
                }
            } else if (inFence) {
                current.append(line).append("\n");
            } else if (trimmed.isEmpty() && current.length() > 0) {
                String block = current.toString().trim();
                if (!block.isEmpty()) parts.add(block);
                current = new StringBuilder();
            } else {
                current.append(line).append("\n");
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) parts.add(remaining);

        return parts;
    }

    /**
     * 按句子边界拆分普通文本（PARAGRAPH / LIST）
     */
    private List<SemanticUnit> refineBySentences(SemanticUnit unit, int targetSize) {
        List<SemanticUnit> result = new ArrayList<>();
        List<String> sentences = splitIntoSentences(unit.text);
        StringBuilder chunk = new StringBuilder();

        for (String sentence : sentences) {
            if (chunk.length() + sentence.length() > targetSize && chunk.length() > 0) {
                result.add(new SemanticUnit(unit.heading, chunk.toString().trim(), unit.type, unit.breadcrumb));
                chunk = new StringBuilder();
            }
            chunk.append(sentence);
        }
        if (!chunk.isEmpty()) {
            result.add(new SemanticUnit(unit.heading, chunk.toString().trim(), unit.type, unit.breadcrumb));
        }

        return result;
    }

    /**
     * 根据元素类型和 StructureProfile 动态计算目标 chunk 大小
     */
    private int getTargetChunkSize(ElementType type) {
        switch (type) {
            case CODE_BLOCK:
                // 代码块完整性优先：给足空间保持围栏代码块不被切断
                if (profile != null && profile.codeRatio() > 0.2) {
                    return (int) (maxChunkSize * 0.8);
                }
                return maxChunkSize;

            case TABLE:
                // 表格不切
                return Integer.MAX_VALUE;

            case LIST:
                // 列表用中等 chunk
                return maxChunkSize;

            case PARAGRAPH:
            default:
                // 长段落型文档：大 chunk
                if (profile != null && profile.longParaRatio() > 0.3) {
                    return (int) (maxChunkSize * 1.2);  // 长段落多，大 chunk
                }
                return maxChunkSize;
        }
    }

    // ==================== 行类型判断 ====================

    private static boolean isTableLine(String line) {
        // Tab 分隔的表格
        if (line.contains("\t") && line.split("\t").length >= 2) return true;
        // Markdown 表格（| 分隔）
        if (line.startsWith("|") && line.endsWith("|")) return true;
        return false;
    }

    private static boolean isListLine(String line) {
        return line.matches("^[-*+]\\s+.*")
                || line.matches("^\\d+[).]\\s+.*");
    }

    private static boolean isTechKeywordLine(String line) {
        return line.matches("^(import |from \\w+ import |package |require\\(|#include\\s|using\\s).*")
                || line.matches("^(public |private |protected |static |final |abstract |class |interface |enum |void |return |if \\(|for \\(|while \\(|switch \\(|try \\{|catch \\().+")
                || line.matches("^(def |async def |class |print\\(|console\\.log|System\\.out|logger\\.|fmt\\.).*")
                || line.matches("^(func |let |var |const |type |struct |interface |map\\[|func\\(|go ).*")
                || line.matches("^\\}.*$")
                || line.matches(".*\\{$")
                || line.matches("^(npm |pip |cargo |go |mvn |gradle |docker |kubectl |git ).*")
                || line.matches("^\\$\\s+.*")
                || line.matches("^>\\s+.*");
    }

    // ==================== 句子分割 ====================

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);

            boolean isSentenceEnd = (c == '。' || c == '！' || c == '？'
                    || c == '.' || c == '!' || c == '?')
                    && (i + 1 >= text.length()
                    || Character.isWhitespace(text.charAt(i + 1))
                    || text.charAt(i + 1) == '\n'
                    || text.charAt(i + 1) == '\r');

            if (isSentenceEnd || c == '\n') {
                String s = current.toString().trim();
                if (!s.isEmpty()) {
                    sentences.add(s);
                }
                current = new StringBuilder();
            }
        }

        if (!current.isEmpty()) {
            String s = current.toString().trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }

        return sentences;
    }

    // ==================== 层级元数据增强 ====================

    private void enrichHierarchyMetadata(List<TextSegment> segments) {
        // Build breadcrumb -> count mapping
        java.util.Map<String, Integer> breadcrumbCount = new java.util.LinkedHashMap<>();
        for (TextSegment seg : segments) {
            String bc = seg.metadata().getString("breadcrumb");
            if (bc != null && !bc.isEmpty()) {
                breadcrumbCount.merge(bc, 1, Integer::sum);
            }
        }

        // Build parent breadcrumb -> child count
        java.util.Map<String, Integer> parentChildCount = new java.util.LinkedHashMap<>();
        for (String bc : breadcrumbCount.keySet()) {
            String parent = parentBreadcrumb(bc);
            if (parent != null) {
                parentChildCount.merge(parent, breadcrumbCount.get(bc), Integer::sum);
            }
        }

        // Enrich each segment
        for (TextSegment seg : segments) {
            String bc = seg.metadata().getString("breadcrumb");
            if (bc != null && !bc.isEmpty()) {
                int depth = countDepth(bc);
                seg.metadata().put("depth", depth);
                String parent = parentBreadcrumb(bc);
                if (parent != null) {
                    seg.metadata().put("parentBreadcrumb", parent);
                }
                Integer childCount = parentChildCount.get(bc);
                if (childCount != null) {
                    seg.metadata().put("childCount", childCount);
                }
            }
        }
    }

    private static String parentBreadcrumb(String breadcrumb) {
        int lastSep = breadcrumb.lastIndexOf(" > ");
        if (lastSep <= 0) return null;
        return breadcrumb.substring(0, lastSep);
    }

    private static int countDepth(String breadcrumb) {
        int depth = 1;
        int idx = 0;
        while ((idx = breadcrumb.indexOf(" > ", idx)) != -1) {
            depth++;
            idx += 3;
        }
        return depth;
    }

    private static String detectCodeLanguage(String text) {
        if (text == null || text.isEmpty()) return null;
        String firstLine = text.split("\n", 2)[0].trim();
        if (firstLine.startsWith("```")) {
            String lang = firstLine.substring(3).trim();
            return lang.isEmpty() ? null : lang;
        }
        return null;
    }

    // ==================== 句子级重叠 ====================

    private List<TextSegment> applyOverlap(List<TextSegment> segments) {
        List<TextSegment> result = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            StringBuilder enriched = new StringBuilder();

            if (i > 0) {
                String prevContext = extractLastSentences(segments.get(i - 1).text(), overlapSentences);
                if (!prevContext.isEmpty()) {
                    enriched.append(prevContext).append("\n");
                }
            }

            enriched.append(segments.get(i).text());

            if (i < segments.size() - 1) {
                String nextContext = extractFirstSentences(segments.get(i + 1).text(), overlapSentences);
                if (!nextContext.isEmpty()) {
                    enriched.append("\n").append(nextContext);
                }
            }

            result.add(new TextSegment(enriched.toString(), segments.get(i).metadata()));
        }

        return result;
    }

    private String extractLastSentences(String text, int count) {
        List<String> sentences = splitIntoSentences(text);
        if (sentences.size() <= count) return text;
        int start = sentences.size() - count;
        return String.join("", sentences.subList(start, sentences.size()));
    }

    private String extractFirstSentences(String text, int count) {
        List<String> sentences = splitIntoSentences(text);
        if (sentences.size() <= count) return text;
        return String.join("", sentences.subList(0, Math.min(count, sentences.size())));
    }

    // ==================== 数据结构 ====================

    private static class SemanticUnit {
        final String heading;
        final String text;
        final ElementType type;
        final String breadcrumb;

        SemanticUnit(String heading, String text, ElementType type, String breadcrumb) {
            this.heading = heading;
            this.text = text;
            this.type = type;
            this.breadcrumb = breadcrumb != null ? breadcrumb : "";
        }

        SemanticUnit(String heading, String text, ElementType type) {
            this(heading, text, type, "");
        }

        int length() {
            return text.length();
        }
    }
}
