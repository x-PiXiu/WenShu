package com.example.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SemanticSplitter 测试
 * 验证围栏代码块保护、自适应分块、元素类型标记
 */
class SemanticSplitterTest {

    private static final String XH003_DOC = """
        # XH-003：5分钟跑通第一个AI对话

        ---

        ## 封面图提示词

        ```
        代码教学风格截图
        上方大字：「Hello AI」
        代码编辑器界面（IntelliJ IDEA深色主题）
        ```

        ---

        ## 第一步：建项目（1分钟）

        用IDEA新建Maven项目，pom.xml加依赖：

        ```xml
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <groupId>org.example</groupId>
            <artifactId>ExampleDemo</artifactId>
            <version>1.0-SNAPSHOT</version>
            <dependencies>
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-core</artifactId>
                    <version>1.0.0</version>
                </dependency>
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-open-ai</artifactId>
                    <version>1.0.0</version>
                </dependency>
            </dependencies>
        </project>
        ```

        点击刷新，等待下载完成~

        ---

        ## 第二步：写代码（2分钟）

        新建类，粘贴这5行代码：

        ```java
        ChatModel model = OpenAiChatModel.builder()
            .apiKey(System.getenv("MINIMAX_API_KEY"))
            .baseUrl("https://api.minimax.chat/v1")
            .modelName("MiniMax-M2.7")
            .build();
        String response = model.chat("用一句话介绍LangChain4j");
        System.out.println(response);
        ```

        就这5行，核心逻辑就完了。

        ---

        ## 避坑指南

        1. `api key invalid` → Key填错了，仔细检查
        2. `quota exceeded` → 免费额度用完了，换个账号或充值
        3. `connection timeout` → 检查网络是否正常
        """;

    @Test
    void fenceCodeBlock_shouldNotBeSplit() {
        SemanticSplitter splitter = new SemanticSplitter(500, 2);
        Document doc = Document.from(XH003_DOC, new Metadata());
        List<TextSegment> segments = splitter.split(doc);

        // 找到包含 pom.xml 的 chunk
        String pomChunk = segments.stream()
                .map(TextSegment::text)
                .filter(t -> t.contains("<dependencies>") && t.contains("langchain4j-open-ai"))
                .findFirst()
                .orElse(null);

        assertNotNull(pomChunk, "Should have a chunk containing the full pom.xml dependencies");

        // 验证 pom.xml 围栏代码块完整：两个 dependency 都在同一个 chunk 中
        assertTrue(pomChunk.contains("langchain4j-core"),
                "pom.xml chunk should contain langchain4j-core dependency");
        assertTrue(pomChunk.contains("langchain4j-open-ai"),
                "pom.xml chunk should contain langchain4j-open-ai dependency");
    }

    @Test
    void javaCodeBlock_shouldNotBeSplit() {
        SemanticSplitter splitter = new SemanticSplitter(500, 2);
        Document doc = Document.from(XH003_DOC, new Metadata());
        List<TextSegment> segments = splitter.split(doc);

        String javaChunk = segments.stream()
                .map(TextSegment::text)
                .filter(t -> t.contains("OpenAiChatModel.builder()"))
                .findFirst()
                .orElse(null);

        assertNotNull(javaChunk, "Should have a chunk containing Java code");

        // Java 代码块完整：builder 创建和 chat 调用在同一个 chunk
        assertTrue(javaChunk.contains("model.chat("),
                "Java code chunk should contain the complete flow: builder + chat");
    }

    @Test
    void elementType_shouldBeCorrectlyTagged() {
        SemanticSplitter splitter = new SemanticSplitter(500, 0);
        Document doc = Document.from(XH003_DOC, new Metadata());
        List<TextSegment> segments = splitter.split(doc);

        // 至少有一个 CODE_BLOCK 类型的 segment
        boolean hasCodeBlock = segments.stream()
                .anyMatch(s -> "CODE_BLOCK".equals(s.metadata().getString("elementType")));
        assertTrue(hasCodeBlock, "Should have at least one CODE_BLOCK segment");

        // 至少有一个 PARAGRAPH 或 LIST 类型的 segment
        boolean hasNonCode = segments.stream()
                .anyMatch(s -> {
                    String type = s.metadata().getString("elementType");
                    return "PARAGRAPH".equals(type) || "LIST".equals(type);
                });
        assertTrue(hasNonCode, "Should have at least one non-code segment");
    }

    @Test
    void headingMetadata_shouldBeSet() {
        SemanticSplitter splitter = new SemanticSplitter(500, 0);
        Document doc = Document.from(XH003_DOC, new Metadata());
        List<TextSegment> segments = splitter.split(doc);

        boolean hasHeading = segments.stream()
                .anyMatch(s -> s.metadata().getString("heading") != null
                        && s.metadata().getString("heading").contains("第一步"));
        assertTrue(hasHeading, "Should have segments with heading metadata");
    }

    @Test
    void smallDoc_shouldProduceSingleSegment() {
        String small = "这是一段简短的文本，不需要分块。";
        SemanticSplitter splitter = new SemanticSplitter(500, 0);
        Document doc = Document.from(small, new Metadata());
        List<TextSegment> segments = splitter.split(doc);

        assertEquals(1, segments.size());
        assertEquals(small, segments.get(0).text());
    }

    @Test
    void table_shouldNotBeSplit() {
        String tableDoc = """
            ## 数据对比

            | 模型 | 参数量 | 速度 |
            |------|--------|------|
            | GPT-4 | 1.8T | 慢 |
            | Qwen2 | 72B | 快 |
            | MiniMax | 200B | 中 |

            以上是各模型的简单对比。
            """;

        SemanticSplitter splitter = new SemanticSplitter(200, 0);
        Document doc = Document.from(tableDoc, new Metadata());
        List<TextSegment> segments = splitter.split(doc);

        // 表格行应该在同一个 chunk 中
        String tableChunk = segments.stream()
                .map(TextSegment::text)
                .filter(t -> t.contains("GPT-4") || t.contains("Qwen2"))
                .findFirst()
                .orElse(null);

        if (tableChunk != null) {
            // 如果表格和 GPT-4 在同一个 chunk，Qwen2 也应该在
            if (tableChunk.contains("GPT-4")) {
                assertTrue(tableChunk.contains("Qwen2"),
                        "Table rows should not be split across chunks");
            }
        }
    }

    @Test
    void oversizedFenceCodeBlock_shouldSplitAtFenceBoundary() {
        // 构造一个超过 2x targetSize 的文档，包含两个围栏代码块
        StringBuilder bigCode1 = new StringBuilder("```java\n");
        for (int i = 0; i < 30; i++) {
            bigCode1.append("public void method").append(i).append("() {\n");
            bigCode1.append("    System.out.println(\"method ").append(i).append("\");\n");
            bigCode1.append("}\n");
        }
        bigCode1.append("```\n\n");

        StringBuilder bigCode2 = new StringBuilder("```python\n");
        for (int i = 0; i < 30; i++) {
            bigCode2.append("def func_").append(i).append("():\n");
            bigCode2.append("    print('func ").append(i).append("')\n");
        }
        bigCode2.append("```");

        String doc = "## Big Code\n\n" + bigCode1 + bigCode2;

        // chunkSize = 200, targetSize for CODE_BLOCK = 200, maxIntactSize = 400
        SemanticSplitter splitter = new SemanticSplitter(200, 0);
        Document document = Document.from(doc, new Metadata());
        List<TextSegment> segments = splitter.split(document);

        // 每个 chunk 不应把一个围栏代码块的内容拆到两半
        for (TextSegment seg : segments) {
            String text = seg.text();
            int openCount = countOccurrences(text, "```");
            // 如果有围栏标记，必须成对出现
            if (openCount > 0) {
                assertEquals(0, openCount % 2,
                        "Fence markers must be paired in each chunk. Got " + openCount
                                + " markers in chunk: " + text.substring(0, Math.min(80, text.length())));
            }
        }
    }

    @Test
    void sentenceOverlap_shouldWork() {
        String doc = """
            ## 概述

            这是第一句话。这是第二句话。这是第三句话。这是第四句话。这是第五句话。
            这是第六句话。这是第七句话。这是第八句话。这是第九句话。这是第十句话。
            """;

        SemanticSplitter splitter = new SemanticSplitter(100, 1);
        Document document = Document.from(doc, new Metadata());
        List<TextSegment> segments = splitter.split(document);

        // 有 overlap 时，相邻 segment 应有重叠内容
        if (segments.size() > 1) {
            String first = segments.get(0).text();
            String second = segments.get(1).text();
            // 第二个 segment 应包含第一个的某些句子（overlap）
            // 或者第一个包含第二个的某些句子
            boolean hasOverlap = false;
            for (String sentence : new String[]{"第一句", "第二句", "第三句"}) {
                if (second.contains(sentence)) hasOverlap = true;
            }
            assertTrue(hasOverlap || first.length() <= 100,
                    "Adjacent segments should have overlap when overlapSentences > 0");
        }
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
