package com.example.rag.service;

import com.example.rag.config.AppConfiguration;
import com.example.rag.config.ModelFactory;
import com.example.rag.config.VectorStoreFactory;
import com.example.rag.parser.AutoDocumentParser;
import com.example.rag.parser.DocumentMetaStore;
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
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.StreamingResponseHandler;
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

    private volatile ChatLanguageModel chatModel;
    private volatile StreamingChatLanguageModel streamingChatModel;
    private volatile EmbeddingModel embeddingModel;
    private volatile EmbeddingStore<TextSegment> store;
    private volatile HybridSearcher searcher;
    private volatile DocumentSplitter splitter;

    // Dedicated lock for thread-unsafe store/searcher writes (NOT 'this')
    private final Object indexLock = new Object();

    private final AppConfiguration config;
    private final DocumentMetaStore metaStore;
    private int documentCount = 0;
    private int segmentCount = 0;

    public RagService(AppConfiguration config) {
        this.config = config;
        this.metaStore = new DocumentMetaStore("knowledge");
        rebuildModels();
    }

    /**
     * 根据配置重建所有模型实例
     */
    public synchronized void rebuildModels() {
        this.chatModel = ModelFactory.createChatModel(config.getLlm());
        this.streamingChatModel = ModelFactory.createStreamingChatModel(config.getLlm());
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
            String typeName = metaStore.getTypeName(source);
            DocumentSplitter typeSplitter = createSplitter(typeName);
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
        String answer = chatModel.generate(prompt);

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sources.add(new SourceInfo(
                    i + 1,
                    r.text().length() > 120 ? r.text().substring(0, 120) + "..." : r.text(),
                    r.source(),
                    r.rrfScore(),
                    r.vectorScore()
            ));
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
     */
    public RagAnswer askWithContext(String question, List<HistoryEntry> history, String agentPrompt) {
        List<SearchResult> results = searcher.search(question);

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

        Response<AiMessage> response = chatModel.generate(messages);
        String answer = response.content().text();
        if (answer == null) {
            answer = "未能生成回答。";
        }

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sources.add(new SourceInfo(
                    i + 1,
                    r.text().length() > 120 ? r.text().substring(0, 120) + "..." : r.text(),
                    r.source(),
                    r.rrfScore(),
                    r.vectorScore()
            ));
        }

        return new RagAnswer(answer, sources, refs.stream()
                .map(r -> r.text().length() > 80 ? r.text().substring(0, 80) + "..." : r.text())
                .toList());
    }

    /**
     * 准备流式上下文：执行 RAG 检索、构建消息列表，但不调用 LLM
     */
    public StreamContext prepareStreamContext(String question, List<HistoryEntry> history) {
        return prepareStreamContext(question, history, null);
    }

    /**
     * 准备流式上下文（带自定义智能体提示词）
     */
    public StreamContext prepareStreamContext(String question, List<HistoryEntry> history, String agentPrompt) {
        List<SearchResult> results = searcher.search(question);

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
            SearchResult r = results.get(i);
            sources.add(new SourceInfo(
                    i + 1,
                    r.text().length() > 120 ? r.text().substring(0, 120) + "..." : r.text(),
                    r.source(),
                    r.rrfScore(),
                    r.vectorScore()
            ));
        }

        return new StreamContext(messages, sources);
    }

    /**
     * 流式生成：将消息列表发送给 StreamingChatModel，通过回调逐 token 输出
     */
    public void streamGenerate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        streamingChatModel.generate(messages, handler);
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
        return chatModel.generate(prompt.toString());
    }

    /**
     * 将摘要向量化存储到知识库，作为长期记忆
     * 后续 RAG 检索时会自动检索到这些记忆片段
     */
    public void storeMemory(String conversationId, String summary) {
        Metadata meta = new Metadata();
        meta.add("source", "memory:" + conversationId);
        meta.add("type", "memory");
        TextSegment segment = TextSegment.from("[记忆摘要] " + summary, meta);
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        String id = store.add(embedding, segment);
        searcher.indexSegment(id, segment.text(), "memory:" + conversationId);
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
        AppConfiguration.DocumentTypeConfig cfg = config.findDocTypeConfig(typeName);
        return DocumentSplitters.recursive(cfg.chunkSize, cfg.chunkOverlap);
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
    }

    // ===== Blog Article Indexing =====

    /**
     * 索引博客文章到向量库
     */
    public synchronized void indexBlogArticle(String articleId, String slug, String title, String content, String category) {
        String source = "blog:" + slug;
        Metadata meta = new Metadata();
        meta.add("source", source);
        meta.add("type", "blog");
        meta.add("articleId", articleId);
        meta.add("title", title);
        meta.add("category", category != null ? category : "");

        TextSegment segment = TextSegment.from(title + "\n\n" + content, meta);
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        String id;
        synchronized (indexLock) {
            id = store.add(embedding, segment);
            searcher.indexSegment(id, segment.text(), source);
            segmentCount++;
        }
        System.out.println("[BLOG-INDEX] Indexed article: " + slug + " (segment " + id + ")");
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
     * 博客专属 RAG 问答（限定 blog: 来源）
     */
    public RagAnswer askBlog(String question, List<HistoryEntry> history, String agentPrompt) {
        List<SearchResult> results = searcher.search(question, "blog:");

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

        Response<AiMessage> response = chatModel.generate(messages);
        String answer = response.content().text();
        if (answer == null) answer = "未能生成回答。";

        List<SourceInfo> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sources.add(new SourceInfo(i + 1,
                    r.text().length() > 120 ? r.text().substring(0, 120) + "..." : r.text(),
                    r.source(), r.rrfScore(), r.vectorScore()));
        }

        return new RagAnswer(answer, sources, refs.stream()
                .map(r -> r.text().length() > 80 ? r.text().substring(0, 80) + "..." : r.text())
                .toList());
    }

    /**
     * 博客专属流式上下文
     */
    public StreamContext prepareBlogStreamContext(String question, List<HistoryEntry> history, String agentPrompt) {
        List<SearchResult> results = searcher.search(question, "blog:");

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
            SearchResult r = results.get(i);
            sources.add(new SourceInfo(i + 1,
                    r.text().length() > 120 ? r.text().substring(0, 120) + "..." : r.text(),
                    r.source(), r.rrfScore(), r.vectorScore()));
        }

        return new StreamContext(messages, sources);
    }

    // ===== Stats =====

    public KnowledgeStats getStats() {
        return new KnowledgeStats(documentCount, segmentCount,
                config.getLlm().provider, config.getLlm().modelName,
                config.getEmbedding().provider, config.getEmbedding().modelName,
                config.getVectorStore().type);
    }

    // ===== Records =====

    public record RagAnswer(String answer, List<SourceInfo> sources, List<String> references) {}
    public record SourceInfo(int index, String text, String source, double rrfScore, double vectorScore) {}
    public record HistoryEntry(String role, String content) {}
    public record StreamContext(List<ChatMessage> messages, List<SourceInfo> sources) {}
    public record KnowledgeStats(int documentCount, int segmentCount,
                                  String llmProvider, String llmModel,
                                  String embeddingProvider, String embeddingModel,
                                  String vectorStoreType) {}
}
