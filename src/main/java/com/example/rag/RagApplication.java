package com.example.rag;

import com.example.rag.a2a.AgentCard;
import com.example.rag.a2a.Task;
import com.example.rag.a2a.TaskManager;
import com.example.rag.blog.AuthFilter;
import com.example.rag.blog.BlogStore;
import com.example.rag.chat.AgentStore;
import com.example.rag.chat.ChatStore;
import com.example.rag.config.AppConfiguration;
import com.example.rag.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 文枢 (WenShu) - RAG 知识库智能问答系统
 * 基于 LangChain4j + Javalin，支持 A2A 协议的 Agent 节点
 */
public class RagApplication {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private static AppConfiguration config;
    private static RagService ragService;
    private static TaskManager taskManager;
    private static AgentCard agentCard;
    private static ChatStore chatStore;
    private static AgentStore agentStore;
    private static BlogStore blogStore;
    private static AuthFilter authFilter;

    public static void main(String[] args) {
        // 1. Load config
        config = AppConfiguration.load();
        System.out.println("[INIT] Configuration loaded");

        // 2. Initialize RAG service
        ragService = new RagService(config);
        System.out.println("[INIT] Models initialized: LLM=" + config.getLlm().modelName
                + ", Embedding=" + config.getEmbedding().modelName);

        // 3. Index knowledge base
        try {
            ragService.indexKnowledgeBase("knowledge/");
            RagService.KnowledgeStats stats = ragService.getStats();
            System.out.println("[INIT] Knowledge base indexed: " + stats.documentCount()
                    + " docs, " + stats.segmentCount() + " segments");
        } catch (Exception e) {
            System.err.println("[WARN] Knowledge base indexing failed: " + e.getMessage());
            System.err.println("[WARN] You can index later via Settings page or check your embedding model config");
        }

        // 4. A2A setup
        taskManager = new TaskManager();
        chatStore = new ChatStore();
        agentStore = new AgentStore();
        blogStore = new BlogStore();
        authFilter = new AuthFilter(config.getBlog().adminPassword);
        int port = config.getServer().port;

        agentCard = AgentCard.create(
                config.getA2a().agentName,
                config.getA2a().agentDescription,
                "http://localhost:" + port + "/a2a/v1"
        );

        // 5. Start Javalin server
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
            javalinConfig.showJavalinBanner = false;
        });

        registerRoutes(app);

        app.start(port);
        System.out.println("========================================");
        System.out.println("  文枢 · 藏书阁  WenShu v1.0.0");
        System.out.println("  http://localhost:" + port);
        System.out.println("  A2A endpoint: http://localhost:" + port + "/a2a/v1");
        System.out.println("========================================");
    }

    private static void registerRoutes(Javalin app) {

        // ===== Health Check =====
        app.get("/api/health", ctx -> {
            RagService.KnowledgeStats stats = ragService.getStats();
            ctx.json(Map.of(
                    "status", "running",
                    "documents", stats.documentCount(),
                    "segments", stats.segmentCount(),
                    "llm", stats.llmModel(),
                    "embedding", stats.embeddingModel()
            ));
        });

        // ===== Chat API =====
        app.post("/api/chat", RagApplication::handleChat);
        app.post("/api/chat/stream", RagApplication::handleChatStream);

        // ===== Settings API =====
        app.get("/api/settings", ctx -> ctx.json(config));

        app.post("/api/settings", ctx -> {
            String body = ctx.body();
            AppConfiguration newConfig = MAPPER.readValue(body, AppConfiguration.class);
            if (newConfig == null) {
                ctx.status(400).json(Map.of("error", "Invalid config"));
                return;
            }

            if (newConfig.getLlm() != null) {
                config.getLlm().baseUrl = nvl(newConfig.getLlm().baseUrl, config.getLlm().baseUrl);
                config.getLlm().apiKey = nvl(newConfig.getLlm().apiKey, config.getLlm().apiKey);
                config.getLlm().modelName = nvl(newConfig.getLlm().modelName, config.getLlm().modelName);
                config.getLlm().provider = nvl(newConfig.getLlm().provider, config.getLlm().provider);
                if (newConfig.getLlm().temperature != null) config.getLlm().temperature = newConfig.getLlm().temperature;
                if (newConfig.getLlm().maxTokens != null) config.getLlm().maxTokens = newConfig.getLlm().maxTokens;
                config.getLlm().streaming = newConfig.getLlm().streaming;
            }

            if (newConfig.getEmbedding() != null) {
                config.getEmbedding().baseUrl = nvl(newConfig.getEmbedding().baseUrl, config.getEmbedding().baseUrl);
                config.getEmbedding().apiKey = nvl(newConfig.getEmbedding().apiKey, config.getEmbedding().apiKey);
                config.getEmbedding().modelName = nvl(newConfig.getEmbedding().modelName, config.getEmbedding().modelName);
                config.getEmbedding().provider = nvl(newConfig.getEmbedding().provider, config.getEmbedding().provider);
            }

            if (newConfig.getVectorStore() != null) {
                var vs = newConfig.getVectorStore();
                var cur = config.getVectorStore();
                cur.type = nvl(vs.type, cur.type);
                cur.chromaBaseUrl = nvl(vs.chromaBaseUrl, cur.chromaBaseUrl);
                cur.collectionName = nvl(vs.collectionName, cur.collectionName);
                cur.milvusHost = nvl(vs.milvusHost, cur.milvusHost);
                if (vs.milvusPort > 0) cur.milvusPort = vs.milvusPort;
                if (vs.embeddingDimension > 0) cur.embeddingDimension = vs.embeddingDimension;
            }

            if (newConfig.getRag() != null) {
                if (newConfig.getRag().chunkSize > 0) config.getRag().chunkSize = newConfig.getRag().chunkSize;
                if (newConfig.getRag().chunkOverlap >= 0) config.getRag().chunkOverlap = newConfig.getRag().chunkOverlap;
                if (newConfig.getRag().vectorTopK > 0) config.getRag().vectorTopK = newConfig.getRag().vectorTopK;
                if (newConfig.getRag().keywordTopK > 0) config.getRag().keywordTopK = newConfig.getRag().keywordTopK;
                if (newConfig.getRag().rrfK > 0) config.getRag().rrfK = newConfig.getRag().rrfK;
            }

            if (newConfig.getDocumentTypes() != null && !newConfig.getDocumentTypes().isEmpty()) {
                config.setDocumentTypes(newConfig.getDocumentTypes());
            }

            config.save();
            ragService.rebuildModels();
            ctx.json(Map.of("status", "ok", "message", "Settings saved and models rebuilt"));
        });

        app.post("/api/settings/reindex", ctx -> {
            try {
                ragService.rebuildModels();
                ragService.indexKnowledgeBase("knowledge/");
                RagService.KnowledgeStats stats = ragService.getStats();
                ctx.json(Map.of("status", "ok", "documents", stats.documentCount(),
                        "segments", stats.segmentCount()));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Reindex failed: " + e.getMessage()));
            }
        });

        // ===== Knowledge Base Stats API =====
        app.get("/api/knowledge/stats", ctx -> ctx.json(ragService.getStats()));

        // ===== Document Management API =====
        app.get("/api/documents/types", ctx -> {
            List<Map<String, Object>> types = new ArrayList<>();
            for (var dtc : config.getDocumentTypes()) {
                types.add(Map.of(
                        "name", dtc.name,
                        "label", dtc.label,
                        "chunkSize", dtc.chunkSize,
                        "chunkOverlap", dtc.chunkOverlap
                ));
            }
            ctx.json(types);
        });
        app.get("/api/documents", RagApplication::handleListDocuments);
        app.post("/api/documents/upload", ctx -> {
            ctx.contentType("multipart/form-data");
            handleDocumentUpload(ctx);
        });
        app.delete("/api/documents/{filename}", RagApplication::handleDocumentDelete);

        // ===== Agent API =====
        app.get("/api/agents", ctx -> ctx.json(agentStore.list()));

        app.post("/api/agents", ctx -> {
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            String name = body.get("name");
            String description = body.getOrDefault("description", "");
            String systemPrompt = body.get("systemPrompt");
            String avatar = body.getOrDefault("avatar", "");
            if (name == null || name.isBlank() || systemPrompt == null || systemPrompt.isBlank()) {
                ctx.status(400).json(Map.of("error", "name and systemPrompt are required"));
                return;
            }
            AgentStore.Agent agent = agentStore.create(name, description, systemPrompt, avatar);
            ctx.json(agent);
        });

        app.put("/api/agents/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            String name = body.get("name");
            String description = body.getOrDefault("description", "");
            String systemPrompt = body.get("systemPrompt");
            String avatar = body.getOrDefault("avatar", "");
            if (name == null || name.isBlank() || systemPrompt == null || systemPrompt.isBlank()) {
                ctx.status(400).json(Map.of("error", "name and systemPrompt are required"));
                return;
            }
            try {
                AgentStore.Agent agent = agentStore.update(id, name, description, systemPrompt, avatar);
                ctx.json(agent);
            } catch (RuntimeException e) {
                ctx.status(404).json(Map.of("error", e.getMessage()));
            }
        });

        app.delete("/api/agents/{id}", ctx -> {
            String id = ctx.pathParam("id");
            try {
                agentStore.delete(id);
                ctx.json(Map.of("status", "ok"));
            } catch (RuntimeException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        // ===== Conversation API =====
        app.post("/api/conversations", ctx -> {
            String agentId = null;
            try {
                Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
                agentId = body.get("agentId");
            } catch (Exception ignored) {}
            ChatStore.Conversation conv = chatStore.createConversation(agentId);
            ctx.json(conv);
        });

        app.get("/api/conversations", ctx -> ctx.json(chatStore.listConversations()));

        app.get("/api/conversations/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ChatStore.Conversation conv = chatStore.getConversation(id);
            if (conv == null) {
                ctx.status(404).json(Map.of("error", "Conversation not found"));
                return;
            }
            List<ChatStore.Message> messages = chatStore.listMessages(id);
            ctx.json(Map.of("conversation", conv, "messages", messages));
        });

        app.put("/api/conversations/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            String title = body.get("title");
            if (title != null && !title.isBlank()) {
                chatStore.updateTitle(id, title);
            }
            ctx.json(Map.of("status", "ok"));
        });

        app.delete("/api/conversations/{id}", ctx -> {
            String id = ctx.pathParam("id");
            chatStore.deleteConversation(id);
            ctx.json(Map.of("status", "ok"));
        });

        // ===== A2A Protocol Endpoints =====
        app.get("/a2a/v1/agent", ctx -> ctx.json(agentCard));

        app.post("/a2a/v1/rpc", ctx -> {
            Map<String, Object> request = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String method = (String) request.get("method");
            String id = (String) request.get("id");

            try {
                Object result = switch (method) {
                    case "tasks/send" -> handleA2ATaskSend(request);
                    case "tasks/get" -> handleA2ATaskGet(request);
                    case "tasks/cancel" -> handleA2ATaskCancel(request);
                    default -> Map.of("error", "Method not found: " + method);
                };
                ctx.json(Map.of("jsonrpc", "2.0", "id", id, "result", result));
            } catch (Exception e) {
                ctx.json(Map.of("jsonrpc", "2.0", "id", id,
                        "error", Map.of("code", -32603, "message", e.getMessage())));
            }
        });

        app.get("/a2a/v1/tasks", ctx -> {
            List<Task> tasks = taskManager.listRecent(20);
            ctx.json(tasks);
        });

        // ===== Blog Public Routes =====
        registerBlogRoutes(app);
    }

    private static void registerBlogRoutes(Javalin app) {

        // --- Public: Blog Posts ---
        app.get("/api/blog/posts", ctx -> {
            int page = Integer.parseInt(ctx.queryParam("page") != null ? ctx.queryParam("page") : "1");
            int size = Integer.parseInt(ctx.queryParam("size") != null ? ctx.queryParam("size") : String.valueOf(config.getBlog().postsPerPage));
            String category = ctx.queryParam("category");
            String tag = ctx.queryParam("tag");
            ctx.json(blogStore.listPublished(page, size, category, tag));
        });

        app.get("/api/blog/posts/{slug}", ctx -> {
            String slug = ctx.pathParam("slug");
            BlogStore.Article article = blogStore.getBySlug(slug);
            if (article == null) {
                ctx.status(404).json(Map.of("error", "Article not found"));
                return;
            }
            blogStore.incrementViewCount(article.id());
            ctx.json(article);
        });

        app.get("/api/blog/categories", ctx -> ctx.json(blogStore.listCategories()));

        app.get("/api/blog/tags", ctx -> ctx.json(blogStore.getAllTags()));

        app.get("/api/blog/search", ctx -> {
            String q = ctx.queryParam("q");
            if (q == null || q.isBlank()) {
                ctx.json(List.of());
                return;
            }
            ctx.json(blogStore.searchArticles(q));
        });

        app.get("/api/blog/stats", ctx -> {
            ctx.json(Map.of(
                    "totalArticles", blogStore.countPublished(),
                    "totalCategories", blogStore.listCategories().size(),
                    "totalTags", blogStore.getAllTags().size(),
                    "blogTitle", config.getBlog().title,
                    "blogDescription", config.getBlog().description
            ));
        });

        // --- Admin: Login ---
        app.post("/api/admin/login", ctx -> {
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            String password = body.get("password");
            if (password != null && password.equals(config.getBlog().adminPassword)) {
                String token = AuthFilter.generateToken(password);
                ctx.json(Map.of("token", token, "status", "ok"));
            } else {
                ctx.status(401).json(Map.of("error", "Invalid password"));
            }
        });

        // --- Admin: All routes below require auth ---
        String adminPath = "/api/admin";
        app.before(adminPath + "/*", ctx -> {
            String path = ctx.path();
            if (path.equals(adminPath + "/login")) return;
            authFilter.handle(ctx);
        });

        app.get("/api/admin/posts", ctx -> {
            int page = Integer.parseInt(ctx.queryParam("page") != null ? ctx.queryParam("page") : "1");
            int size = Integer.parseInt(ctx.queryParam("size") != null ? ctx.queryParam("size") : "20");
            ctx.json(blogStore.listAll(page, size));
        });

        app.post("/api/admin/posts", ctx -> {
            Map<String, Object> body = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String title = (String) body.get("title");
            String content = (String) body.get("content");
            String contentType = (String) body.getOrDefault("contentType", "md");
            String category = (String) body.get("category");
            List<String> tags = body.get("tags") instanceof List ? (List<String>) body.get("tags") : List.of();
            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "title is required"));
                return;
            }
            BlogStore.Article article = blogStore.createArticle(title, content != null ? content : "", contentType, category, tags);
            ctx.json(article);
        });

        app.put("/api/admin/posts/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, Object> body = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String title = (String) body.get("title");
            String content = (String) body.get("content");
            String category = (String) body.get("category");
            List<String> tags = body.get("tags") instanceof List ? (List<String>) body.get("tags") : List.of();
            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "title is required"));
                return;
            }
            BlogStore.Article article = blogStore.updateArticle(id, title, content != null ? content : "", category, tags);
            ctx.json(article);
        });

        app.delete("/api/admin/posts/{id}", ctx -> {
            blogStore.deleteArticle(ctx.pathParam("id"));
            ctx.json(Map.of("status", "ok"));
        });

        app.post("/api/admin/posts/{id}/publish", ctx -> {
            BlogStore.Article article = blogStore.publishArticle(ctx.pathParam("id"));
            ctx.json(article);
        });

        app.post("/api/admin/posts/{id}/unpublish", ctx -> {
            BlogStore.Article article = blogStore.unpublishArticle(ctx.pathParam("id"));
            ctx.json(article);
        });

        app.post("/api/admin/categories", ctx -> {
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            String name = body.get("name");
            String slug = body.get("slug");
            String description = body.getOrDefault("description", "");
            if (name == null || name.isBlank() || slug == null || slug.isBlank()) {
                ctx.status(400).json(Map.of("error", "name and slug are required"));
                return;
            }
            ctx.json(blogStore.createCategory(name, slug, description));
        });

        app.put("/api/admin/categories/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            ctx.json(blogStore.updateCategory(id, body.get("name"), body.get("slug"), body.getOrDefault("description", "")));
        });

        app.delete("/api/admin/categories/{id}", ctx -> {
            blogStore.deleteCategory(ctx.pathParam("id"));
            ctx.json(Map.of("status", "ok"));
        });
    }

    // ===== Chat Handler =====

    private static void handleChat(Context ctx) throws Exception {
        Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
        String question = body.get("question");
        String conversationId = body.get("conversationId");
        String agentId = body.get("agentId");

        if (question == null || question.isBlank()) {
            ctx.status(400).json(Map.of("error", "question is required"));
            return;
        }

        // Auto-create conversation if not provided
        if (conversationId == null || conversationId.isBlank()) {
            ChatStore.Conversation conv = chatStore.createConversation(agentId);
            conversationId = conv.id();
        }

        try {
            // Load conversation history (before saving current question)
            List<ChatStore.Message> historyMessages = chatStore.listMessages(conversationId);
            List<RagService.HistoryEntry> history = historyMessages.stream()
                    .map(m -> new RagService.HistoryEntry(m.role(), m.content()))
                    .toList();

            // Get RAG answer with conversation context
            String agentPrompt = resolveAgentPrompt(agentId);
            RagService.RagAnswer answer = ragService.askWithContext(question, history, agentPrompt);

            // Save user message + assistant message
            chatStore.saveMessage(conversationId, "user", question, null);

            List<ChatStore.MessageSource> msgSources = answer.sources().stream()
                    .map(s -> new ChatStore.MessageSource(s.index(), s.text(), s.source(), s.rrfScore(), s.vectorScore()))
                    .toList();
            chatStore.saveMessage(conversationId, "assistant", answer.answer(), msgSources);
            chatStore.touchConversation(conversationId);

            // Auto-update conversation title from first message
            ChatStore.Conversation conv = chatStore.getConversation(conversationId);
            if (conv != null && "新对话".equals(conv.title())) {
                String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
                chatStore.updateTitle(conversationId, title);
            }

            // Long-term memory: periodically summarize and store in vector store
            List<RagService.HistoryEntry> fullHistory = new ArrayList<>(history);
            fullHistory.add(new RagService.HistoryEntry("user", question));
            fullHistory.add(new RagService.HistoryEntry("assistant", answer.answer()));

            if (fullHistory.size() >= 6 && fullHistory.size() % 6 == 0) {
                try {
                    String summary = ragService.summarizeConversation(fullHistory);
                    if (summary != null && !summary.isBlank()) {
                        ragService.storeMemory(conversationId, summary);
                    }
                } catch (Exception ignored) {
                    // Memory generation failure should not affect the response
                }
            }

            // Return response with conversationId
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("answer", answer.answer());
            response.put("sources", answer.sources());
            response.put("references", answer.references());
            response.put("conversationId", conversationId);
            ctx.json(response);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Failed to process question: " + e.getMessage()));
        }
    }

    // ===== Streaming Chat Handler (SSE) =====

    private static void handleChatStream(Context ctx) throws Exception {
        Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
        String question = body.get("question");
        String conversationId = body.get("conversationId");
        String agentId = body.get("agentId");

        if (question == null || question.isBlank()) {
            ctx.status(400).json(Map.of("error", "question is required"));
            return;
        }

        // Auto-create conversation
        if (conversationId == null || conversationId.isBlank()) {
            ChatStore.Conversation conv = chatStore.createConversation(agentId);
            conversationId = conv.id();
        }

        // Load history
        List<ChatStore.Message> historyMessages = chatStore.listMessages(conversationId);
        List<RagService.HistoryEntry> history = historyMessages.stream()
                .map(m -> new RagService.HistoryEntry(m.role(), m.content()))
                .toList();

        // Prepare RAG context (retrieval only, no LLM call)
        String agentPrompt = resolveAgentPrompt(agentId);
        RagService.StreamContext streamCtx = ragService.prepareStreamContext(question, history, agentPrompt);

        // Set SSE response headers (charset=UTF-8 is critical for Chinese characters)
        ctx.res().setContentType("text/event-stream; charset=UTF-8");
        ctx.res().setHeader("Cache-Control", "no-cache");
        ctx.res().setHeader("Connection", "keep-alive");
        ctx.res().setHeader("X-Accel-Buffering", "no");

        var writer = ctx.res().getWriter();
        String finalConvId = conversationId;

        // Send meta event with conversationId
        writer.write("event: meta\ndata: " + MAPPER.writeValueAsString(
                Map.of("conversationId", finalConvId)) + "\n\n");
        writer.flush();

        // Send sources event
        String sourcesJson = MAPPER.writeValueAsString(streamCtx.sources());
        writer.write("event: sources\ndata: " + sourcesJson + "\n\n");
        writer.flush();

        // Stream LLM tokens
        StringBuilder fullAnswer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();

        ragService.streamGenerate(streamCtx.messages(), new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                fullAnswer.append(token);
                try {
                    writer.write("event: token\ndata: " + MAPPER.writeValueAsString(
                            Map.of("t", token)) + "\n\n");
                    writer.flush();
                } catch (Exception ignored) {
                    // Client disconnected
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    // Send done event IMMEDIATELY — front-end needs this to show sources & re-enable input
                    Map<String, Object> donePayload = new LinkedHashMap<>();
                    donePayload.put("answer", fullAnswer.toString());
                    donePayload.put("conversationId", finalConvId);
                    donePayload.put("sources", streamCtx.sources());
                    writer.write("event: done\ndata: " + MAPPER.writeValueAsString(donePayload) + "\n\n");
                    writer.flush();
                } catch (Exception e) {
                    errorRef.set(e.getMessage());
                } finally {
                    // Release Javalin thread FIRST so the connection can close promptly
                    latch.countDown();
                }

                // --- All slow work below runs AFTER latch release ---
                // The HTTP connection may close at any point, but we no longer write to it.

                // Save messages to DB (failure only loses history, not UX)
                try {
                    saveStreamMessages(finalConvId, question, fullAnswer.toString(), streamCtx);
                } catch (Exception e) {
                    System.err.println("[WARN] Failed to save stream messages: " + e.getMessage());
                }

                // Long-term memory: run async to avoid blocking anything
                String capturedAnswer = fullAnswer.toString();
                CompletableFuture.runAsync(() -> {
                    try {
                        List<RagService.HistoryEntry> fullHistory = new ArrayList<>(history);
                        fullHistory.add(new RagService.HistoryEntry("user", question));
                        fullHistory.add(new RagService.HistoryEntry("assistant", capturedAnswer));
                        if (fullHistory.size() >= 6 && fullHistory.size() % 6 == 0) {
                            String summary = ragService.summarizeConversation(fullHistory);
                            if (summary != null && !summary.isBlank()) {
                                ragService.storeMemory(finalConvId, summary);
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }

            @Override
            public void onError(Throwable error) {
                try {
                    String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    writer.write("event: error\ndata: " + MAPPER.writeValueAsString(
                            Map.of("error", msg)) + "\n\n");
                    writer.flush();
                } catch (Exception ignored) {}
                errorRef.set(error.getMessage());
                latch.countDown();

                // Save partial messages AFTER releasing latch (user input is preserved)
                try {
                    saveStreamMessages(finalConvId, question, fullAnswer.toString(), streamCtx);
                } catch (Exception e) {
                    System.err.println("[WARN] Failed to save partial messages: " + e.getMessage());
                }
            }
        });

        // Block until streaming completes (with timeout)
        boolean completed = latch.await(120, java.util.concurrent.TimeUnit.SECONDS);

        // Timeout fallback: save whatever we have
        if (!completed && fullAnswer.length() > 0) {
            try {
                saveStreamMessages(finalConvId, question, fullAnswer.toString(), streamCtx);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Shared helper: save user + assistant messages and update conversation metadata.
     * Uses a single connection + transaction to avoid 5 separate pool borrow/return cycles.
     */
    private static void saveStreamMessages(String convId, String question,
                                           String answer, RagService.StreamContext streamCtx) {
        List<ChatStore.MessageSource> msgSources = streamCtx.sources().stream()
                .map(s -> new ChatStore.MessageSource(
                        s.index(), s.text(), s.source(), s.rrfScore(), s.vectorScore()))
                .toList();
        try {
            chatStore.saveMessagesInTransaction(convId, question, answer, msgSources);
        } catch (Exception e) {
            System.err.println("[WARN] saveMessagesInTransaction failed, falling back: " + e.getMessage());
            chatStore.saveMessage(convId, "user", question, null);
            chatStore.saveMessage(convId, "assistant", answer, msgSources);
            chatStore.touchConversation(convId);

            ChatStore.Conversation conv = chatStore.getConversation(convId);
            if (conv != null && "新对话".equals(conv.title())) {
                String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
                chatStore.updateTitle(convId, title);
            }
        }
    }

    /**
     * Resolve agent prompt: lookup agent by id, fallback to default agent's prompt, then hardcoded default.
     */
    private static String resolveAgentPrompt(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            AgentStore.Agent agent = agentStore.getById(agentId);
            if (agent != null) return agent.systemPrompt();
        }
        AgentStore.Agent def = agentStore.getById("default");
        if (def != null) return def.systemPrompt();
        return null; // RagPromptTemplate will use its own hardcoded default
    }

    // ===== A2A Handlers =====

    private static Object handleA2ATaskSend(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String skillId = (String) params.get("skillId");
        String taskId = params.containsKey("id") ? String.valueOf(params.get("id")) : "task-" + UUID.randomUUID();

        Task task = taskManager.create(taskId, skillId);

        if (!"rag-query".equals(skillId)) {
            task.fail("Unknown skill: " + skillId);
            return taskResult(task);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) params.get("input");
            String question = input != null ? (String) input.get("question") : "";
            if (question == null || question.isBlank()) {
                task.fail("question is required in input");
                return taskResult(task);
            }

            RagService.RagAnswer ragAnswer = ragService.ask(question);
            List<Map<String, Object>> taskSources = new ArrayList<>();
            for (RagService.SourceInfo s : ragAnswer.sources()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("index", s.index());
                m.put("text", s.text());
                m.put("source", s.source());
                m.put("rrfScore", s.rrfScore());
                m.put("vectorScore", s.vectorScore());
                taskSources.add(m);
            }
            task.completeWithDetails(ragAnswer.answer(), question, taskSources);
        } catch (Exception e) {
            task.fail(e.getMessage());
        }

        return taskResult(task);
    }

    private static Object handleA2ATaskGet(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String taskId = (String) params.get("taskId");
        Task task = taskManager.get(taskId);
        if (task == null) {
            return Map.of("error", "Task not found: " + taskId);
        }
        return taskResult(task);
    }

    private static Object handleA2ATaskCancel(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String taskId = (String) params.get("taskId");
        Task task = taskManager.get(taskId);
        if (task != null) {
            task.fail("Canceled by caller");
        }
        return taskResult(task);
    }

    private static Map<String, Object> taskResult(Task task) {
        Map<String, Object> status = Map.of("state", task.getState().name().toLowerCase());

        if (task.getState() == Task.State.completed && task.getResult() != null) {
            return Map.of("task", Map.of(
                    "id", task.getId(),
                    "status", status,
                    "artifacts", List.of(Map.of(
                            "name", "藏书阁问答结果",
                            "parts", List.of(Map.of("type", "text", "text", task.getResult()))
                    ))
            ));
        }
        if (task.getState() == Task.State.failed) {
            return Map.of("task", Map.of(
                    "id", task.getId(),
                    "status", Map.of("state", "failed", "message", task.getError() != null ? task.getError() : "Unknown error")
            ));
        }
        return Map.of("task", Map.of("id", task.getId(), "status", status));
    }

    // ===== Document Management Handlers =====

    private static void handleListDocuments(Context ctx) throws IOException {
        Path knowledgeDir = Path.of("knowledge");
        if (!Files.exists(knowledgeDir) || !Files.isDirectory(knowledgeDir)) {
            ctx.json(List.of());
            return;
        }

        var allMeta = ragService.getDocumentMeta();

        List<Map<String, Object>> documents = new ArrayList<>();
        try (Stream<Path> paths = Files.list(knowledgeDir)) {
            paths.filter(p -> Files.isRegularFile(p))
                 .filter(p -> !p.getFileName().toString().startsWith("."))
                 .sorted(Comparator.comparingLong((Path p) -> {
                     try { return -Files.getLastModifiedTime(p).toMillis(); }
                     catch (IOException e) { return 0L; }
                 }))
                 .forEach(p -> {
                     try {
                         String name = p.getFileName().toString();
                         long size = Files.size(p);
                         long lastModified = Files.getLastModifiedTime(p).toMillis();
                         String ext = name.contains(".")
                                 ? name.substring(name.lastIndexOf('.') + 1).toUpperCase()
                                 : "FILE";

                         // Get document type from metadata
                         String docType = "GENERAL";
                         AppConfiguration.DocumentTypeConfig typeConfig = config.findDocTypeConfig("GENERAL");
                         var meta = allMeta.get(name);
                         if (meta != null) {
                             docType = meta.type();
                             typeConfig = config.findDocTypeConfig(meta.type());
                         }
                         String docTypeLabel = typeConfig.label;
                         int chunkSize = typeConfig.chunkSize;
                         int chunkOverlap = typeConfig.chunkOverlap;

                         documents.add(Map.of(
                                 "name", name,
                                 "size", size,
                                 "lastModified", lastModified,
                                 "format", ext,
                                 "docType", docType,
                                 "docTypeLabel", docTypeLabel,
                                 "chunkSize", chunkSize,
                                 "chunkOverlap", chunkOverlap
                         ));
                     } catch (IOException ignored) {}
                 });
        }
        ctx.json(documents);
    }

    private static void handleDocumentUpload(Context ctx) throws IOException {
        var uploadedFiles = ctx.uploadedFiles();
        if (uploadedFiles.isEmpty()) {
            ctx.status(400).json(Map.of("error", "No file uploaded"));
            return;
        }

        // Read document type from form field (default: GENERAL)
        String docType = ctx.formParam("type");
        if (docType == null || docType.isBlank()) {
            docType = "GENERAL";
        }

        Path knowledgeDir = Path.of("knowledge");
        if (!Files.exists(knowledgeDir)) {
            Files.createDirectories(knowledgeDir);
        }

        List<String> indexed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (var uploadedFile : uploadedFiles) {
            String filename = uploadedFile.filename();
            if (filename == null || filename.isBlank()) {
                failed.add("unnamed file");
                continue;
            }

            // Security: prevent path traversal
            filename = filename.replaceAll("[\\\\/]", "_");

            Path target = knowledgeDir.resolve(filename);
            try (var is = uploadedFile.content()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                ragService.indexDocument(target, docType);
                indexed.add(filename);
            } catch (Exception e) {
                failed.add(filename + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indexed", indexed);
        result.put("failed", failed);
        RagService.KnowledgeStats stats = ragService.getStats();
        result.put("documentCount", stats.documentCount());
        result.put("segmentCount", stats.segmentCount());
        ctx.json(result);
    }

    private static void handleDocumentDelete(Context ctx) throws IOException {
        String filename = ctx.pathParam("filename");

        // Security: prevent path traversal
        filename = filename.replaceAll("[\\\\/]", "_");

        Path file = Path.of("knowledge").resolve(filename);
        if (!Files.exists(file)) {
            ctx.status(404).json(Map.of("error", "Document not found: " + filename));
            return;
        }

        Files.delete(file);
        ragService.removeDocumentMeta(filename);

        // Rebuild index from remaining files
        try {
            ragService.reindexAll("knowledge/");
            RagService.KnowledgeStats stats = ragService.getStats();
            ctx.json(Map.of("status", "ok", "message", "Deleted: " + filename,
                    "documentCount", stats.documentCount(), "segmentCount", stats.segmentCount()));
        } catch (Exception e) {
            ctx.json(Map.of("status", "ok", "message", "Deleted: " + filename,
                    "warning", "Reindex failed: " + e.getMessage()));
        }
    }

    private static String nvl(String newVal, String defaultVal) {
        return (newVal != null && !newVal.isBlank()) ? newVal : defaultVal;
    }
}
