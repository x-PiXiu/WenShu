package com.example.rag.service;

import com.example.rag.config.AppConfiguration;
import com.example.rag.config.ModelFactory;
import com.example.rag.config.VectorStoreFactory;
import com.example.rag.parser.AutoDocumentParser;
import com.example.rag.parser.DocumentMetaStore;
import com.example.rag.parser.DocumentTypeDetector;
import com.example.rag.parser.SemanticSplitter;
import com.example.rag.prompt.RagPromptTemplate;
import com.example.rag.prompt.RagPromptTemplate.Reference;
import com.example.rag.search.HybridSearcher;
import com.example.rag.search.HybridSearcher.SearchResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 核心服务：封装文档索引 + 检索 + 生成的完整流程
 * 支持运行时动态切换模型和重新索引
 */
public class RagService {

    private volatile ChatModel chatModel;
    private volatile StreamingChatModel streamingChatModel;
    private volatile EmbeddingModel embeddingModel;
    private volatile EmbeddingStore<TextSegment> store;
    private volatile HybridSearcher searcher;
    private volatile DocumentSplitter splitter;

    // Dedicated lock for thread-unsafe store/searcher writes (NOT 'this')
    private final Object indexLock = new Object();

    private final AppConfiguration config;
    private final DocumentMetaStore metaStore;
    private volatile MemoryStore memoryStore;
    private volatile List<ChatModelListener> listeners = List.of();
    private volatile com.example.rag.tools.ToolEngine toolEngine;
    private volatile com.example.rag.tools.WebSearcher webSearcher;
    private Object[] toolObjects = new Object[0];
    private int documentCount = 0;
    private int segmentCount = 0;

    public RagService(AppConfiguration config) {
        this.config = config;
        this.metaStore = new DocumentMetaStore("knowledge");
        rebuildModels();
    }

    public void setMemoryStore(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    public void setListeners(List<ChatModelListener> listeners) {
        this.listeners = listeners != null ? listeners : List.of();
        rebuildModels();
    }

    public void setToolEngine(com.example.rag.tools.ToolEngine engine) {
        this.toolEngine = engine;
    }

    public void setWebSearcher(com.example.rag.tools.WebSearcher searcher) {
        this.webSearcher = searcher;
    }

    public void setToolObjects(Object... objects) {
        this.toolObjects = objects != null ? objects : new Object[0];
    }

    /**
     * 根据配置重建所有模型实例
     */
    public synchronized void rebuildModels() {
        this.chatModel = ModelFactory.createChatModel(config.getLlm(), listeners);
        this.streamingChatModel = ModelFactory.createStreamingChatModel(config.getLlm(), listeners);
        this.embeddingModel = ModelFactory.createEmbeddingModel(config.getEmbedding());
        this.store = VectorStoreFactory.create(config.getVectorStore());

        AppConfiguration.RagConfig rag = config.getRag();
        this.splitter = DocumentSplitters.recursive(rag.chunkSize, rag.chunkOverlap);
        this.searcher = new HybridSearcher(store, embeddingModel,
                rag.vectorTopK, rag.keywordTopK, rag.rrfK, rag.minScore);

        this.documentCount = 0;
        this.segmentCount = 0;
    }

    /**
     * 索引知识库目录下的所有文档
     */
    public void indexKnowledgeBase(String knowledgeDir) {
        List<Document> docs = AutoDocumentParser.loadDirectory(Path.of(knowledgeDir));
        if (docs.isEmpty()) {
            throw new RuntimeException("No documents found in " + knowledgeDir);
        }

        segmentCount = 0;
        documentCount = docs.size();
        long startTime = System.currentTimeMillis();

        // Collect all segments from all documents
        List<TextSegment> allSegments = new ArrayList<>();
        for (Document doc : docs) {
            String source = doc.metadata().getString("source");
            DocumentSplitter typeSplitter = createSplitterForReindex(source, doc.text());
            allSegments.addAll(typeSplitter.split(doc));
        }
        int totalSegments = allSegments.size();
        System.out.println("[INDEX] " + documentCount + " docs, " + totalSegments
                + " segments to embed (parallel)...");

        // Parallel embedding: 4 threads
        AtomicInteger processed = new AtomicInteger(0);
        int concurrency = 4;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>();

        for (TextSegment segment : allSegments) {
            futures.add(executor.submit(() -> {
                try {
                    // Embedding API call (thread-safe, can run in parallel)
                    Embedding embedding = embeddingModel.embed(segment.text()).content();

                    // Store write (NOT thread-safe, must synchronize)
                    String id;
                    String segSource = segment.metadata().getString("source");
                    synchronized (indexLock) {
                        id = store.add(embedding, segment);
                        searcher.indexSegment(id, segment.text(),
                                segSource != null ? segSource : "unknown");
                        segmentCount++;
                    }

                    int done = processed.incrementAndGet();
                    if (done % 50 == 0 || done == totalSegments) {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        System.out.println("[INDEX] " + done + "/" + totalSegments
                                + " segments embedded (" + elapsed + "s elapsed)");
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Failed to embed segment: " + e.getMessage());
                    processed.incrementAndGet();
                }
            }));
        }

        // Wait for all tasks to complete
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) {
                System.err.println("[WARN] Embedding task failed: " + e.getMessage());
            }
        }
        executor.shutdown();
    }

    /**
     * RAG 问答核心方法
     */
    public RagAnswer ask(String question) {
        List<SearchResult> results = searcher.search(question);

        if (results.isEmpty()) {
            return new RagAnswer("Knowledge base does not contain relevant information.", List.of(), List.of());
        }

        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String prompt = RagPromptTemplate.build(question, refs);
        String answer = stripThinkTags(chatModel.chat(prompt));

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            sources.add(toSourceInfo(i + 1, results.get(i)));
        }

        return new RagAnswer(answer, sources, refs.stream()
                .map(r -> r.text().length() > 80 ? r.text().substring(0, 80) + "..." : r.text())
                .toList());
    }

    /**
     * 简单问答（A2A Skill 调用）
     */
    public String query(String question) {
        return ask(question).answer();
    }

    /**
     * 带对话历史的 RAG 问答
     * 使用多消息格式，将历史消息 + RAG 检索上下文一起发送给 LLM
     */
    public RagAnswer askWithContext(String question, List<HistoryEntry> history) {
        return askWithContext(question, history, null);
    }

    /**
     * 带对话历史 + 自定义智能体提示词的 RAG 问答
     * 使用 AiServices 自动管理 Tool Calling 多轮循环
     */
    public RagAnswer askWithContext(String question, List<HistoryEntry> history, String agentPrompt) {
        List<SearchResult> results = searcher.search(question);

        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String systemContent = buildSystemContent(question, agentPrompt, refs, results);

        var memory = buildChatMemory(history, systemContent);
        var assistant = buildAssistant(memory);

        String answer = stripThinkTags(assistant.chat(question));
        if (answer == null) {
            answer = "未能生成回答。";
        }

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            sources.add(toSourceInfo(i + 1, results.get(i)));
        }

        return new RagAnswer(answer, sources, refs.stream()
                .map(r -> r.text().length() > 80 ? r.text().substring(0, 80) + "..." : r.text())
                .toList());
    }

    /**
     * 流式 Agentic 问答：返回 TokenStream + sources
     * AiServices 自动管理 Tool Calling 循环（包括流式模式）
     */
    public AgenticStreamContext prepareAgenticStream(String question, List<HistoryEntry> history, String agentPrompt) {
        List<SearchResult> results = searcher.search(question);

        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String systemContent = buildSystemContent(question, agentPrompt, refs, results);

        var memory = buildChatMemory(history, systemContent);
        var assistant = buildAssistant(memory);
        TokenStream tokenStream = assistant.chatStream(question);

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            sources.add(toSourceInfo(i + 1, results.get(i)));
        }

        return new AgenticStreamContext(tokenStream, sources);
    }

    /**
     * 流式生成：将消息列表发送给 StreamingChatModel（用于博客等不走 AiServices 的路径）
     */
    public void streamGenerate(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        streamingChatModel.chat(messages, handler);
    }

    /**
     * 生成对话摘要
     */
    public String summarizeConversation(List<HistoryEntry> fullHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请用2-3句话总结以下对话中的关键知识点和用户需求，只保留最重要的信息：\n\n");
        for (HistoryEntry entry : fullHistory) {
            if ("user".equals(entry.role())) {
                prompt.append("用户: ").append(entry.content()).append("\n");
            } else if ("assistant".equals(entry.role())) {
                // Truncate long assistant messages for summary
                String content = entry.content();
                if (content.length() > 200) content = content.substring(0, 200) + "...";
                prompt.append("助手: ").append(content).append("\n");
            }
        }
        return stripThinkTags(chatModel.chat(prompt.toString()));
    }

    /**
     * 将摘要向量化存储到知识库，作为长期记忆
     * 后续 RAG 检索时会自动检索到这些记忆片段
     */
    public void storeMemory(String conversationId, String summary, List<HistoryEntry> history) {
        // Score importance
        List<MemoryStore.MemoryEntry> existing = memoryStore != null
                ? memoryStore.listByConversation(conversationId) : List.of();
        double importance = MemoryScorer.score(summary, history, existing);

        // Persist to SQLite
        if (memoryStore != null) {
            memoryStore.storeMemory(conversationId, summary, importance);
        }

        // Store in vector store for semantic retrieval
        Metadata meta = new Metadata();
        meta.put("source", "memory:" + conversationId);
        meta.put("type", "memory");
        meta.put("importance", importance);
        TextSegment segment = TextSegment.from("[记忆摘要] " + summary, meta);
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        String id = store.add(embedding, segment);
        searcher.indexSegment(id, segment.text(), "memory:" + conversationId);
    }

    /**
     * Recall relevant memories for a given query
     */
    public List<String> recallMemories(String query, int limit) {
        if (memoryStore == null || memoryStore.getMemoryCount() == 0) return List.of();
        try {
            Embedding queryEmb = embeddingModel.embed(query).content();
            dev.langchain4j.store.embedding.EmbeddingSearchRequest request =
                    dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmb)
                            .maxResults(limit * 2)
                            .minScore(0.3)
                            .build();
            var results = store.search(request).matches();
            List<String> memories = new ArrayList<>();
            for (var hit : results) {
                String source = hit.embedded().metadata().getString("source");
                if (source != null && source.startsWith("memory:")) {
                    memories.add(hit.embedded().text());
                    if (memories.size() >= limit) break;
                }
            }
            return memories;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 索引单个文档文件（用于文档上传场景）
     * @param file 文件路径
     * @param type 文档类型，决定分块策略
     */
    public synchronized void indexDocument(java.nio.file.Path file, String type) {
        Document doc = AutoDocumentParser.load(file);
        DocumentSplitter typeSplitter = createSplitter(type);

        List<TextSegment> segments = typeSplitter.split(doc);
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            String id = store.add(embedding, segment);
            String source = segment.metadata().getString("source");
            searcher.indexSegment(id, segment.text(), source != null ? source : "unknown");
            segmentCount++;
        }
        documentCount++;

        // Persist metadata for reindex
        String filename = file.getFileName().toString();
        AppConfiguration.DocumentTypeConfig cfg = config.findDocTypeConfig(type);
        metaStore.put(filename, type, cfg.chunkSize, cfg.chunkOverlap);
    }

    /**
     * 根据文档类型名创建对应的 Splitter（从配置读取分块参数）
     */
    private DocumentSplitter createSplitter(String typeName) {
        return createSplitter(typeName, null);
    }

    /**
     * 根据文档类型名创建对应的 Splitter，可传入 StructureProfile 做动态调整
     */
    private DocumentSplitter createSplitter(String typeName,
                                            DocumentTypeDetector.StructureProfile profile) {
        AppConfiguration.DocumentTypeConfig cfg = config.findDocTypeConfig(typeName);
        return new SemanticSplitter(cfg.chunkSize, cfg.overlapSentences, profile);
    }

    /**
     * 重索引时创建 Splitter：根据用户原始选择决定策略
     * - 自动检测上传的文档 → 重新运行检测，获取 StructureProfile
     * - 手动指定类型的文档 → 使用存储的类型，不传 profile
     * - 无元数据的文档 → 自动检测
     */
    private DocumentSplitter createSplitterForReindex(String filename, String content) {
        DocumentMetaStore.DocMeta docMeta = metaStore.get(filename);

        if (docMeta == null) {
            // 无元数据：自动检测
            DocumentTypeDetector.DetailedDetection detailed = DocumentTypeDetector.detectDetailed(filename, content);
            return createSplitter(detailed.result().type(), detailed.profile());
        }

        if ("structure".equals(docMeta.detectionMethod())) {
            // 原先是自动检测 → 重跑检测获取最新 profile
            DocumentTypeDetector.DetailedDetection detailed = DocumentTypeDetector.detectDetailed(filename, content);
            return createSplitter(detailed.result().type(), detailed.profile());
        }

        // 手动指定类型 → 保持用户选择，不传 profile
        return createSplitter(docMeta.type());
    }

    /**
     * 自动检测文档类型并索引
     * @param file 文件路径
     * @return 检测到的文档类型
     */
    public synchronized String autoDetectAndIndex(java.nio.file.Path file) {
        Document doc = AutoDocumentParser.load(file);

        // 自动检测文档类型（同时获取结构分析详情）
        String filename = file.getFileName().toString();
        String docText = doc.text();
        DocumentTypeDetector.DetailedDetection detailed = DocumentTypeDetector.detectDetailed(filename, docText);
        DocumentTypeDetector.DetectionResult detection = detailed.result();
        String detectedType = detection.type();

        System.out.println("[AUTO-DETECT] " + filename + " → " + detectedType
                + " (confidence: " + String.format("%.2f", detection.confidence())
                + ", method: " + detection.method()
                + ", codeRatio: " + String.format("%.1f%%", detailed.profile().codeRatio() * 100)
                + ")");

        // 使用检测到的类型 + 结构分析进行分块和索引
        DocumentSplitter typeSplitter = createSplitter(detectedType, detailed.profile());
        List<TextSegment> segments = typeSplitter.split(doc);

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            String id = store.add(embedding, segment);
            String source = segment.metadata().getString("source");
            searcher.indexSegment(id, segment.text(), source != null ? source : "unknown");
            segmentCount++;
        }
        documentCount++;

        // 持久化元数据（记录检测来源）
        AppConfiguration.DocumentTypeConfig cfg = config.findDocTypeConfig(detectedType);
        metaStore.put(filename, detectedType, cfg.chunkSize, cfg.chunkOverlap, detection.method());

        return detectedType;
    }

    /**
     * 删除文档元数据
     */
    public void removeDocumentMeta(String filename) {
        metaStore.remove(filename);
    }

    /**
     * 获取所有文档元数据
     */
    public Map<String, DocumentMetaStore.DocMeta> getDocumentMeta() {
        return metaStore.getAll();
    }

    /**
     * 清除并重新索引全部文档（用于删除文档后重建索引）
     */
    public synchronized void reindexAll(String knowledgeDir) {
        this.documentCount = 0;
        this.segmentCount = 0;
        this.store = VectorStoreFactory.create(config.getVectorStore());
        AppConfiguration.RagConfig rag = config.getRag();
        this.searcher = new HybridSearcher(store, embeddingModel,
                rag.vectorTopK, rag.keywordTopK, rag.rrfK, rag.minScore);

        // Re-index remaining documents
        indexKnowledgeBase(knowledgeDir);
    }

    // ===== Blog Article Indexing =====

    /**
     * 索引博客文章到向量库（自动检测类型 + 自适应分块）
     */
    public synchronized void indexBlogArticle(String articleId, String slug, String title, String content, String category) {
        String source = "blog:" + slug;
        Metadata baseMeta = new Metadata();
        baseMeta.put("source", source);
        baseMeta.put("type", "blog");
        baseMeta.put("articleId", articleId);
        baseMeta.put("title", title);
        baseMeta.put("category", category != null ? category : "");

        // Auto-detect article structure for optimal chunking
        String fullText = title + "\n\n" + content;
        DocumentTypeDetector.DetailedDetection detailed = DocumentTypeDetector.detectDetailed(slug, fullText);
        String detectedType = detailed.result().type();
        DocumentSplitter articleSplitter = createSplitter(detectedType, detailed.profile());

        System.out.println("[BLOG-INDEX] " + slug + " → " + detectedType
                + " (codeRatio: " + String.format("%.1f%%", detailed.profile().codeRatio() * 100) + ")");

        Document doc = Document.from(fullText, baseMeta);
        List<TextSegment> segments = articleSplitter.split(doc);

        int indexed = 0;
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            String id;
            synchronized (indexLock) {
                id = store.add(embedding, segment);
                searcher.indexSegment(id, segment.text(), source);
                segmentCount++;
            }
            indexed++;
        }
        System.out.println("[BLOG-INDEX] Indexed article: " + slug + " (" + indexed + " segments)");
    }

    /**
     * 移除博客文章的向量索引
     */
    public void removeBlogSegments(String slug) {
        String source = "blog:" + slug;
        synchronized (indexLock) {
            searcher.removeSegmentsBySource(source);
        }
        System.out.println("[BLOG-INDEX] Removed segments for: " + slug);
    }

    /**
     * 博客文章直接问答：用文章全文作为上下文，无需向量检索
     */
    public RagAnswer askBlogDirect(String question, List<HistoryEntry> history,
                                   String articleTitle, String articleContent, String agentPrompt) {
        StringBuilder systemContent = new StringBuilder();
        systemContent.append(agentPrompt != null && !agentPrompt.isBlank() ? agentPrompt : BLOG_SYSTEM_PROMPT);
        systemContent.append("\n\n【当前文章】\n");
        systemContent.append("标题：").append(articleTitle).append("\n\n");
        systemContent.append(articleContent);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent.toString()));

        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            if ("user".equals(entry.role())) {
                messages.add(UserMessage.from(entry.content()));
            } else if ("assistant".equals(entry.role())) {
                messages.add(AiMessage.from(entry.content()));
            }
        }
        messages.add(UserMessage.from(question));

        ChatResponse chatResponse = chatModel.chat(messages);
        String answer = stripThinkTags(chatResponse.aiMessage().text());
        if (answer == null) answer = "未能生成回答。";

        return new RagAnswer(answer, List.of(), List.of());
    }

    /**
     * 博客文章直接问答（流式）：准备消息列表
     */
    public StreamContext prepareBlogDirectStreamContext(String question, List<HistoryEntry> history,
                                                        String articleTitle, String articleContent, String agentPrompt) {
        StringBuilder systemContent = new StringBuilder();
        systemContent.append(agentPrompt != null && !agentPrompt.isBlank() ? agentPrompt : BLOG_SYSTEM_PROMPT);
        systemContent.append("\n\n【当前文章】\n");
        systemContent.append("标题：").append(articleTitle).append("\n\n");
        systemContent.append(articleContent);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent.toString()));

        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            if ("user".equals(entry.role())) {
                messages.add(UserMessage.from(entry.content()));
            } else if ("assistant".equals(entry.role())) {
                messages.add(AiMessage.from(entry.content()));
            }
        }
        messages.add(UserMessage.from(question));

        return new StreamContext(messages, List.of());
    }

    private static final String BLOG_SYSTEM_PROMPT = """
            你是「文枢·博客」的智能助手，专门回答关于当前文章的问题。
            你已经收到了用户正在阅读的完整文章内容，请严格基于该文章内容回答。

            ## 回答原则
            - 严格基于【当前文章】的内容回答，不编造、不推测、不引入外部知识。
            - 使用中文回答，语言清晰流畅，适当使用 Markdown 格式提升可读性。
            - 支持多轮对话，结合历史上下文理解用户的追问。
            - 如果问题与文章内容无关，礼貌地引导用户围绕文章内容提问。
            """;

    /**
     * 博客专属 RAG 问答（可限定到特定文章）
     */
    public RagAnswer askBlog(String question, List<HistoryEntry> history, String agentPrompt, String slug) {
        String sourcePrefix = slug != null && !slug.isBlank() ? "blog:" + slug : "blog:";
        List<SearchResult> results = searcher.search(question, sourcePrefix);

        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String systemContent = RagPromptTemplate.buildSystemContext(agentPrompt, refs);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent));

        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            if ("user".equals(entry.role())) {
                messages.add(UserMessage.from(entry.content()));
            } else if ("assistant".equals(entry.role())) {
                messages.add(AiMessage.from(entry.content()));
            }
        }
        messages.add(UserMessage.from(question));

        ChatResponse chatResponse = chatModel.chat(messages);
        String answer = stripThinkTags(chatResponse.aiMessage().text());
        if (answer == null) answer = "未能生成回答。";

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            sources.add(toSourceInfo(i + 1, results.get(i)));
        }

        return new RagAnswer(answer, sources, refs.stream()
                .map(r -> r.text().length() > 80 ? r.text().substring(0, 80) + "..." : r.text())
                .toList());
    }

    /**
     * 博客专属流式上下文
     */
    public StreamContext prepareBlogStreamContext(String question, List<HistoryEntry> history, String agentPrompt) {
        return prepareBlogStreamContext(question, history, agentPrompt, null);
    }

    /**
     * 博客专属流式上下文（可限定到特定文章）
     */
    public StreamContext prepareBlogStreamContext(String question, List<HistoryEntry> history, String agentPrompt, String slug) {
        String sourcePrefix = slug != null && !slug.isBlank() ? "blog:" + slug : "blog:";
        List<SearchResult> results = searcher.search(question, sourcePrefix);

        List<Reference> refs = results.stream()
                .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                .toList();

        String systemContent = RagPromptTemplate.buildSystemContext(agentPrompt, refs);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemContent));

        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            if ("user".equals(entry.role())) {
                messages.add(UserMessage.from(entry.content()));
            } else if ("assistant".equals(entry.role())) {
                messages.add(AiMessage.from(entry.content()));
            }
        }
        messages.add(UserMessage.from(question));

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            sources.add(toSourceInfo(i + 1, results.get(i)));
        }

        return new StreamContext(messages, sources);
    }

    // ===== LLM Output Utilities =====

    /**
     * 去除 LLM 输出中的 <think</think\> 思考过程标签
     * 兼容 DeepSeek 等支持思考模式的模型
     */
    public static String stripThinkTags(String text) {
        if (text == null) return null;
        return text.replaceAll("(?s)<think\\s*>.*?</\\s*think\\s*>", "").trim();
    }

    // ===== AiServices Helpers =====

    /**
     * 构建 AiServices 助手实例（per-request，携带 ChatMemory + tools）
     * System message 已在 buildChatMemory 中作为首条消息加入，避免某些模型不兼容 .systemMessage()
     */
    private RagAssistant buildAssistant(MessageWindowChatMemory memory) {
        var builder = AiServices.builder(RagAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(memory)
                .maxSequentialToolsInvocations(10);
        if (toolObjects.length > 0) {
            builder.tools(toolObjects);
        }
        return builder.build();
    }

    /**
     * 从历史记录构建 ChatMemory，system message 作为首条消息插入。
     * 使用 alwaysKeepSystemMessageFirst 确保 system message 不会被窗口淘汰。
     */
    private MessageWindowChatMemory buildChatMemory(List<HistoryEntry> history, String systemContent) {
        var memory = MessageWindowChatMemory.builder()
                .maxMessages(50)
                .alwaysKeepSystemMessageFirst(true)
                .build();
        if (systemContent != null && !systemContent.isBlank()) {
            memory.add(SystemMessage.from(systemContent));
        }
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            if ("user".equals(entry.role())) {
                memory.add(UserMessage.from(entry.content()));
            } else if ("assistant".equals(entry.role())) {
                memory.add(AiMessage.from(entry.content()));
            }
        }
        return memory;
    }

    /**
     * 构建完整的 system prompt（RAG 上下文 + 记忆 + Web 搜索回退）
     */
    private String buildSystemContent(String question, String agentPrompt, List<Reference> refs, List<SearchResult> results) {
        String systemContent = buildContextWithMemories(question, agentPrompt, refs);

        if (results.isEmpty() && webSearcher != null && webSearcher.isConfigured()) {
            try {
                String webResults = webSearcher.search(question);
                if (webResults != null && !webResults.isBlank()) {
                    systemContent += "\n\n【互联网搜索结果】\n" + webResults
                            + "\n\n注意：以上内容来自互联网搜索，请根据这些信息回答用户问题，并注明信息来源。";
                }
            } catch (Exception ignored) {}
        }

        return systemContent;
    }

    // Agentic streaming context: TokenStream + sources
    public record AgenticStreamContext(TokenStream tokenStream, List<SourceInfo> sources) {}

    private String buildContextWithMemories(String question, String agentPrompt, List<Reference> refs) {
        String base = RagPromptTemplate.buildSystemContext(agentPrompt, refs);

        if (memoryStore == null || memoryStore.getMemoryCount() == 0) return base;

        List<String> recalled = recallMemories(question, 3);
        if (recalled.isEmpty()) return base;

        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n【历史记忆】\n");
        for (String mem : recalled) {
            String clean = mem.startsWith("[记忆摘要] ") ? mem.substring("[记忆摘要] ".length()) : mem;
            sb.append("- ").append(clean).append("\n");
        }
        return sb.toString();
    }

    // ===== Stats =====

    public KnowledgeStats getStats() {
        return new KnowledgeStats(documentCount, segmentCount,
                config.getLlm().provider, config.getLlm().modelName,
                config.getEmbedding().provider, config.getEmbedding().modelName,
                config.getVectorStore().type);
    }

    public HybridSearcher getSearcher() {
        return searcher;
    }

    public MemoryStore getMemoryStore() {
        return memoryStore;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    // ===== Records =====

    private static SourceInfo toSourceInfo(int index, SearchResult r) {
        String truncated = r.text().length() > 120 ? r.text().substring(0, 120) + "..." : r.text();
        return new SourceInfo(index, truncated, r.source(), r.rrfScore(), r.vectorScore(),
                r.breadcrumb(), r.confidence(), r.confidenceLabel(), r.explanation());
    }

    public record RagAnswer(String answer, List<SourceInfo> sources, List<String> references) {}
    public record SourceInfo(int index, String text, String source, double rrfScore, double vectorScore,
                             String breadcrumb, double confidence, String confidenceLabel, String explanation) {}
    public record HistoryEntry(String role, String content) {}
    public record StreamContext(List<ChatMessage> messages, List<SourceInfo> sources) {}
    public record KnowledgeStats(int documentCount, int segmentCount,
                                  String llmProvider, String llmModel,
                                  String embeddingProvider, String embeddingModel,
                                  String vectorStoreType) {}
}
