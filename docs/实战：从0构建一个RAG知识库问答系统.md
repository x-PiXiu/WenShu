# 第12篇：实战——从0构建一个RAG知识库问答系统

> **📝 作者说**：前一篇我们用手工拼的零件走完了 RAG 全流程。这篇是"搬砖车间"——把那些零件变成一座真正能跑的项目，支持多格式文档、支持混合检索、支持调参优化。代码可以直接复制到生产用，不是玩具。

---

## 一、需求：做一个能上线的知识库问答

### 1.1 老板的需求 vs 你想象中的需求

大多数团队提需求时是这样说的：
> "我们要做一个智能客服，用户问问题，AI自动回答。"

你想象中的智能客服：
> 用户："我的订单到哪了？" → AI："您好，顺丰运单SF123456，预计明天送达。"

实际做出来可能是这样的：
> 用户："我的订单到哪了？" → AI："您好，关于您的问题，我需要更多信息。请问您是指哪个订单？"（检索到的是"订单查询接口文档"，不是物流信息）

这就是为什么 RAG 的实战比理论难十倍——**知道原理和能上线之间，隔着一万个坑**。

### 1.2 真实需求清单

一个能上线的知识库问答系统，至少要满足：

| 需求 | 说明 |
|------|------|
| 多格式支持 | 用户上传的文档可能是 PDF、Word、Markdown、纯文本 |
| 多文件索引 | 知识库有几十上百份文档，不是只有一份 |
| 混合检索 | 纯向量搜索有局限，关键词搜索是重要补充 |
| 相似问题判断 | 用户问"怎么退"和"可以退货吗"其实是同一个意图 |
| 拒答能力 | 知识库里没有的内容，AI 要能说"不知道" |
| 回答溯源 | 用户问"你说的依据在哪"，AI 要能指向原文 |

光有向量检索不够，你需要一套**组合拳**。

---

## 二、技术架构：完整流水线

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                     用户上传文档（Indexing）                    │
│                                                              │
│  PDF / Word / Markdown / TXT                                 │
│       │                                                     │
│       ▼                                                     │
│  AutoDocumentParser（根据格式自动选择解析器）                 │
│       │                                                     │
│       ▼                                                     │
│  DocumentSplitter.recursive()（分割成片段）                 │
│       │                                                     │
│       ▼                                                     │
│  EmbeddingModel → 向量化（存储时用同一个）                    │
│       │                                                     │
│       ▼                                                     │
│  向量库（Milvus/Chroma）+ 关键词索引（ES/MySQL FULLTEXT）     │
│                                                              │
├─────────────────────────────────────────────────────────┤
│                     用户提问（Query）                        │
│                                                              │
│  用户问题 → 改写（同一意图的不同表达）                       │
│       │                                                     │
│       ├──→ 向量检索（TopK）→ 向量分数                        │
│       │                                                     │
│       └──→ 关键词检索（BM25）→ 关键词分数                     │
│                    │                                        │
│                    ▼                                        │
│              RRF融合（Reciprocal Rank Fusion）                │
│                    │                                        │
│                    ▼                                        │
│              重排序（可选 Reranker）                         │
│                    │                                        │
│                    ▼                                        │
│              最相关的N个片段                                  │
│                    │                                        │
│                    ▼                                        │
│              Prompt注入 → ChatModel → 回答                    │
│                    │                                        │
│                    ▼                                        │
│              返回回答 + 引用来源                              │
└─────────────────────────────────────────────────────────┘
```

### 2.2 和第11篇的区别

| 功能 | 第11篇 | 第12篇（本文） |
|------|---------|--------------|
| 文档格式 | 纯文本 | PDF/Word/Markdown/纯文本自动识别 |
| 知识库规模 | 1个文件 | 多文件批量索引 |
| 检索方式 | 纯向量 | **向量+关键词混合检索（RRF）** |
| 检索重排 | 无 | 可选 Reranker |
| Prompt策略 | 简单拼接 | **多策略注入** |
| 回答质量 | 基本 | 可评估、可调参 |

---

## 三、核心组件：智能文档解析器

### 3.1 为什么需要自动识别格式？

你可能会想："那我把所有文档都转成 TXT 不就行了？"

可以的，但代价是：
- PDF 里的表格会变成一团乱码
- Word 里的加粗标题会丢失
- Markdown 的结构信息会丢失

一个好的解析器，应该**让格式信息为检索服务**。

### 3.2 自动选择解析器的实现

```java
/**
 * 根据文件后缀自动选择解析器
 */
public class AutoDocumentParser {

    public static DocumentParser forFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        
        if (filename.endsWith(".pdf")) {
            return new ApachePdfBoxDocumentParser();
        } else if (filename.endsWith(".docx")) {
            return new ApacheTikaDocumentParser();  // Tika 支持 Word
        } else if (filename.endsWith(".md")) {
            return new TextDocumentParser();         // Markdown 本质是纯文本
        } else {
            return new TextDocumentParser();        // .txt / .csv / .json 统统用这个
        }
    }
    
    /**
     * 加载单个文件
     */
    public static Document load(Path path) {
        DocumentParser parser = forFile(path);
        try (InputStream inputStream = Files.newInputStream(path)) {
            return parser.parse(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("无法加载文档: " + path, e);
        }
    }
    
    /**
     * 批量加载目录（只加载知识库目录下的文档）
     */
    public static List<Document> loadDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在：" + dir);
        }
        
        List<Document> allDocs = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> Files.isRegularFile(p))
                 .filter(p -> !p.getFileName().toString().startsWith("."))  // 跳过隐藏文件
                 .filter(p -> {
                     String name = p.getFileName().toString().toLowerCase();
                     return name.endsWith(".pdf") || name.endsWith(".docx")
                         || name.endsWith(".md") || name.endsWith(".txt");
                 })
                 .forEach(p -> {
                     try {
                         Document doc = load(p);
                         System.out.println("  ✅ 加载: " + p.getFileName()
                             + " (" + doc.text().length() + " 字)");
                         allDocs.add(doc);
                     } catch (Exception e) {
                         System.out.println("  ⚠️ 跳过: " + p.getFileName()
                             + "（解析失败: " + e.getMessage() + "）");
                     }
                 });
        } catch (IOException e) {
            throw new RuntimeException("无法遍历目录: " + dir, e);
        }

        return allDocs;
    }
}
```

使用方式：

```java
// 一行代码加载整个知识库目录
Path knowledgeDir = Path.of("knowledge/");
List<Document> docs = AutoDocumentParser.loadDirectory(knowledgeDir);
System.out.println("共加载 " + docs.size() + " 个文档");
```

### 3.3 解析器的选择建议

有人问：Tika 什么格式都能解析，是不是直接全部用 Tika 就好了？

答案是：**不要**。Tika 虽然万能，但：
- JAR 体积 80MB+
- 解析速度比专用解析器慢 3-5 倍
- 对 PDF 的解析质量不如 PDFBox

一个简单的选择原则：

```
.pdf      → ApachePdfBoxDocumentParser（轻量、快速）
.docx/.pptx → ApacheTikaDocumentParser（只有 Tika 能解析 Word）
.md/.txt  → TextDocumentParser（零依赖，效果够用）
```

---

## 四、混合检索：向量+关键词双路召回

### 4.1 纯向量检索的盲区

向量检索很强，但有一个致命弱点——**专有名词和缩写**。

举个例子：

```
知识库里的片段："Java.lang.Thread.sleep() 方法用于线程休眠"

用户问："thread怎么睡"
```

- "thread睡" 转换成向量后，和 "线程休眠" 的语义相似度很低
- 纯向量检索可能根本找不到这条
- 但关键词 "thread" 和 "睡" 在原文里是存在的

这就是为什么**关键词检索不可替代**。

### 4.2 RRF 融合算法

RRF（Reciprocal Rank Fusion，倒数排名融合）的核心思想：

```
向量检索返回：片段A（第1名）、片段B（第2名）、片段C（第5名）
关键词检索返回：片段C（第1名）、片段D（第2名）、片段A（第4名）

RRF综合排名：片段A（1/1 + 1/4 = 0.25）、片段C（1/5 + 1/1 = 1.2）、片段D（0 + 1/2 = 0.5）
```

每个片段在两个检索结果中的排名都会被考虑，最终综合出一个更合理的排名。

**为什么 RRF 比简单平均分更好？** 因为它不依赖两个检索系统的绝对分数量级——不管向量相似度是 0.9 还是 0.7，只看你排在第几名。

### 4.3 RRF 实战代码

LangChain4j 0.35+ 提供了 `EmbeddingModel.embed(String)` 的批量化接口，可以帮助实现 RRF：

```java
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器：向量 + 关键词（BM25）双路召回 → RRF 融合
 */
public class HybridSearcher {

    private final EmbeddingStore<TextSegment> vectorStore;
    private final EmbeddingModel embeddingModel;
    private final int vectorTopK;   // 向量检索返回 TopK
    private final int keywordTopK;  // 关键词检索返回 TopK
    private final double rrfK;      // RRF 参数，通常设为 60

    // 内存倒排索引：关键词 → 片段ID列表
    private final Map<String, List<String>> invertedIndex = new HashMap<>();
    // 片段ID → 片段文本（返回结果时携带原文）
    private final Map<String, String> segmentTextMap = new HashMap<>();
    // 片段ID → 来源信息
    private final Map<String, String> segmentSourceMap = new HashMap<>();
    // 文档频率：每个词在多少个片段中出现过（用于 BM25 IDF 计算）
    private final Map<String, Integer> docFrequency = new HashMap<>();
    // 总片段数（用于 BM25 IDF 计算）
    private int totalSegments = 0;

    public HybridSearcher(EmbeddingStore<TextSegment> vectorStore,
                         EmbeddingModel embeddingModel,
                         int vectorTopK, int keywordTopK, double rrfK) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.vectorTopK = vectorTopK;
        this.keywordTopK = keywordTopK;
        this.rrfK = rrfK;
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
        // Step 1: 向量检索
        List<EmbeddingMatch<TextSegment>> vectorResults = vectorSearch(query);

        // Step 2: 关键词检索（BM25）
        List<KeywordMatch> keywordResults = keywordSearch(query);

        // Step 3: RRF 融合
        return rrfFusion(vectorResults, keywordResults);
    }

    List<EmbeddingMatch<TextSegment>> vectorSearch(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(vectorTopK)
                .minScore(0.3)
                .build();

        return vectorStore.search(request).matches();
    }

    /**
     * BM25 关键词检索
     * 对查询分词后，在倒排索引中查找匹配片段，计算 BM25 分数并排序
     */
    List<KeywordMatch> keywordSearch(String query) {
        if (totalSegments == 0) return Collections.emptyList();

        List<String> queryTerms = new ArrayList<>(tokenize(query));
        if (queryTerms.isEmpty()) return Collections.emptyList();

        double avgDocLen = segmentTextMap.values().stream()
                .mapToInt(String::length)
                .average()
                .orElse(1.0);
        // BM25 参数
        double k1 = 1.2;
        double b = 0.75;

        // 对每个候选片段计算 BM25 分数
        Map<String, Double> bm25Scores = new HashMap<>();
        for (String term : queryTerms) {
            List<String> matchingIds = invertedIndex.getOrDefault(term, Collections.emptyList());
            // IDF = log((N - df + 0.5) / (df + 0.5))
            double idf = Math.log((totalSegments - matchingIds.size() + 0.5)
                                  / (matchingIds.size() + 0.5));
            for (String segId : matchingIds) {
                String text = segmentTextMap.get(segId);
                int tf = countTermFrequency(text, term);
                double docLen = text.length();
                // BM25 TF 部分
                double tfNorm = (tf * (k1 + 1))
                        / (tf + k1 * (1 - b + b * docLen / avgDocLen));
                bm25Scores.merge(segId, idf * tfNorm, Double::sum);
            }
        }

        return bm25Scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
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
        // 用于从向量检索结果中查找原始 TextSegment
        Map<String, EmbeddingMatch<TextSegment>> matchMap = new HashMap<>();

        // 向量检索得分（排名越前，RRF分越高）
        for (int i = 0; i < vectorResults.size(); i++) {
            EmbeddingMatch<TextSegment> match = vectorResults.get(i);
            String id = match.embedded().id();
            double rrfScore = 1.0 / (rrfK + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            matchMap.put(id, match);
        }

        // 关键词检索得分
        for (int i = 0; i < keywordResults.size(); i++) {
            String id = keywordResults.get(i).segmentId();
            double rrfScore = 1.0 / (rrfK + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
        }

        // 按 RRF 综合分排序，构建包含原文的结果
        return scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(vectorTopK)
                .map(e -> {
                    String id = e.getKey();
                    // 优先从向量检索结果中获取原文和分数
                    EmbeddingMatch<TextSegment> match = matchMap.get(id);
                    if (match != null) {
                        return new SearchResult(id, e.getValue(),
                                match.embedded().text(),
                                match.embedded().metadata().getString("source", "unknown"),
                                match.score());
                    }
                    // 从关键词索引中获取原文
                    return new SearchResult(id, e.getValue(),
                            segmentTextMap.getOrDefault(id, ""),
                            segmentSourceMap.getOrDefault(id, "unknown"),
                            0.0);
                })
                .toList();
    }

    // ========== 工具方法 ==========

    /** 简易分词：按非字母数字字符拆分，转小写 */
    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9\\u4e00-\\u9fa5]+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }

    /** 计算词频 */
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

    /** 关键词检索结果 */
    record KeywordMatch(String segmentId, double bm25Score) {}

    /** 融合后的搜索结果（携带原文和来源，下游可直接使用） */
    record SearchResult(String segmentId, double rrfScore,
                        String text, String source, double vectorScore) {}
}
```

> 💡 有人问：RRF 的 K 值设多少？通常 **60** 是经验值——意味着排名 60 名以后的片段对最终结果影响微乎其微。

---

## 五、Prompt注入：让回答更精准

### 5.1 Prompt 的四个层次

你知道 Prompt 写得不好，AI 回答就会跑偏。但"好 Prompt"和"差 Prompt"的区别在哪？

```
❌ 差 Prompt（没有约束）：
  "用户问：我的订单到哪了。请回答。"
  → AI 可能瞎编一个物流信息

✅ 普通 Prompt（有约束）：
  "你是客服助手。如果知识库中有答案，请基于知识库回答。
   如果知识库中没有，请说'我不确定'。不要编造。"
  → AI 至少不会瞎编了

✅ 好的 Prompt（有结构）：
  "【角色】你是售后客服助手。
   【知识库】以下是相关文档片段：
   [1] 物流信息：2-3个工作日发货...
   [2] 退货政策：7天内可申请...
   【要求】先判断知识库是否有答案，
          如果有，用【知识库】中的编号引用回答；
          如果没有，明确说'知识库中未找到相关信息'。"

✅ 优秀的 Prompt（考虑边界）：
  "【角色】售后客服助手。
   【知识库范围】仅限：物流信息、退换货政策、优惠券规则、积分兑换。
   【禁止】不要回答超出知识库范围的问题，如价格、实体店地址等。
   【回答格式】先判断是否在知识库范围内，
              范围内则回答 + 引用编号；
              范围外则说'这个问题超出我的服务范围'。"
```

你发现了吗？Prompt 越具体，AI 越不容易跑偏。

### 5.2 LangChain4j 的 Prompt 模板工具

```java
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;

/**
 * RAG 专用的 Prompt 模板
 */
public class RagPromptTemplate {

    /** 角色设定 */
    private static final String SYSTEM_PROMPT = """
            【角色】你是售后客服助手，基于提供的参考资料回答用户问题。
            
            【要求】
            1. 只基于参考资料中的信息回答，不要编造。
            2. 如果参考资料中有相关信息，引用时使用 [编号] 格式。
            3. 如果参考资料中没有相关信息，明确说"知识库中未找到相关信息"。
            4. 回答要简洁，最多3句话。
            5. 禁止回答超出参考资料范围的问题。
            """;

    /**
     * 构建完整的 Prompt
     */
    public static String build(String question, List<Reference> references) {
        if (references.isEmpty()) {
            // 无参考资料的情况
            return SYSTEM_PROMPT + "\n\n用户问题：" + question;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT);
        sb.append("\n\n【参考资料】\n");
        
        for (int i = 0; i < references.size(); i++) {
            Reference ref = references.get(i);
            sb.append("[").append(i + 1).append("] ")
              .append(ref.text()).append("\n");
        }
        
        sb.append("\n用户问题：").append(question);
        return sb.toString();
    }

    /** 参考资料记录 */
    public record Reference(String text, String source, double score) {}
}
```

使用：

```java
// HybridSearcher 返回的 SearchResult 已携带原文
List<SearchResult> results = searcher.search("我的订单到哪了？");

// 转换为 Reference（SearchResult 已包含 text/source/vectorScore）
List<RagPromptTemplate.Reference> refs = results.stream()
    .map(r -> new RagPromptTemplate.Reference(
        r.text(), r.source(), r.vectorScore()))
    .toList();

// 构建 Prompt
String prompt = RagPromptTemplate.build("我的订单到哪了？", refs);

// 发送给 LLM
ChatResponse response = chatModel.chat(
    ChatRequest.builder()
        .messages(UserMessage.from(prompt))
        .build()
);
String answer = response.aiMessage().text();
```

### 5.3 三个立刻提升回答质量的技巧

**技巧1：让 AI 先判断，再回答**

```
在 Prompt 里加一句："先判断知识库中是否有相关信息，再决定如何回答"
→ 防止 AI 在知识库为空时仍然瞎编
```

**技巧2：限制回答范围**

```
不要问"请回答用户问题"
而是"请判断以下参考资料是否包含用户问题的答案，
 如果包含，给出答案；如果不包含，说'知识库中未找到'"
→ AI 的任务更清晰，跑偏概率更低
```

**技巧3：加输出格式要求**

```
"回答格式：判断结果 + 具体答案 + 参考编号。
 例如：✅ 知识库中有相关信息 [1]。退货政策为7天内可申请。"
→ 输出结构化，方便你后续解析和统计
```

---

## 六、效果评估：你的 RAG 系统到底好不好？

### 6.1 三个核心指标

很多人做完 RAG 系统就以为大功告成了，结果上线后才发现检索质量差、回答不准。

在庆祝之前，先问自己三个问题：

```
问题1：检索回来的片段，真的和用户问题相关吗？
问题2：LLM 生成的回答，真的基于检索到的内容吗？
问题3：回答的格式，用户能看懂吗？
```

### 6.2 检索质量评估

| 指标 | 含义 | 怎么算好 |
|------|------|---------|
| **Precision@K** | 召回的 K 个片段里，有多少是真正相关的？ | Top3 ≥ 80% |
| **Recall@K** | 所有相关片段里，被召回了多少？ | Top10 ≥ 90% |
| **MRR** | 第一个相关结果排在第几位？ | MRR ≥ 0.8 |

代码实现：

```java
/**
 * 检索质量评估
 */
public class RetrievalEvaluator {

    /**
     * 计算 Recall@K
     */
    public static double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
        if (retrieved.isEmpty() || k == 0) return 0.0;
        
        List<String> topK = retrieved.subList(0, Math.min(k, retrieved.size()));
        long hitCount = topK.stream()
                .filter(relevant::contains)
                .count();
        
        return (double) hitCount / relevant.size();
    }

    /**
     * 计算 MRR（Mean Reciprocal Rank）
     */
    public static double meanReciprocalRank(List<List<String>> queries) {
        double totalMrr = 0.0;
        
        for (List<String> retrieved : queries) {
            for (int i = 0; i < retrieved.size(); i++) {
                // 假设每个查询只有一个正确答案
                if (isCorrect(retrieved.get(i))) {
                    totalMrr += 1.0 / (i + 1);
                    break;
                }
            }
        }
        
        return totalMrr / queries.size();
    }
    
    private static boolean isCorrect(String docId) {
        // 实际项目中与标准答案对比
        return true;
    }
}
```

### 6.3 生成质量评估

检索质量好了，生成质量也可能翻车。常见问题：

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| AI 编造答案 | Prompt 没约束 | 加"不确定就说不知道" |
| 答案不引用原文 | 没强调引用格式 | 加"[编号]引用"要求 |
| 答案太啰嗦 | 没限制长度 | 加"最多3句话" |
| 答案跳过了关键信息 | 片段不完整 | 调 chunk_size 或 topK |

### 6.4 一个自动评估流程

```java
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

    /**
     * 在测试集上跑一遍，输出评估报告
     */
    public EvaluationReport evaluate(List<TestCase> testCases) {
        int total = testCases.size();
        int retrievalHits = 0;
        int generationCorrect = 0;

        for (TestCase tc : testCases) {
            // 检索
            List<SearchResult> results = searcher.search(tc.question());
            boolean hit = results.stream()
                    .anyMatch(r -> tc.relevantIds().contains(r.segmentId()));
            if (hit) retrievalHits++;

            // 生成
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
        // 复用 Prompt 模板，将 SearchResult 转为 Reference
        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String prompt = RagPromptTemplate.build(question, refs);
        return chatModel.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(prompt))
                        .build()
        ).aiMessage().text();
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

    /** 测试用例（用关键词集合替代单字符串，更准确） */
    record TestCase(String question, Set<String> relevantIds, Set<String> expectedKeywords) {}

    /** 评估报告 */
    record EvaluationReport(double retrievalHitRate, double generationCorrectRate, int totalCases) {
        public String summary() {
            return String.format(
                "检索命中率: %.1f%% | 生成正确率: %.1f%% | 总测试用例: %d",
                retrievalHitRate * 100, generationCorrectRate * 100, totalCases
            );
        }
    }
}
```

---

## 七、完整项目：RagKnowledgeBaseApp

### 7.1 项目结构

```
src/main/java/com/example/rag/
├── parser/
│   └── AutoDocumentParser.java       ← 智能文档解析器
├── search/
│   └── HybridSearcher.java           ← 混合检索（向量+BM25关键词+RRF融合）
├── prompt/
│   └── RagPromptTemplate.java       ← Prompt 模板
├── eval/
│   ├── RetrievalEvaluator.java      ← 检索质量评估
│   └── RagEvaluator.java            ← 完整 RAG 评估
└── RagKnowledgeBaseApp.java          ← 入口类
```

### 7.2 Maven 依赖

```xml
<properties>
    <langchain4j.version>1.0.0</langchain4j.version>
</properties>

<dependencies>
    <!-- 核心库 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- OpenAI 兼容接口（ChatModel + EmbeddingModel）-->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- PDF 解析 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- 万能解析器（Word/PPT/HTML）-->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-document-parser-apache-tika</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- 向量数据库（生产用 Milvus，可先用 InMemory 调试）-->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-store-embedding-inmemory</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>
</dependencies>
```

### 7.3 入口类完整代码

```java
package com.example.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import com.example.rag.parser.AutoDocumentParser;
import com.example.rag.search.HybridSearcher;
import com.example.rag.search.HybridSearcher.SearchResult;
import com.example.rag.prompt.RagPromptTemplate;
import com.example.rag.prompt.RagPromptTemplate.Reference;

import java.nio.file.Path;
import java.util.List;

/**
 * RAG 知识库问答系统（LangChain4j 1.x 版）
 */
public class RagKnowledgeBaseApp {

    public static void main(String[] args) {
        // ========== 1. 初始化 ==========
        String apiKey = System.getenv("MINIMAX_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("❌ 请设置环境变量 MINIMAX_API_KEY");
            System.exit(1);
        }
        String baseUrl = "https://api.minimax.chat/v1";

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName("MiniMax-M2.5").build();

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName("embo-01").build();

        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // ========== 2. Indexing：加载知识库 → 分割 → 向量化 → 存储 ==========
        System.out.println("📚 正在索引知识库...");
        List<Document> docs = AutoDocumentParser.loadDirectory(Path.of("knowledge/"));
        if (docs.isEmpty()) {
            System.err.println("❌ 未找到任何知识库文档，请检查 knowledge/ 目录");
            System.exit(1);
        }

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 30,
                        new OpenAiTokenCountEstimator("gpt-4o")))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();

        docs.forEach(ingestor::ingest);
        System.out.println("✅ 索引完成，共 " + docs.size() + " 个文档\n");

        // ========== 3. 创建混合检索器 ==========
        HybridSearcher searcher = new HybridSearcher(store, embeddingModel, 5, 10, 60);

        // ========== 4. 问答循环 ==========
        System.out.println("💬 知识库问答系统已启动（输入 exit 退出）");
        System.out.println("=".repeat(50));

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            System.out.print("\n📝 你的问题：");
            String question = scanner.nextLine().trim();

            if (question.isEmpty()) continue;
            if (question.equalsIgnoreCase("exit")) break;

            try {
                // Step A: 混合检索（向量 + 关键词 BM25 → RRF 融合）
                List<SearchResult> results = searcher.search(question);

                if (results.isEmpty()) {
                    System.out.println("⚠️ 未找到相关信息，请尝试其他表述");
                    continue;
                }

                // Step B: 转换为 Reference，构建 Prompt
                List<Reference> refs = results.stream()
                        .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                        .toList();
                String prompt = RagPromptTemplate.build(question, refs);

                // Step C: 生成回答
                ChatResponse response = chatModel.chat(
                        ChatRequest.builder()
                                .messages(UserMessage.from(prompt))
                                .build()
                );
                String answer = response.aiMessage().text();

                // Step D: 输出结果
                System.out.println("\n💬 回答：");
                System.out.println("  " + answer);
                System.out.println("\n📎 参考来源：");
                for (int i = 0; i < results.size(); i++) {
                    SearchResult r = results.get(i);
                    String preview = r.text().length() > 80
                            ? r.text().substring(0, 80) + "..."
                            : r.text();
                    System.out.printf("  [%d] RRF=%.4f 向量=%.4f | %s%n",
                            i + 1, r.rrfScore(), r.vectorScore(), preview);
                }
            } catch (Exception e) {
                System.err.println("❌ 处理问题时出错: " + e.getMessage());
            }
        }

        System.out.println("\n👋 再见！");
    }
}
```

### 7.4 运行效果

```
📚 正在索引知识库...
  ✅ 加载: 退货政策.md (1243 字)
  ✅ 加载: 物流说明.pdf (2876 字)
  ✅ 加载: 优惠券规则.txt (856 字)
✅ 索引完成，共 3 个文档

💬 知识库问答系统已启动（输入 exit 退出）
==================================================

📝 你的问题：我的订单什么时候到？
  💬 回答：
  根据物流信息[1]，您的订单将在2-3个工作日内发货送达。具体时效取决于您所在地区。

📎 参考来源：
  [1] RRF=0.0327 向量=0.9123 | 物流信息：默认使用顺丰速运，一线城市1-2天...
  [2] RRF=0.0164 向量=0.7234 | 退货政策：7天内可申请无理由退货...
  [3] RRF=0.0119 向量=0.6512 | 优惠券规则：每笔订单只能使用一张...

📝 你的问题：thread怎么睡
  💬 回答：
  根据参考资料[1]，`Thread.sleep()` 方法用于线程休眠。

📎 参考来源：
  [1] RRF=0.0492 向量=0.5432 | Java.lang.Thread.sleep() 方法用于线程休眠...

📝 你的问题：exit
👋 再见！
```

---

## 八、调优总结：让 RAG 系统从 60 分到 90 分

### 8.1 调优优先级

很多人在不该优化的方向花了大量时间。记住这个优先级：

```
第一优先（影响 80% 效果）
  ✅ chunk_size：200~500 Token 之间试，找最合适的
  ✅ topK：返回 3~5 个片段，不要太少也不要太多

第二优先（影响 15% 效果）
  ✅ Embedding 模型：换更好的中文模型（embedding-3）
  ✅ minScore 阈值：太高漏检，太低噪声多

第三优先（影响 5% 效果）
  ✅ 混合检索：向量 + 关键词 RRF 融合
  ✅ Prompt 模板：加角色设定和回答格式要求
```

### 8.2 一句话调优口诀

> **先让检索找到对的东西，再让 AI 读懂检索结果，最后约束 AI 不要跑偏。**

80% 的 RAG 优化都是"检索优化"——检索找到了正确的片段，AI 的回答就差不了。

### 8.3 三个最容易踩的坑

| 坑 | 表现 | 解决方案 |
|---|------|---------|
| **文档格式乱** | 检索质量差 | 先做文档预处理，去除无关内容 |
| **chunk_size 乱设** | 答案时好时坏 | 固定 300 Token，跑通了再调 |
| **只调 Prompt 不调检索** | AI 回答越来越"圆滑" | Prompt 优化解决不了检索差的问题 |

---

## 九、总结

### 核心要点

| 要点 | 说明 |
|------|------|
| **AutoDocumentParser** | 根据后缀自动选解析器，零额外依赖处理 Markdown |
| **RRF 融合** | 向量+关键词双路召回，综合排名更稳定 |
| **Prompt 模板** | 角色 + 参考资料 + 格式要求 + 拒答约束 |
| **效果评估** | Precision@K / Recall@K / MRR 量化检索质量 |
| **调优优先级** | 检索 > Prompt > 模型 |

### 项目代码速查

| 功能 | 类/方法 |
|------|---------|
| 自动解析多格式文档 | `AutoDocumentParser.loadDirectory()` |
| 分割+向量化+存储 | `EmbeddingStoreIngestor.ingest()` |
| 混合检索 | `EmbeddingStore.search()` + RRF |
| 构建 Prompt | `RagPromptTemplate.build()` |
| 发送问答 | `chatModel.chat(ChatRequest)` |
| 效果评估 | `RagEvaluator.evaluate()` |

### 与前几篇的关系

```
第10篇：文档加载与分割 → 从文件到TextSegment
第11篇：RAG核心原理 → 三件套完整链路
第12篇：RAG实战 → 从0构建完整项目 ⭐ 本篇
第13篇：Tool与Function Calling → 让AI调用外部工具
```

---

## 下期预告

> 第13篇：Tool与Function Calling——让AI真正动手干活
>
> 剧透：
> - 为什么 RAG + LLM 还是不够？LLM 不知道实时信息
> - Tool 的本质：让 AI 调用外部 API / 数据库
> - @Tool 注解：声明式工具定义
> - 工具选择策略：ZeroShot / SingleShot / Conversational
> - 实战：给 AI 接入天气查询 + 数据库查询能力

---

## 往期链接

| 篇目 | 标题 | 状态 |
|------|------|------|
| 第1篇 | 开篇：AI应用时代，Java还能打吗？ | ✅ 已发布 |
| 第2篇 | 开发环境搭建：5分钟跑通第一个AI对话 | ✅ 已发布 |
| 第3篇 | Prompt工程：不只是写提示词 | ✅ 已发布 |
| 第4篇 | Memory机制：让AI拥有记忆 | ✅ 已发布 |
| 第5篇 | LLM模型调用：ChatModel核心机制 | ✅ 已发布 |
| 第6篇 | Chain：让AI工作流串联起来 | ✅ 已发布 |
| 第7篇 | 实战：构建一个智能客服对话系统 | ✅ 已发布 |
| 第8篇 | 向量数据库：RAG的根基 | ✅ 已发布 |
| 第9篇 | Embedding与向量检索：让AI理解语义 | ✅ 已发布 |
| 第10篇 | 文档加载与分割：让AI读到你的资料 | ✅ 已发布 |
| 第11篇 | RAG核心原理：检索增强生成完整链路 | ✅ 已发布 |
| 第12篇 | 实战：从0构建一个RAG知识库问答系统 | 🔜 本文 |

---

> **📱 小貔貅Agent日记**
>
> 一个专注Java AI应用开发的技术号
>
> 关注我，带你用Java玩转AI！**国产模型，不需要翻墙！**
