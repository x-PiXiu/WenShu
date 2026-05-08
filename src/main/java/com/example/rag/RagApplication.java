package com.example.rag;

import com.example.rag.a2a.AgentCard;
import com.example.rag.a2a.Task;
import com.example.rag.a2a.TaskManager;
import com.example.rag.blog.AuthFilter;
import com.example.rag.blog.BlogIndexer;
import com.example.rag.blog.BlogStore;
import com.example.rag.blog.MediaStore;
import com.example.rag.chat.AgentStore;
import com.example.rag.chat.ChatStore;
import com.example.rag.graph.KnowledgeGraphExtractor;
import com.example.rag.graph.KnowledgeGraphStore;
import com.example.rag.tools.PendingFileChanges;
import com.example.rag.config.AppConfiguration;
import com.example.rag.config.LlmProviderStore;
import com.example.rag.eval.EvalResultStore;
import com.example.rag.eval.EvalResultStore.CaseSummary;
import com.example.rag.eval.EvalResultStore.EvalResult;
import com.example.rag.eval.EvalTestCaseStore;
import com.example.rag.eval.RagEvaluator;
import com.example.rag.parser.AutoDocumentParser;
import com.example.rag.observability.LlmCallListener;
import com.example.rag.observability.LlmCallStore;
import com.example.rag.prompt.PromptRegistry;
import com.example.rag.service.MemoryStore;
import com.example.rag.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
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
    private static MemoryStore memoryStore;
    private static LlmCallStore llmCallStore;
    private static BlogStore blogStore;
    private static BlogIndexer blogIndexer;
    private static MediaStore mediaStore;
    private static AuthFilter authFilter;
    private static EvalTestCaseStore evalTestCaseStore;
    private static EvalResultStore evalResultStore;
    private static com.example.rag.tools.ToolEngine toolEngine;
    private static com.example.rag.flashcard.FlashcardStore flashcardStore;
    private static com.example.rag.flashcard.FlashcardGenerator flashcardGenerator;
    private static com.example.rag.a2a.SkillRegistry skillRegistry;
    private static com.example.rag.mcp.McpClientConnector mcpClient;
    private static com.example.rag.a2a.AgentDiscovery agentDiscovery;
    private static KnowledgeGraphStore graphStore;
    private static KnowledgeGraphExtractor graphExtractor;
    private static LlmProviderStore llmProviderStore;

    // Active streaming tasks: conversationId -> AbortController
    private static final ConcurrentHashMap<String, StreamAbortController> activeStreams = new ConcurrentHashMap<>();

    public record StreamAbortController(AtomicBoolean aborted, CountDownLatch latch) {
        public void abort() { aborted.set(true); latch.countDown(); }
    }

    public static void main(String[] args) {
        // 1. Load config
        config = AppConfiguration.load();
        PromptRegistry.init(config);
        System.out.println("[INIT] Configuration loaded");

        // 2. Initialize RAG service
        ragService = new RagService(config);
        System.out.println("[INIT] Models initialized: LLM=" + config.getLlm().modelName
                + ", Embedding=" + config.getEmbedding().modelName);

        // 2.1 LLM observability (must be before indexKnowledgeBase to avoid rebuildModels clearing the store)
        llmCallStore = new LlmCallStore();
        var llmListener = new LlmCallListener(llmCallStore, "chat");
        ragService.setListeners(List.of(llmListener));

        // 2.2 Web Search + Tool Engine
        var webSearcher = new com.example.rag.tools.WebSearcher(config.getWebSearch());
        ragService.setWebSearcher(webSearcher);
        var ragTools = new com.example.rag.tools.RagTools(ragService, webSearcher, config);
        toolEngine = new com.example.rag.tools.ToolEngine(ragTools);
        ragService.setToolEngine(toolEngine);
        ragService.setToolObjects(ragTools);
        System.out.println("[INIT] Tool engine initialized with " + toolEngine.getSpecifications().size() + " tools");
        System.out.println("[INIT] Web search provider: " + config.getWebSearch().provider);

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
        agentStore = new AgentStore(config);
        llmProviderStore = new LlmProviderStore(config);

        // 4.1 Memory store
        memoryStore = new MemoryStore();
        ragService.setMemoryStore(memoryStore);
        memoryStore.recalculateDecay();

        // 4.2 Inject stores into tool engine
        ragTools.setMemoryStore(memoryStore);
        ragTools.setChatStore(chatStore);

        // 4.2.1 Blog store (for blog tools)
        blogStore = new BlogStore();
        blogIndexer = new BlogIndexer(ragService, blogStore);
        blogStore.setBlogIndexer(blogIndexer);
        ragTools.setBlogStore(blogStore);

        // 4.2 Schedule hourly memory decay recalculation
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> {
                    try { memoryStore.recalculateDecay(); } catch (Exception ignored) {}
                }, 1, 60, java.util.concurrent.TimeUnit.MINUTES);

        mediaStore = new MediaStore();

        // 4.3 Re-index published blog articles (InMemory vector store loses data on restart)
        try {
            var publishedArticles = blogStore.listAllPublished();
            if (!publishedArticles.isEmpty()) {
                for (var article : publishedArticles) {
                    blogIndexer.indexArticle(article);
                }
                System.out.println("[INIT] Blog articles indexed: " + publishedArticles.size() + " articles");
            }
        } catch (Exception e) {
            System.err.println("[WARN] Blog article re-indexing failed: " + e.getMessage());
        }
        authFilter = new AuthFilter(config.getBlog().adminPassword);
        evalTestCaseStore = new EvalTestCaseStore("eval");
        evalResultStore = new EvalResultStore("eval");

        // 4.3 Flashcard generator
        flashcardStore = new com.example.rag.flashcard.FlashcardStore();
        flashcardGenerator = new com.example.rag.flashcard.FlashcardGenerator(ragService);
        System.out.println("[INIT] Flashcard store initialized");

        // 4.3.1 Knowledge Graph
        graphStore = new KnowledgeGraphStore();
        graphExtractor = new KnowledgeGraphExtractor(ragService);
        System.out.println("[INIT] Knowledge graph store initialized");

        int port = config.getServer().port;

        // 4.4 Skill Registry + Agent Card
        skillRegistry = new com.example.rag.a2a.SkillRegistry(ragService, toolEngine);
        agentCard = AgentCard.create(
                config.getA2a().agentName,
                config.getA2a().agentDescription,
                "http://localhost:" + port + "/a2a/v1",
                skillRegistry
        );
        System.out.println("[INIT] Skill registry initialized with " + skillRegistry.listSkills().size() + " skills");

        // 4.5 MCP Client — connect to external MCP servers
        mcpClient = new com.example.rag.mcp.McpClientConnector();
        List<AppConfiguration.McpServerConfig> mcpServers = config.getMcpServers();
        if (!mcpServers.isEmpty()) {
            try {
                mcpClient.connectAll(mcpServers);
                List<dev.langchain4j.agent.tool.ToolSpecification> mcpTools = mcpClient.getAllToolSpecifications();
                if (!mcpTools.isEmpty()) {
                    System.out.println("[INIT] MCP Client connected: " + mcpTools.size() + " external tools available");
                }
            } catch (Exception e) {
                System.err.println("[WARN] MCP Client initialization failed: " + e.getMessage());
            }
        }

        // 4.6 A2A Agent Discovery — discover remote agents
        agentDiscovery = new com.example.rag.a2a.AgentDiscovery(skillRegistry);
        skillRegistry.setAgentDiscovery(agentDiscovery);
        List<AppConfiguration.RemoteAgentConfig> remoteAgents = config.getRemoteAgents();
        if (!remoteAgents.isEmpty()) {
            try {
                agentDiscovery.discoverAll(remoteAgents);
                System.out.println("[INIT] Agent discovery completed: " + agentDiscovery.listDiscovered().size() + " remote agents");
            } catch (Exception e) {
                System.err.println("[WARN] Agent discovery failed: " + e.getMessage());
            }
        }

        // 5. Start Javalin server
        Javalin app = Javalin.create(javalinConfig -> {
            // Serve built Vue frontend — only if static/ directory exists (optional)
            Path staticDir = Path.of("static").toAbsolutePath();
            if (Files.isDirectory(staticDir)) {
                javalinConfig.staticFiles.add(staticDir.toString(), io.javalin.http.staticfiles.Location.EXTERNAL);
                System.out.println("[INIT] Static frontend served from: " + staticDir);
            } else {
                System.out.println("[INIT] No static/ directory found — API-only mode (frontend runs separately)");
            }
            javalinConfig.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
            javalinConfig.requestLogger.http((ctx, ms) -> {
                String method = ctx.method().toString();
                String path = ctx.path();
                int status = ctx.statusCode();
                String log = String.format("[HTTP] %s %s -> %d (%.0fms)", method, path, status, ms);
                if (status >= 500) System.err.println(log);
                else if (status >= 400) System.out.println("[WARN] " + log);
                else System.out.println(log);
            });
            javalinConfig.showJavalinBanner = false;
        });

        registerRoutes(app);

        // Upload file serving — MUST be registered BEFORE SPA fallback /* handler
        Path uploadsDir = Path.of("uploads").toAbsolutePath();
        try { if (!Files.exists(uploadsDir)) Files.createDirectories(uploadsDir); }
        catch (java.io.IOException e) { System.err.println("[WARN] Cannot create uploads dir: " + e.getMessage()); }
        app.get("/uploads/{filename}", ctx -> {
            String filename = ctx.pathParam("filename");
            Path file = uploadsDir.resolve(filename).normalize();
            if (!file.startsWith(uploadsDir) || !Files.exists(file)) {
                ctx.status(404).result("Not found");
                return;
            }
            String name = file.getFileName().toString().toLowerCase();
            String contentType = name.endsWith(".png") ? "image/png"
                    : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                    : name.endsWith(".gif") ? "image/gif"
                    : name.endsWith(".svg") ? "image/svg+xml"
                    : name.endsWith(".webp") ? "image/webp"
                    : name.endsWith(".pdf") ? "application/pdf"
                    : "application/octet-stream";
            ctx.contentType(contentType);
            ctx.result(Files.newInputStream(file));
        });

        // SPA fallback: serve index.html for all non-API, non-static, non-upload routes
        Path staticDir = Path.of("static").toAbsolutePath();
        if (Files.isDirectory(staticDir)) {
            app.get("/*", ctx -> {
                String path = ctx.path();
                if (path.startsWith("/api/") || path.startsWith("/uploads/") || path.startsWith("/a2a/")) return;
                if (path.startsWith("/assets/")) return;
                Path indexHtml = staticDir.resolve("index.html");
                if (Files.exists(indexHtml)) {
                    ctx.contentType("text/html; charset=utf-8");
                    ctx.result(Files.newInputStream(indexHtml));
                }
            });
        }

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
        app.post("/api/chat/cancel", RagApplication::handleChatCancel);

        // ===== File Change Confirmation API =====
        app.get("/api/file-change/{id}/diff", ctx -> {
            String id = ctx.pathParam("id");
            var data = com.example.rag.tools.PendingFileChanges.getDiffData(id);
            if (data != null) ctx.json(data);
            else ctx.status(404).json(Map.of("error", "Change not found"));
        });

        app.post("/api/file-change/{id}/apply", ctx -> {
            String id = ctx.pathParam("id");
            try {
                var change = com.example.rag.tools.PendingFileChanges.apply(id);
                if (change != null) {
                    // 如果是知识库文档，触发重新索引
                    if ("document".equals(change.type())) {
                        try {
                            java.nio.file.Path file = java.nio.file.Path.of(change.path());
                            String filename = file.getFileName().toString();
                            ragService.removeDocumentMeta(filename);
                            ragService.reindexAll("knowledge/");
                        } catch (Exception e) {
                            System.err.println("[WARN] Reindex after apply failed: " + e.getMessage());
                        }
                    }
                    ctx.json(Map.of("status", "ok", "path", change.path()));
                } else {
                    ctx.status(404).json(Map.of("error", "Change not found or already resolved"));
                }
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });

        app.post("/api/file-change/{id}/reject", ctx -> {
            String id = ctx.pathParam("id");
            var change = com.example.rag.tools.PendingFileChanges.reject(id);
            if (change != null) ctx.json(Map.of("status", "ok"));
            else ctx.status(404).json(Map.of("error", "Change not found"));
        });

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

            if (newConfig.getA2a() != null) {
                config.getA2a().enabled = newConfig.getA2a().enabled;
                config.getA2a().agentName = nvl(newConfig.getA2a().agentName, config.getA2a().agentName);
                config.getA2a().agentDescription = nvl(newConfig.getA2a().agentDescription, config.getA2a().agentDescription);
            }

            if (newConfig.getWebSearch() != null) {
                var ws = newConfig.getWebSearch();
                var curWs = config.getWebSearch();
                curWs.provider = nvl(ws.provider, curWs.provider);
                curWs.apiKey = nvl(ws.apiKey, curWs.apiKey);
                curWs.baseUrl = nvl(ws.baseUrl, curWs.baseUrl);
                if (ws.maxResults > 0) curWs.maxResults = ws.maxResults;
            }

            if (newConfig.getPrompts() != null) {
                config.setPrompts(newConfig.getPrompts());
                PromptRegistry.init(config);
            }

            if (newConfig.getMcpServers() != null) {
                config.setMcpServers(newConfig.getMcpServers());
            }

            if (newConfig.getRemoteAgents() != null) {
                config.setRemoteAgents(newConfig.getRemoteAgents());
            }

            config.save();
            llmProviderStore.syncActiveFromConfig();
            ragService.rebuildModels();
            ctx.json(Map.of("status", "ok", "message", "Settings saved and models rebuilt"));
        });

        app.post("/api/settings/reindex", ctx -> {
            try {
                ragService.rebuildModels();
                ragService.reindexAll("knowledge/");
                RagService.KnowledgeStats stats = ragService.getStats();
                ctx.json(Map.of("status", "ok", "documents", stats.documentCount(),
                        "segments", stats.segmentCount()));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Reindex failed: " + e.getMessage()));
            }
        });

        // ===== LLM Provider Management API =====
        app.get("/api/llm-providers", ctx -> {
            ctx.json(llmProviderStore.list());
        });

        app.get("/api/llm-providers/active", ctx -> {
            LlmProviderStore.Provider active = llmProviderStore.getActive();
            if (active != null) ctx.json(active);
            else ctx.status(404).json(Map.of("error", "No active provider"));
        });

        app.post("/api/llm-providers", ctx -> {
            var body = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String name = (String) body.getOrDefault("name", "New Provider");
            String provider = (String) body.getOrDefault("provider", "ollama");
            String baseUrl = (String) body.getOrDefault("baseUrl", "");
            String apiKey = (String) body.getOrDefault("apiKey", "");
            String modelName = (String) body.getOrDefault("modelName", "");
            Double temperature = body.get("temperature") != null ? ((Number) body.get("temperature")).doubleValue() : null;
            Integer maxTokens = body.get("maxTokens") != null ? ((Number) body.get("maxTokens")).intValue() : null;
            boolean streaming = body.get("streaming") == null || Boolean.TRUE.equals(body.get("streaming"));
            LlmProviderStore.Provider created = llmProviderStore.create(name, provider, baseUrl, apiKey, modelName, temperature, maxTokens, streaming);
            ctx.status(201).json(created);
        });

        app.put("/api/llm-providers/{id}", ctx -> {
            String id = ctx.pathParam("id");
            var body = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String name = (String) body.getOrDefault("name", "");
            String provider = (String) body.getOrDefault("provider", "ollama");
            String baseUrl = (String) body.getOrDefault("baseUrl", "");
            String apiKey = (String) body.getOrDefault("apiKey", "");
            String modelName = (String) body.getOrDefault("modelName", "");
            Double temperature = body.get("temperature") != null ? ((Number) body.get("temperature")).doubleValue() : null;
            Integer maxTokens = body.get("maxTokens") != null ? ((Number) body.get("maxTokens")).intValue() : null;
            boolean streaming = body.get("streaming") == null || Boolean.TRUE.equals(body.get("streaming"));
            try {
                LlmProviderStore.Provider updated = llmProviderStore.update(id, name, provider, baseUrl, apiKey, modelName, temperature, maxTokens, streaming);
                if (updated.isDefault()) ragService.rebuildModels();
                ctx.json(updated);
            } catch (RuntimeException e) {
                ctx.status(404).json(Map.of("error", e.getMessage()));
            }
        });

        app.delete("/api/llm-providers/{id}", ctx -> {
            String id = ctx.pathParam("id");
            try {
                llmProviderStore.delete(id);
                ctx.json(Map.of("status", "ok"));
            } catch (RuntimeException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        app.post("/api/llm-providers/{id}/activate", ctx -> {
            String id = ctx.pathParam("id");
            try {
                LlmProviderStore.Provider activated = llmProviderStore.setActive(id);
                ragService.rebuildModels();
                System.out.println("[LLM] Switched to provider: " + activated.name() + " (" + activated.modelName() + ")");
                ctx.json(activated);
            } catch (RuntimeException e) {
                ctx.status(404).json(Map.of("error", e.getMessage()));
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
        app.get("/api/agents", ctx -> {
            List<AgentStore.Agent> agents = agentStore.list();
            List<Map<String, Object>> response = agents.stream().map(a -> {
                String resolved = PromptRegistry.getTemplate(a.promptKey());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.id());
                m.put("name", a.name());
                m.put("description", a.description());
                m.put("systemPrompt", resolved != null ? resolved : "");
                m.put("promptKey", a.promptKey());
                m.put("avatar", a.avatar());
                m.put("isDefault", a.isDefault());
                m.put("toolNames", a.toolNames());
                m.put("createdAt", a.createdAt());
                m.put("updatedAt", a.updatedAt());
                return m;
            }).toList();
            ctx.json(response);
        });

        app.post("/api/agents", ctx -> {
            JsonNode root = MAPPER.readTree(ctx.body());
            String name = root.path("name").asText("");
            String description = root.path("description").asText("");
            String systemPrompt = root.path("systemPrompt").asText("");
            String avatar = root.path("avatar").asText("");
            if (name.isBlank() || systemPrompt.isBlank()) {
                ctx.status(400).json(Map.of("error", "name and systemPrompt are required"));
                return;
            }
            List<String> toolNames = parseToolNamesFromJson(root.path("toolNames"));
            AgentStore.Agent agent = agentStore.create(name, description, systemPrompt, avatar, toolNames.isEmpty() ? null : toolNames);
            String resolved = PromptRegistry.getTemplate(agent.promptKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", agent.id()); m.put("name", agent.name()); m.put("description", agent.description());
            m.put("systemPrompt", resolved != null ? resolved : ""); m.put("promptKey", agent.promptKey());
            m.put("avatar", agent.avatar()); m.put("isDefault", agent.isDefault());
            m.put("toolNames", agent.toolNames());
            m.put("createdAt", agent.createdAt()); m.put("updatedAt", agent.updatedAt());
            ctx.json(m);
        });

        app.put("/api/agents/{id}", ctx -> {
            String id = ctx.pathParam("id");
            JsonNode root = MAPPER.readTree(ctx.body());
            String name = root.path("name").asText("");
            String description = root.path("description").asText("");
            String systemPrompt = root.path("systemPrompt").asText("");
            String avatar = root.path("avatar").asText("");
            if (name.isBlank() || systemPrompt.isBlank()) {
                ctx.status(400).json(Map.of("error", "name and systemPrompt are required"));
                return;
            }
            List<String> toolNames = parseToolNamesFromJson(root.path("toolNames"));
            try {
                AgentStore.Agent agent = agentStore.update(id, name, description, systemPrompt, avatar, toolNames.isEmpty() ? null : toolNames);
                String resolved = PromptRegistry.getTemplate(agent.promptKey());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", agent.id()); m.put("name", agent.name()); m.put("description", agent.description());
                m.put("systemPrompt", resolved != null ? resolved : ""); m.put("promptKey", agent.promptKey());
                m.put("avatar", agent.avatar()); m.put("isDefault", agent.isDefault());
                m.put("toolNames", agent.toolNames());
                m.put("createdAt", agent.createdAt()); m.put("updatedAt", agent.updatedAt());
                ctx.json(m);
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

        // 工具列表 API（供 Agent 编辑页勾选用）
        app.get("/api/tools", ctx -> {
            List<Map<String, String>> tools = toolEngine.getSpecifications().stream()
                    .map(spec -> Map.of("name", spec.name(), "description", spec.description() != null ? spec.description() : ""))
                    .toList();
            ctx.json(tools);
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

        // ===== MCP (Model Context Protocol) Server =====
        if (config.getA2a().enabled) {
            var mcpHandler = new com.example.rag.mcp.McpServerHandler(ragService, toolEngine);
            app.get("/mcp/sse", mcpHandler::handleSse);
            app.post("/mcp/message", mcpHandler::handleMessage);
            System.out.println("[INIT] MCP Server enabled at /mcp/sse");
        }

        // ===== Blog Public Routes =====
        registerBlogRoutes(app);

        // ===== Flashcard Routes =====
        registerFlashcardRoutes(app);

        // ===== Knowledge Graph Routes =====
        registerGraphRoutes(app);
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

        // --- Blog Chat API ---
        app.post("/api/blog/chat", ctx -> {
            JsonNode root = MAPPER.readTree(ctx.body());
            String question = root.path("question").asText("");

            if (question.isBlank()) {
                ctx.status(400).json(Map.of("error", "question is required"));
                return;
            }
            String slug = root.path("slug").asText(null);
            try {
                BlogStore.Article article = blogStore.getBySlug(slug);
                if (article == null) {
                    ctx.status(404).json(Map.of("error", "Article not found"));
                    return;
                }
                List<RagService.HistoryEntry> history = parseBlogChatHistory(root.path("history"));
                RagService.RagAnswer answer = ragService.askBlogDirect(question, history,
                        article.title(), article.content(), null);
                ctx.json(Map.of("answer", answer.answer(), "sources", answer.sources()));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Failed to process question: " + e.getMessage()));
            }
        });

        app.post("/api/blog/chat/stream", ctx -> {
            JsonNode root = MAPPER.readTree(ctx.body());
            String question = root.path("question").asText("");
            if (question.isBlank()) {
                ctx.status(400).json(Map.of("error", "question is required"));
                return;
            }
            String slug = root.path("slug").asText(null);

            BlogStore.Article article = blogStore.getBySlug(slug);
            if (article == null) {
                ctx.status(404).json(Map.of("error", "Article not found"));
                return;
            }

            List<RagService.HistoryEntry> history = parseBlogChatHistory(root.path("history"));
            RagService.StreamContext streamCtx = ragService.prepareBlogDirectStreamContext(
                    question, history, article.title(), article.content(), null);

            ctx.res().setContentType("text/event-stream; charset=UTF-8");
            ctx.res().setHeader("Cache-Control", "no-cache");
            ctx.res().setHeader("Connection", "keep-alive");
            ctx.res().setHeader("X-Accel-Buffering", "no");

            var writer = ctx.res().getWriter();

            StringBuilder fullAnswer = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean doneSent = new AtomicBoolean(false);
            AtomicLong lastTokenTime = new AtomicLong(0);
            AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

            java.util.concurrent.ScheduledExecutorService watchdog =
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            watchdog.scheduleAtFixedRate(() -> {
                if (!firstTokenReceived.get() || doneSent.get()) return;
                long elapsed = System.currentTimeMillis() - lastTokenTime.get();
                if (elapsed > 5_000 && doneSent.compareAndSet(false, true)) {
                    try {
                        Map<String, Object> donePayload = new LinkedHashMap<>();
                        donePayload.put("answer", fullAnswer.toString());
                        donePayload.put("sources", streamCtx.sources());
                        writer.write("event: done\ndata: " + MAPPER.writeValueAsString(donePayload) + "\n\n");
                        writer.flush();
                    } catch (Exception ignored) {}
                    latch.countDown();
                }
            }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

            ragService.streamGenerate(streamCtx.messages(), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    lastTokenTime.set(System.currentTimeMillis());
                    firstTokenReceived.set(true);
                    fullAnswer.append(token);
                    try {
                        writer.write("event: token\ndata: " + MAPPER.writeValueAsString(Map.of("t", token)) + "\n\n");
                        writer.flush();
                    } catch (Exception ignored) {}
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                    watchdog.shutdownNow();
                    if (doneSent.compareAndSet(false, true)) {
                        try {
                            Map<String, Object> donePayload = new LinkedHashMap<>();
                            donePayload.put("answer", fullAnswer.toString());
                            donePayload.put("sources", streamCtx.sources());
                            writer.write("event: done\ndata: " + MAPPER.writeValueAsString(donePayload) + "\n\n");
                            writer.flush();
                        } catch (Exception ignored) {}
                    }
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    watchdog.shutdownNow();
                    if (doneSent.compareAndSet(false, true)) {
                        try {
                            writer.write("event: error\ndata: " + MAPPER.writeValueAsString(
                                    Map.of("error", error.getMessage() != null ? error.getMessage() : "Unknown error")) + "\n\n");
                            writer.flush();
                        } catch (Exception ignored) {}
                    }
                    latch.countDown();
                }
            });

            latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
            watchdog.shutdownNow();
        });

        // --- Admin: Login ---
        app.post("/api/admin/login", ctx -> {
            String cfgPassword = config.getBlog().adminPassword;
            // 无密码模式：直接返回免登录 token
            if (cfgPassword == null || cfgPassword.isBlank()) {
                String token = AuthFilter.generateToken("no-password");
                ctx.json(Map.of("token", token, "status", "ok", "passwordEnabled", false));
                return;
            }
            Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
            String password = body.get("password");
            if (password != null && password.equals(cfgPassword)) {
                String token = AuthFilter.generateToken(password);
                ctx.json(Map.of("token", token, "status", "ok", "passwordEnabled", true));
            } else {
                ctx.status(401).json(Map.of("error", "Invalid password"));
            }
        });

        // 查询是否启用密码保护
        app.get("/api/admin/auth-status", ctx -> {
            String cfgPassword = config.getBlog().adminPassword;
            boolean enabled = cfgPassword != null && !cfgPassword.isBlank();
            ctx.json(Map.of("passwordEnabled", enabled));
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
            String summary = (String) body.get("summary");
            String coverImage = (String) body.get("coverImage");
            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "title is required"));
                return;
            }
            BlogStore.Article article = blogStore.createArticle(title, content != null ? content : "", contentType, category, tags, summary, coverImage);
            ctx.json(article);
        });

        app.put("/api/admin/posts/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, Object> body = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String title = (String) body.get("title");
            String content = (String) body.get("content");
            String category = (String) body.get("category");
            List<String> tags = body.get("tags") instanceof List ? (List<String>) body.get("tags") : List.of();
            String summary = (String) body.get("summary");
            String coverImage = (String) body.get("coverImage");
            if (title == null || title.isBlank()) {
                ctx.status(400).json(Map.of("error", "title is required"));
                return;
            }
            BlogStore.Article article = blogStore.updateArticle(id, title, content != null ? content : "", category, tags, summary, coverImage);
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

        app.post("/api/admin/posts/{id}/summarize", ctx -> {
            String id = ctx.pathParam("id");
            BlogStore.Article article = blogStore.getById(id);
            if (article == null) {
                ctx.status(404).json(Map.of("error", "Article not found"));
                return;
            }
            String text = article.content();
            if (text.length() > 2000) text = text.substring(0, 2000);
            String summaryTemplate = PromptRegistry.getTemplate("article_summary");
            String defaultSummaryPrompt = "请用1-2句话总结以下文章的核心内容，不超过100字，直接输出摘要文本，不要加引号或前缀：\n\n";
            String prompt = (summaryTemplate != null && !summaryTemplate.isBlank()
                    ? summaryTemplate : defaultSummaryPrompt) + text;
            String generatedSummary = ragService.query(prompt);
            generatedSummary = generatedSummary.replaceAll("^\"|\"$", "").trim();
            article = blogStore.updateArticle(id, article.title(), article.content(),
                    article.category(), article.tags(), generatedSummary, article.coverImage());
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

        // --- Admin: Media ---
        app.get("/api/admin/media", ctx -> ctx.json(mediaStore.listAll()));

        app.post("/api/admin/media/upload", ctx -> {
            var uploadedFiles = ctx.uploadedFiles();
            if (uploadedFiles.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No file uploaded"));
                return;
            }
            List<Map<String, Object>> uploaded = new ArrayList<>();
            for (var file : uploadedFiles) {
                String filename = file.filename();
                if (filename == null || filename.isBlank()) continue;
                filename = filename.replaceAll("[\\\\/]", "_");
                try (var is = file.content()) {
                    MediaStore.MediaFile mf = mediaStore.upload(filename, file.contentType(), is);
                    uploaded.add(Map.of(
                            "id", mf.id(),
                            "filename", mf.filename(),
                            "url", mf.url(),
                            "fileSize", mf.fileSize(),
                            "mimeType", mf.mimeType()
                    ));
                } catch (Exception e) {
                    uploaded.add(Map.of("error", filename + ": " + e.getMessage()));
                }
            }
            ctx.json(uploaded);
        });

        app.delete("/api/admin/media/{id}", ctx -> {
            if (mediaStore.delete(ctx.pathParam("id"))) {
                ctx.json(Map.of("status", "ok"));
            } else {
                ctx.status(404).json(Map.of("error", "Media not found"));
            }
        });

        // --- Admin: Document Import (DOCX/PDF → article) ---
        app.post("/api/admin/import", ctx -> {
            var uploadedFiles = ctx.uploadedFiles();
            if (uploadedFiles.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No file uploaded"));
                return;
            }
            var file = uploadedFiles.get(0);
            String filename = file.filename();
            if (filename == null || filename.isBlank()) {
                ctx.status(400).json(Map.of("error", "Filename is required"));
                return;
            }

            Path tmpDir = Files.createTempDirectory("rag-import-");
            Path tmpFile = tmpDir.resolve(filename.replaceAll("[\\\\/]", "_"));
            try (var is = file.content()) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Document doc = AutoDocumentParser.load(tmpFile);
                String text = doc.text();
                String title = filename.replaceAll("\\.(docx|pdf|txt|md)$", "").trim();

                BlogStore.Article article = blogStore.createArticle(title, text, "md", null, List.of(), null, null);
                ctx.json(article);
            } finally {
                Files.deleteIfExists(tmpFile);
                Files.deleteIfExists(tmpDir);
            }
        });

        // --- Admin: Retrieval Evaluation ---
        app.get("/api/admin/eval/cases", ctx -> ctx.json(evalTestCaseStore.getAll()));

        app.post("/api/admin/eval/cases", ctx -> {
            EvalTestCaseStore.TestCase tc = MAPPER.readValue(ctx.body(), EvalTestCaseStore.TestCase.class);
            if (tc.id() == null || tc.question() == null || tc.relevantSources() == null) {
                ctx.status(400).json(Map.of("error", "id, question, relevantSources are required"));
                return;
            }
            evalTestCaseStore.add(tc);
            ctx.json(Map.of("status", "ok"));
        });

        app.delete("/api/admin/eval/cases/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (evalTestCaseStore.remove(id)) {
                ctx.json(Map.of("status", "ok"));
            } else {
                ctx.status(404).json(Map.of("error", "Test case not found"));
            }
        });

        app.post("/api/admin/eval/run", ctx -> {
            int topK = config.getRag().vectorTopK;
            try {
                JsonNode root = MAPPER.readTree(ctx.body());
                if (root.has("topK") && root.get("topK").asInt() > 0) {
                    topK = root.get("topK").asInt();
                }
            } catch (Exception ignored) {}

            List<EvalTestCaseStore.TestCase> cases = evalTestCaseStore.getAll();
            if (cases.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No test cases. Add cases via POST /api/admin/eval/cases"));
                return;
            }

            RagEvaluator evaluator = new RagEvaluator(ragService.getSearcher());
            RagEvaluator.RetrievalReport report = evaluator.evaluateRetrieval(cases, topK);

            // Persist result
            List<CaseSummary> summaries = report.caseResults().stream()
                    .map(cr -> new CaseSummary(cr.id(), cr.question(), cr.category(), cr.hit(), cr.topScore()))
                    .toList();
            EvalResult result = new EvalResult(
                    java.time.Instant.now().toString(),
                    report.totalCases(), report.topK(),
                    report.hitRate(), report.avgRecallAtK(), report.avgPrecisionAtK(), report.mrr(),
                    summaries
            );
            evalResultStore.save(result);

            ctx.json(report);
        });

        app.get("/api/admin/eval/results", ctx -> ctx.json(evalResultStore.getHistory()));

        app.get("/api/admin/eval/trend", ctx -> {
            List<EvalResult> history = evalResultStore.getHistory();
            List<Map<String, Object>> trend = new ArrayList<>();
            for (EvalResult r : history) {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("timestamp", r.timestamp());
                entry.put("hitRate", r.hitRate());
                entry.put("avgRecallAtK", r.avgRecallAtK());
                entry.put("avgPrecisionAtK", r.avgPrecisionAtK());
                entry.put("mrr", r.mrr());
                trend.add(entry);
            }
            ctx.json(trend);
        });

        app.get("/api/admin/eval/status", ctx -> {
            List<EvalResult> history = evalResultStore.getHistory();
            if (history.isEmpty()) {
                ctx.json(Map.of("hasResults", false));
                return;
            }
            EvalResult latest = history.get(history.size() - 1);
            Map<String, Object> status = new java.util.LinkedHashMap<>();
            status.put("hasResults", true);
            status.put("latest", latest);
            if (history.size() >= 2) {
                EvalResult prev = history.get(history.size() - 2);
                status.put("delta", Map.of(
                        "hitRate", latest.hitRate() - prev.hitRate(),
                        "avgRecallAtK", latest.avgRecallAtK() - prev.avgRecallAtK(),
                        "mrr", latest.mrr() - prev.mrr()
                ));
            }
            ctx.json(status);
        });

        // --- Admin: Memory Management ---
        app.get("/api/admin/memories", ctx -> ctx.json(memoryStore.listAll()));

        app.get("/api/admin/memories/count", ctx -> ctx.json(Map.of("count", memoryStore.getMemoryCount())));

        app.delete("/api/admin/memories/{id}", ctx -> {
            String id = ctx.pathParam("id");
            memoryStore.deleteMemory(id);
            ctx.json(Map.of("status", "ok"));
        });

        app.post("/api/admin/memories/recalc", ctx -> {
            int updated = memoryStore.recalculateDecay();
            ctx.json(Map.of("status", "ok", "updated", updated));
        });

        // --- Admin: LLM Monitoring ---
        app.get("/api/admin/llm/calls", ctx -> {
            int limit = 50;
            try { limit = Integer.parseInt(ctx.queryParam("limit")); } catch (Exception ignored) {}
            ctx.json(llmCallStore.listRecent(limit));
        });

        app.get("/api/admin/llm/stats", ctx -> {
            Map<String, Object> stats = new java.util.LinkedHashMap<>();
            stats.put("today", llmCallStore.getTodayStats());
            stats.put("total", llmCallStore.getTotalStats());
            ctx.json(stats);
        });

        app.get("/api/admin/llm/daily", ctx -> {
            int days = 7;
            try { days = Integer.parseInt(ctx.queryParam("days")); } catch (Exception ignored) {}
            ctx.json(llmCallStore.getDailyStats(days));
        });

        app.get("/api/admin/llm/hourly", ctx -> ctx.json(llmCallStore.getHourlyStats()));

        app.get("/api/admin/llm/type-distribution", ctx -> ctx.json(llmCallStore.getTypeDistribution()));

        app.get("/api/admin/llm/latency-distribution", ctx -> ctx.json(llmCallStore.getLatencyDistribution()));
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
            java.util.Set<String> toolNames = resolveAgentToolNames(agentId);
            RagService.RagAnswer answer = ragService.askWithContext(question, history, agentPrompt, toolNames);

            // Save user message + assistant message
            chatStore.saveMessage(conversationId, "user", question, null);

            List<ChatStore.MessageSource> msgSources = answer.sources().stream()
                    .map(s -> new ChatStore.MessageSource(s.index(), s.text(), s.source(), s.rrfScore(), s.vectorScore(),
                            s.confidence(), s.confidenceLabel(), s.explanation()))
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
                        ragService.storeMemory(conversationId, summary, fullHistory);
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

        // Set SSE response headers early so we can send status events during retrieval
        ctx.res().setContentType("text/event-stream; charset=UTF-8");
        ctx.res().setHeader("Cache-Control", "no-cache");
        ctx.res().setHeader("Connection", "keep-alive");
        ctx.res().setHeader("X-Accel-Buffering", "no");

        var writer = ctx.res().getWriter();
        String finalConvId = conversationId;

        // Register active stream for cancellation support
        StreamAbortController abortCtrl = new StreamAbortController(new AtomicBoolean(false), new CountDownLatch(1));
        activeStreams.put(finalConvId, abortCtrl);
        try {
            handleChatStreamInternal(ctx, question, conversationId, agentId, history, writer, finalConvId, abortCtrl);
        } finally {
            activeStreams.remove(finalConvId);
        }
    }

    private static void handleChatStreamInternal(Context ctx, String question, String conversationId,
                                                  String agentId, List<RagService.HistoryEntry> history,
                                                  java.io.PrintWriter writer, String finalConvId,
                                                  StreamAbortController abortCtrl) throws Exception {

        // Send meta event with conversationId
        writer.write("event: meta\ndata: " + MAPPER.writeValueAsString(
                Map.of("conversationId", finalConvId)) + "\n\n");
        writer.flush();

        // Send retrieval status
        writer.write("event: status\ndata: " + MAPPER.writeValueAsString(
                Map.of("phase", "searching", "message", "正在检索知识库...")) + "\n\n");
        writer.flush();

        // Prepare RAG context + AiServices (TokenStream handles tool calling automatically)
        String agentPrompt = resolveAgentPrompt(agentId);
        java.util.Set<String> toolNames = resolveAgentToolNames(agentId);
        RagService.AgenticStreamContext agenticCtx = ragService.prepareAgenticStream(question, history, agentPrompt, toolNames);

        // Send post-retrieval status
        writer.write("event: status\ndata: " + MAPPER.writeValueAsString(
                Map.of("phase", "generating",
                       "message", "找到 " + agenticCtx.sources().size() + " 个片段，正在生成回答...",
                       "segments", agenticCtx.sources().size())) + "\n\n");
        writer.flush();

        // Send sources event
        String sourcesJson = MAPPER.writeValueAsString(agenticCtx.sources());
        writer.write("event: sources\ndata: " + sourcesJson + "\n\n");
        writer.flush();

        // Stream via TokenStream (AiServices manages tool calling loop automatically)
        StringBuilder fullAnswer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean doneSent = new AtomicBoolean(false);
        AtomicLong lastTokenTime = new AtomicLong(0);
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

        // Watchdog for stall detection (some providers don't fire onCompleteResponse)
        java.util.concurrent.ScheduledExecutorService watchdog =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleAtFixedRate(() -> {
            if (!firstTokenReceived.get() || doneSent.get()) return;
            long elapsed = System.currentTimeMillis() - lastTokenTime.get();
            if (elapsed > 15_000 && doneSent.compareAndSet(false, true)) {
                System.err.println("[STREAM] Watchdog: no token for 15s, sending done");
                try {
                    Map<String, Object> donePayload = new LinkedHashMap<>();
                    donePayload.put("answer", fullAnswer.toString());
                    donePayload.put("conversationId", finalConvId);
                    donePayload.put("sources", agenticCtx.sources());
                    writer.write("event: done\ndata: " + MAPPER.writeValueAsString(donePayload) + "\n\n");
                    writer.flush();
                } catch (Exception ignored) {}
                latch.countDown();
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

        agenticCtx.tokenStream()
                .onPartialResponse(token -> {
                    if (abortCtrl.aborted().get()) return;
                    fullAnswer.append(token);
                    lastTokenTime.set(System.currentTimeMillis());
                    firstTokenReceived.set(true);
                    try {
                        writer.write("event: token\ndata: " + MAPPER.writeValueAsString(
                                Map.of("t", token)) + "\n\n");
                        writer.flush();
                    } catch (Exception ignored) {}
                })
                .onToolExecuted(toolExecution -> {
                    lastTokenTime.set(System.currentTimeMillis());
                    String toolName = toolExecution.request().name();
                    String toolResult = toolExecution.result();
                    try {
                        writer.write("event: status\ndata: " + MAPPER.writeValueAsString(
                                Map.of("phase", "tool_call",
                                       "message", "调用工具: " + toolName)) + "\n\n");
                        // 检测文件修改暂存事件
                        if (toolResult != null && toolResult.contains("PENDING_CONFIRM=true")) {
                            String changeId = extractTagValue(toolResult, "changeId=");
                            if (changeId != null) {
                                // Use PendingFileChanges for reliable data instead of string parsing
                                Map<String, Object> diffData = PendingFileChanges.getDiffData(changeId);
                                if (diffData != null) {
                                    Map<String, Object> diffEvent = new LinkedHashMap<>();
                                    diffEvent.put("changeId", changeId);
                                    diffEvent.put("path", diffData.get("path"));
                                    diffEvent.put("tool", toolName);
                                    diffEvent.put("oldLines", diffData.get("oldLines"));
                                    diffEvent.put("newLines", diffData.get("newLines"));
                                    writer.write("event: file_diff\ndata: " + MAPPER.writeValueAsString(diffEvent) + "\n\n");
                                    System.err.println("[DEBUG] file_diff sent: changeId=" + changeId + " path=" + diffData.get("path"));
                                } else {
                                    System.err.println("[WARN] file_diff: changeId=" + changeId + " not found in PendingFileChanges");
                                }
                            }
                        }
                        writer.flush();
                    } catch (Exception e) {
                        System.err.println("[ERROR] onToolExecuted failed: " + e.getMessage());
                    }
                })
                .onCompleteResponse(response -> {
                    watchdog.shutdownNow();
                    if (doneSent.compareAndSet(false, true)) {
                        try {
                            Map<String, Object> donePayload = new LinkedHashMap<>();
                            donePayload.put("answer", fullAnswer.toString());
                            donePayload.put("conversationId", finalConvId);
                            donePayload.put("sources", agenticCtx.sources());
                            writer.write("event: done\ndata: " + MAPPER.writeValueAsString(donePayload) + "\n\n");
                            writer.flush();
                        } catch (Exception ignored) {}
                    }
                    latch.countDown();

                    // Save messages to ChatStore
                    try {
                        saveStreamMessages(finalConvId, question, fullAnswer.toString(), agenticCtx.sources());
                    } catch (Exception e) {
                        System.err.println("[WARN] Failed to save stream messages: " + e.getMessage());
                    }

                    // Long-term memory generation
                    String capturedAnswer = fullAnswer.toString();
                    CompletableFuture.runAsync(() -> {
                        try {
                            List<RagService.HistoryEntry> fullHistory = new ArrayList<>(history);
                            fullHistory.add(new RagService.HistoryEntry("user", question));
                            fullHistory.add(new RagService.HistoryEntry("assistant", capturedAnswer));
                            if (fullHistory.size() >= 6 && fullHistory.size() % 6 == 0) {
                                String summary = ragService.summarizeConversation(fullHistory);
                                if (summary != null && !summary.isBlank()) {
                                    ragService.storeMemory(finalConvId, summary, fullHistory);
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                })
                .onError(error -> {
                    watchdog.shutdownNow();
                    if (doneSent.compareAndSet(false, true)) {
                        try {
                            String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                            writer.write("event: error\ndata: " + MAPPER.writeValueAsString(
                                    Map.of("error", msg)) + "\n\n");
                            writer.flush();
                        } catch (Exception ignored) {}
                    }
                    latch.countDown();

                    try {
                        saveStreamMessages(finalConvId, question, fullAnswer.toString(), agenticCtx.sources());
                    } catch (Exception ignored) {}
                })
                .start();

        // Block until streaming completes (hard timeout as last resort)
        boolean completed = latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
        watchdog.shutdownNow();

        if (!completed) {
            System.err.println("[STREAM] Hard timeout (120s), forcing done");
        }

        // Hard timeout fallback
        if (!completed && doneSent.compareAndSet(false, true)) {
            try {
                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("answer", fullAnswer.toString());
                donePayload.put("conversationId", finalConvId);
                donePayload.put("sources", agenticCtx.sources());
                writer.write("event: done\ndata: " + MAPPER.writeValueAsString(donePayload) + "\n\n");
                writer.flush();
            } catch (Exception ignored) {}

            if (fullAnswer.length() > 0) {
                try {
                    saveStreamMessages(finalConvId, question, fullAnswer.toString(), agenticCtx.sources());
                } catch (Exception ignored) {}
            }
        }

        // Explicitly close writer to ensure SSE connection terminates
        try { writer.close(); } catch (Exception ignored) {}
    }

    /**
     * Cancel an active streaming generation for a conversation.
     */
    private static void handleChatCancel(Context ctx) throws Exception {
        Map<String, String> body = MAPPER.readValue(ctx.body(), STRING_MAP_TYPE);
        String conversationId = body.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            ctx.status(400).json(Map.of("error", "conversationId is required"));
            return;
        }
        StreamAbortController ctrl = activeStreams.get(conversationId);
        if (ctrl != null) {
            ctrl.abort();
            ctx.json(Map.of("status", "ok", "message", "Stream cancelled"));
        } else {
            ctx.json(Map.of("status", "ok", "message", "No active stream for this conversation"));
        }
    }

    /**
     * Shared helper: save user + assistant messages and update conversation metadata.
     * Uses a single connection + transaction to avoid 5 separate pool borrow/return cycles.
     */
    private static void saveStreamMessages(String convId, String question,
                                           String answer, List<RagService.SourceInfo> sources) {
        List<ChatStore.MessageSource> msgSources = sources.stream()
                .map(s -> new ChatStore.MessageSource(
                        s.index(), s.text(), s.source(), s.rrfScore(), s.vectorScore(),
                        s.confidence(), s.confidenceLabel(), s.explanation()))
                .toList();
        saveStreamMessagesInner(convId, question, answer, msgSources);
    }

    private static void saveStreamMessages(String convId, String question,
                                           String answer, RagService.StreamContext streamCtx) {
        List<ChatStore.MessageSource> msgSources = streamCtx.sources().stream()
                .map(s -> new ChatStore.MessageSource(
                        s.index(), s.text(), s.source(), s.rrfScore(), s.vectorScore(),
                        s.confidence(), s.confidenceLabel(), s.explanation()))
                .toList();
        saveStreamMessagesInner(convId, question, answer, msgSources);
    }

    private static void saveStreamMessagesInner(String convId, String question,
                                                 String answer, List<ChatStore.MessageSource> msgSources) {
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
        AgentStore.Agent agent = resolveAgent(agentId);
        if (agent != null) {
            String prompt = PromptRegistry.getTemplate(agent.promptKey());
            if (prompt != null && !prompt.isBlank()) return prompt;
        }
        return null;
    }

    /**
     * 解析 Agent，返回 Agent 对象（含 toolNames）
     */
    private static AgentStore.Agent resolveAgent(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            AgentStore.Agent agent = agentStore.getById(agentId);
            if (agent != null) return agent;
        }
        return agentStore.getById("default");
    }

    /**
     * 获取 Agent 的工具过滤名称集合
     * 返回 null 表示使用全部工具
     */
    private static java.util.Set<String> resolveAgentToolNames(String agentId) {
        AgentStore.Agent agent = resolveAgent(agentId);
        if (agent != null && agent.hasToolFilter()) {
            return new java.util.HashSet<>(agent.toolNames());
        }
        return null;
    }

    private static List<RagService.HistoryEntry> parseBlogChatHistory(JsonNode historyNode) {
        if (historyNode == null || !historyNode.isArray() || historyNode.isEmpty()) return List.of();
        List<RagService.HistoryEntry> result = new ArrayList<>();
        for (JsonNode item : historyNode) {
            String role = item.path("role").asText("");
            String content = item.path("content").asText("");
            if (!role.isBlank() && !content.isBlank()) {
                result.add(new RagService.HistoryEntry(role, content));
            }
        }
        // Keep only last 10 entries to avoid excessive context
        int start = Math.max(0, result.size() - 10);
        return result.subList(start, result.size());
    }

    // ===== A2A Handlers =====

    private static Object handleA2ATaskSend(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String skillId = (String) params.get("skillId");
        String taskId = params.containsKey("id") ? String.valueOf(params.get("id")) : "task-" + UUID.randomUUID();

        Task task = taskManager.create(taskId, skillId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) params.get("input");
            if (input == null) input = Map.of();

            String result = skillRegistry.execute(skillId, input);
            task.completeWithDetails(result, skillId, List.of());
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

                         String detectionMethod = (meta != null) ? meta.detectionMethod() : "manual";

                         documents.add(Map.of(
                                 "name", name,
                                 "size", size,
                                 "lastModified", lastModified,
                                 "format", ext,
                                 "docType", docType,
                                 "docTypeLabel", docTypeLabel,
                                 "chunkSize", chunkSize,
                                 "chunkOverlap", chunkOverlap,
                                 "detectionMethod", detectionMethod
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

        // Read document type from form field (default: AUTO for auto-detection)
        String docType = ctx.formParam("type");
        if (docType == null || docType.isBlank()) {
            docType = "AUTO";
        }
        boolean autoDetect = "AUTO".equalsIgnoreCase(docType);

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
                String detectedType;
                if (autoDetect) {
                    detectedType = ragService.autoDetectAndIndex(target);
                } else {
                    ragService.indexDocument(target, docType);
                    detectedType = docType;
                }
                indexed.add(filename + " [" + detectedType + "]");
            } catch (Exception e) {
                failed.add(filename + ": " + e.getMessage());
            }

            // Auto-merge new document into the main knowledge graph (create if needed)
            try {
                var mainGraph = graphStore.getOrCreateMainGraph();
                if (mainGraph != null) {
                    String fname = filename;
                    String mainGraphId = mainGraph.id();
                    CompletableFuture.runAsync(() -> {
                        try {
                            Path p = Path.of("knowledge").resolve(fname.replaceAll("[\\\\/]", "_"));
                            if (!Files.exists(p)) return;
                            String text = AutoDocumentParser.load(p).text();
                            if (text == null || text.isBlank()) return;
                            if (text.length() > 8000) text = text.substring(0, 8000) + "...";
                            var req = new KnowledgeGraphExtractor.ExtractRequest(text, fname, 15, 25);
                            var extraction = graphExtractor.extract(req);
                            graphStore.mergeIntoGraph(mainGraphId, extraction, fname);
                        } catch (Exception ex) {
                            System.err.println("[WARN] Auto-merge graph failed for " + fname + ": " + ex.getMessage());
                        }
                    });
                }
            } catch (Exception ignored) {}
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

    private static String extractTagValue(String text, String tag) {
        int start = text.indexOf(tag);
        if (start < 0) return null;
        start += tag.length();
        int end = start;
        while (end < text.length() && text.charAt(end) != ' ' && text.charAt(end) != '\n') end++;
        return text.substring(start, end);
    }

    private static String nvl(String newVal, String defaultVal) {
        return (newVal != null && !newVal.isBlank()) ? newVal : defaultVal;
    }

    // ===== Flashcard Routes =====

    private static void registerFlashcardRoutes(Javalin app) {

        // Generate flashcards from file or text
        app.post("/api/flashcard/generate", ctx -> {
            String contentType = ctx.contentType();
            com.example.rag.flashcard.FlashcardStore.Deck deck;
            List<com.example.rag.flashcard.FlashcardStore.Card> cards;

            if (contentType != null && contentType.contains("multipart/form-data")) {
                // File upload
                var uploadedFile = ctx.uploadedFile("file");
                if (uploadedFile == null) {
                    ctx.status(400).json(Map.of("error", "No file uploaded"));
                    return;
                }
                String cardCountStr = ctx.formParam("cardCount");
                String difficulty = nvl(ctx.formParam("difficulty"), "intermediate");
                int cardCount = cardCountStr != null ? Integer.parseInt(cardCountStr) : 20;

                // Save temp file and parse
                var tempFile = java.nio.file.Files.createTempFile("fc-", uploadedFile.filename());
                try (var is = uploadedFile.content()) {
                    java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                String text;
                try {
                    text = com.example.rag.parser.AutoDocumentParser.load(tempFile).text();
                } finally {
                    java.nio.file.Files.deleteIfExists(tempFile);
                }

                var request = new com.example.rag.flashcard.FlashcardGenerator.GenerateRequest(
                        text, uploadedFile.filename(), cardCount, difficulty);
                var generated = flashcardGenerator.generate(request);
                deck = flashcardStore.createDeck(
                        uploadedFile.filename().replaceAll("\\.[^.]+$", ""), null, uploadedFile.filename());
                cards = new ArrayList<>();
                for (var gc : generated) {
                    cards.add(flashcardStore.createCard(deck.id(), gc.front(), gc.back(), gc.tags(), gc.difficulty()));
                }
            } else {
                // Text input
                Map<String, Object> body = MAPPER.readValue(ctx.body(), MAP_TYPE);
                String text = (String) body.get("text");
                if (text == null || text.isBlank()) {
                    ctx.status(400).json(Map.of("error", "text is required"));
                    return;
                }
                String title = (String) body.getOrDefault("title", "粘贴文本");
                int cardCount = body.get("cardCount") instanceof Number
                        ? ((Number) body.get("cardCount")).intValue() : 20;
                String difficulty = (String) body.getOrDefault("difficulty", "intermediate");

                var request = new com.example.rag.flashcard.FlashcardGenerator.GenerateRequest(
                        text, null, cardCount, difficulty);
                var generated = flashcardGenerator.generate(request);
                deck = flashcardStore.createDeck(title, null, null);
                cards = new ArrayList<>();
                for (var gc : generated) {
                    cards.add(flashcardStore.createCard(deck.id(), gc.front(), gc.back(), gc.tags(), gc.difficulty()));
                }
            }

            flashcardStore.updateDeckStats(deck.id());
            deck = flashcardStore.getDeck(deck.id());
            ctx.json(Map.of("deck", deck, "cards", cards));
        });

        // List decks
        app.get("/api/flashcard/decks", ctx -> {
            var decks = flashcardStore.listDecks();
            ctx.json(decks);
        });

        // Get deck detail
        app.get("/api/flashcard/decks/{id}", ctx -> {
            String id = ctx.pathParam("id");
            var deck = flashcardStore.getDeck(id);
            if (deck == null) { ctx.status(404).json(Map.of("error", "Deck not found")); return; }
            var cards = flashcardStore.listCardsByDeck(id);
            ctx.json(Map.of("deck", deck, "cards", cards));
        });

        // Update card
        app.put("/api/flashcard/cards/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, Object> body = MAPPER.readValue(ctx.body(), MAP_TYPE);
            String front = (String) body.get("front");
            String back = (String) body.get("back");
            @SuppressWarnings("unchecked")
            List<String> tags = body.get("tags") instanceof List ? (List<String>) body.get("tags") : List.of();
            int difficulty = body.get("difficulty") instanceof Number ? ((Number) body.get("difficulty")).intValue() : 2;
            var card = flashcardStore.updateCard(id, front, back, tags, difficulty);
            if (card == null) { ctx.status(404).json(Map.of("error", "Card not found")); return; }
            ctx.json(card);
        });

        // Delete deck
        app.delete("/api/flashcard/decks/{id}", ctx -> {
            flashcardStore.deleteDeck(ctx.pathParam("id"));
            ctx.json(Map.of("ok", true));
        });

        // Delete card
        app.delete("/api/flashcard/cards/{id}", ctx -> {
            var card = flashcardStore.getCard(ctx.pathParam("id"));
            flashcardStore.deleteCard(ctx.pathParam("id"));
            if (card != null) flashcardStore.updateDeckStats(card.deckId());
            ctx.json(Map.of("ok", true));
        });

        // Import deck from uploaded JSON file
        app.post("/api/flashcard/import", ctx -> {
            var body = ctx.bodyAsClass(Map.class);
            String title = (String) body.getOrDefault("title", "导入卡组");
            String description = (String) body.getOrDefault("description", "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cardMaps = body.get("cards") instanceof List ? (List<Map<String, Object>>) body.get("cards") : List.of();
            if (cardMaps.isEmpty()) {
                ctx.status(400).json(Map.of("error", "卡片列表为空"));
                return;
            }
            List<com.example.rag.flashcard.FlashcardStore.Card> cards = new ArrayList<>();
            for (var cm : cardMaps) {
                String front = (String) cm.getOrDefault("front", "");
                String back = (String) cm.getOrDefault("back", "");
                @SuppressWarnings("unchecked")
                List<String> tags = cm.get("tags") instanceof List ? (List<String>) cm.get("tags") : List.of();
                int diff = cm.get("difficulty") instanceof Number ? ((Number) cm.get("difficulty")).intValue() : 2;
                cards.add(new com.example.rag.flashcard.FlashcardStore.Card("", "", front, back, tags, diff, 0, 0));
            }
            var deck = flashcardStore.importFromJson(title, description, cards);
            if (deck == null) {
                ctx.status(500).json(Map.of("error", "导入失败"));
                return;
            }
            ctx.json(deck);
        });

        // Export deck as PDF
        app.get("/api/flashcard/decks/{id}/export", ctx -> {
            String id = ctx.pathParam("id");
            var deck = flashcardStore.getDeck(id);
            if (deck == null) { ctx.status(404).json(Map.of("error", "Deck not found")); return; }
            var cards = flashcardStore.listCardsByDeck(id);
            if (cards.isEmpty()) { ctx.status(400).json(Map.of("error", "No cards to export")); return; }

            byte[] pdfBytes = generateFlashcardPdf(deck.title(), deck.sourceFile(), cards);
            String safeName = deck.title().replaceAll("[\\\\/:*?\"<>|]", "_");
            String encodedName = java.net.URLEncoder.encode(safeName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "attachment; filename=\"flashcards.pdf\"; filename*=UTF-8''" + encodedName + ".pdf");
            ctx.result(pdfBytes);
        });
    }

    private static byte[] generateFlashcardPdf(String title, String sourceFile, List<?> cards) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = loadCJKFont(doc);
            PDFont boldFont = loadCJKBoldFont(doc);

            float margin = 55;
            float pageWidth = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();
            float contentWidth = pageWidth - margin * 2;
            float bodySize = 10.5f;
            float bodyLineH = bodySize * 1.75f;
            float labelSize = 9f;
            float labelLineH = labelSize * 1.5f;

            PDPage page = new PDPage(PDRectangle.A4);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = pageHeight - margin;

            // ── Cover area ──
            // Orange accent bar on left
            cs.setNonStrokingColor(Color.decode("#D97B2B"));
            cs.addRect(margin - 16, pageHeight - margin - 6, 4, 42);
            cs.fill();

            // Title
            cs.beginText();
            cs.setFont(boldFont, 20f);
            cs.setNonStrokingColor(Color.decode("#2D2520"));
            cs.newLineAtOffset(margin, pageHeight - margin + 8);
            cs.showText(title);
            cs.endText();
            y = pageHeight - margin - 16;

            // Subtitle
            cs.beginText();
            cs.setFont(font, 9.5f);
            cs.setNonStrokingColor(Color.decode("#8B7E74"));
            cs.newLineAtOffset(margin, y);
            String subtitle = cards.size() + " 张闪卡";
            if (sourceFile != null && !sourceFile.isBlank()) subtitle += "  ·  " + sourceFile;
            cs.showText(subtitle);
            cs.endText();
            y -= 10;

            // Title underline
            cs.setStrokingColor(Color.decode("#D97B2B"));
            cs.setLineWidth(1.2f);
            cs.moveTo(margin, y);
            cs.lineTo(pageWidth - margin, y);
            cs.stroke();
            y -= 22;

            // ── Cards ──
            for (int i = 0; i < cards.size(); i++) {
                var card = (com.example.rag.flashcard.FlashcardStore.Card) cards.get(i);
                String question = card.front();
                String answer = card.back();

                float qH = estimateTextHeight(font, bodySize, question, contentWidth, bodyLineH);
                float aH = estimateTextHeight(font, bodySize, answer, contentWidth, bodyLineH);
                float tagsH = (!card.tags().isEmpty()) ? labelLineH + 4 : 0;
                // label + gap + text + gap + separator + gap + label + gap + text + tags + bottom padding
                float cardH = labelLineH + 4 + qH + 8 + aH + tagsH + 6;

                // New page if needed
                if (y - cardH < margin + 20) {
                    cs.close();
                    doc.addPage(page);
                    page = new PDPage(PDRectangle.A4);
                    cs = new PDPageContentStream(doc, page);
                    y = pageHeight - margin;
                }

                // ── Card background ──
                float cardTop = y + 4;
                float cardBottom = y - cardH + 10;
                // Light warm background
                cs.setNonStrokingColor(Color.decode("#FDF9F3"));
                cs.addRect(margin - 8, cardBottom, contentWidth + 16, cardTop - cardBottom);
                cs.fill();
                // Left accent stripe
                cs.setNonStrokingColor(Color.decode("#D97B2B"));
                cs.addRect(margin - 8, cardBottom, 3, cardTop - cardBottom);
                cs.fill();

                // ── Question ──
                cs.beginText();
                cs.setFont(boldFont, labelSize);
                cs.setNonStrokingColor(Color.decode("#D97B2B"));
                cs.newLineAtOffset(margin + 2, y);
                cs.showText("Q " + (i + 1));
                cs.endText();
                y -= labelLineH + 3;

                y = drawWrappedText(cs, font, bodySize, Color.decode("#2D2520"), question, margin + 2, y, contentWidth - 4, bodyLineH);
                y -= 6;

                // ── Thin divider ──
                cs.setStrokingColor(Color.decode("#E8DDD0"));
                cs.setLineWidth(0.4f);
                cs.moveTo(margin + 6, y + 4);
                cs.lineTo(margin + 40, y + 4);
                cs.stroke();
                y -= 2;

                // ── Answer ──
                cs.beginText();
                cs.setFont(boldFont, labelSize);
                cs.setNonStrokingColor(Color.decode("#2D8659"));
                cs.newLineAtOffset(margin + 2, y);
                cs.showText("A");
                cs.endText();
                y -= labelLineH + 3;

                y = drawWrappedText(cs, font, bodySize, Color.decode("#3D3028"), answer, margin + 2, y, contentWidth - 4, bodyLineH);

                // ── Tags ──
                if (!card.tags().isEmpty()) {
                    y -= 4;
                    cs.beginText();
                    cs.setFont(font, 7.5f);
                    cs.setNonStrokingColor(Color.decode("#8B7E74"));
                    cs.newLineAtOffset(margin + 2, y);
                    cs.showText(String.join("   ·   ", card.tags()));
                    cs.endText();
                }

                y -= 18;
            }

            // ── Footer on last page ──
            if (y > margin + 30) {
                cs.setStrokingColor(Color.decode("#E8DDD0"));
                cs.setLineWidth(0.3f);
                cs.moveTo(margin, y + 8);
                cs.lineTo(pageWidth - margin, y + 8);
                cs.stroke();
                cs.beginText();
                cs.setFont(font, 7.5f);
                cs.setNonStrokingColor(Color.decode("#B8A898"));
                cs.newLineAtOffset(margin, y);
                cs.showText("文枢 · 藏书阁  WenShu  |  " + java.time.LocalDate.now());
                cs.endText();
            }

            cs.close();
            doc.addPage(page);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static PDFont loadCJKFont(PDDocument doc) {
        // Prefer .ttf over .ttc to avoid FontBox TTC parser warnings
        String[] fontPaths = {
            "C:/Windows/Fonts/simhei.ttf",   // SimHei (standalone .ttf, no warning)
            "C:/Windows/Fonts/msyh.ttc",     // Microsoft YaHei (.ttc, may warn)
            "C:/Windows/Fonts/simsun.ttc",   // SimSun (.ttc)
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        };
        for (String path : fontPaths) {
            try { return PDType0Font.load(doc, new java.io.File(path)); }
            catch (Exception ignored) {}
        }
        try { return PDType0Font.load(doc, new java.io.File("C:/Windows/Fonts/arial.ttf")); }
        catch (Exception ignored) { return org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA; }
    }

    private static PDFont loadCJKBoldFont(PDDocument doc) {
        String[] fontPaths = {
            "C:/Windows/Fonts/simhei.ttf",   // SimHei (inherently bold, .ttf)
            "C:/Windows/Fonts/msyhbd.ttc",   // YaHei Bold (.ttc)
            "C:/Windows/Fonts/msyh.ttc",     // YaHei regular
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
        };
        for (String path : fontPaths) {
            try { return PDType0Font.load(doc, new java.io.File(path)); }
            catch (Exception ignored) {}
        }
        return loadCJKFont(doc);
    }

    private static float drawWrappedText(PDPageContentStream cs, PDFont font, float fontSize,
                                          Color color, String text, float x, float y,
                                          float maxWidth, float lineHeight) throws IOException {
        // Split by explicit newlines first (from \n\n separator in card back)
        String[] paragraphs = text.split("\\n\\n|\\n");
        boolean first = true;
        for (String para : paragraphs) {
            if (!first) { y -= lineHeight * 0.5f; } // extra gap between paragraphs
            first = false;
            y = drawSingleParagraph(cs, font, fontSize, color, para, x, y, maxWidth, lineHeight);
        }
        return y;
    }

    private static float drawSingleParagraph(PDPageContentStream cs, PDFont font, float fontSize,
                                              Color color, String text, float x, float y,
                                              float maxWidth, float lineHeight) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);

        StringBuilder line = new StringBuilder();
        for (String word : text.split("(?<=\\s)|(?=[\\s])|(?<=[\\u4e00-\\u9fff])|(?=[\\u4e00-\\u9fff])")) {
            if (word.isEmpty()) continue;
            String test = line.toString() + word;
            float width;
            try { width = font.getStringWidth(test) / 1000f * fontSize; }
            catch (Exception e) { width = test.length() * fontSize * 0.6f; }

            if (width > maxWidth && line.length() > 0) {
                cs.showText(line.toString());
                y -= lineHeight;
                cs.newLineAtOffset(0, -lineHeight);
                line = new StringBuilder(word.trim());
            } else {
                line.append(word);
            }
        }
        if (line.length() > 0) {
            cs.showText(line.toString());
            y -= lineHeight;
        }
        cs.endText();
        return y;
    }

    private static float estimateTextHeight(PDFont font, float fontSize, String text,
                                             float maxWidth, float lineHeight) {
        String[] paragraphs = text.split("\\n\\n|\\n");
        float total = 0;
        for (String para : paragraphs) {
            float w;
            try { w = font.getStringWidth(para) / 1000f * fontSize; }
            catch (Exception e) { w = para.length() * fontSize * 0.6f; }
            total += Math.max(1, (int) Math.ceil(w / maxWidth)) * lineHeight;
        }
        // Extra spacing between paragraphs
        total += (paragraphs.length - 1) * lineHeight * 0.5f;
        return total;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static List<String> parseToolNamesFromJson(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> names = new ArrayList<>();
        for (JsonNode item : node) {
            String name = item.asText("").trim();
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    // ===== Knowledge Graph Routes =====

    private static void registerGraphRoutes(Javalin app) {

        // Generate graph from document — synchronous, returns directly
        app.post("/api/graph/generate", ctx -> {
            JsonNode root = MAPPER.readTree(ctx.body());
            String sourceFile = root.path("sourceFile").asText("");
            int maxNodes = root.path("maxNodes").asInt(30);
            int maxEdges = root.path("maxEdges").asInt(50);

            if (sourceFile.isBlank()) {
                ctx.status(400).json(Map.of("error", "sourceFile is required"));
                return;
            }

            Path docPath = Path.of("knowledge").resolve(sourceFile.replaceAll("[\\\\/]", "_"));
            if (!Files.exists(docPath)) {
                ctx.status(404).json(Map.of("error", "Document not found: " + sourceFile));
                return;
            }

            String text;
            try {
                Document doc = AutoDocumentParser.load(docPath);
                text = doc.text();
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Failed to parse document: " + e.getMessage()));
                return;
            }

            if (text == null || text.isBlank()) {
                ctx.status(400).json(Map.of("error", "Document has no text content"));
                return;
            }

            // Synchronous extraction
            try {
                String title = sourceFile.replaceAll("\\.[^.]+$", "");
                var request = new KnowledgeGraphExtractor.ExtractRequest(text, sourceFile, maxNodes, maxEdges);
                var extraction = graphExtractor.extract(request);
                var result = graphStore.importFromExtraction(title, sourceFile, extraction);
                ctx.json(Map.of("graphId", result.id(), "status", "ready", "nodeCount", result.nodeCount(), "edgeCount", result.edgeCount()));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Extraction failed: " + e.getMessage()));
            }
        });

        // Generate graph from ALL documents — SSE streaming progress
        app.post("/api/graph/generate-all", ctx -> {
            JsonNode root = MAPPER.readTree(ctx.body());
            int maxNodesPerDoc = root.path("maxNodesPerDoc").asInt(20);
            int maxEdgesPerDoc = root.path("maxEdgesPerDoc").asInt(30);

            Path knowledgeDir = Path.of("knowledge");
            if (!Files.isDirectory(knowledgeDir)) {
                ctx.status(400).json(Map.of("error", "Knowledge base is empty"));
                return;
            }

            List<Path> docs;
            try (Stream<Path> paths = Files.list(knowledgeDir)) {
                docs = paths.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .toList();
            }

            if (docs.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No documents found"));
                return;
            }

            var graph = graphStore.getOrCreateMainGraph();
            String graphId = graph.id();
            int totalDocs = docs.size();

            // SSE response
            ctx.res().setContentType("text/event-stream; charset=UTF-8");
            ctx.res().setHeader("Cache-Control", "no-cache");
            ctx.res().setHeader("Connection", "keep-alive");
            ctx.res().setHeader("X-Accel-Buffering", "no");
            var writer = ctx.res().getWriter();

            int processed = 0;
            int failed = 0;
            for (Path docPath : docs) {
                try {
                    String text = AutoDocumentParser.load(docPath).text();
                    if (text == null || text.isBlank()) { processed++; continue; }
                    if (text.length() > 8000) text = text.substring(0, 8000) + "...";

                    var request = new KnowledgeGraphExtractor.ExtractRequest(
                            text, docPath.getFileName().toString(), maxNodesPerDoc, maxEdgesPerDoc);
                    var extraction = graphExtractor.extract(request);
                    graphStore.mergeIntoGraph(graphId, extraction, docPath.getFileName().toString());
                    processed++;
                } catch (Exception e) {
                    failed++;
                    processed++;
                    System.err.println("[WARN] Failed to extract from " + docPath.getFileName() + ": " + e.getMessage());
                }

                // Send progress event
                try {
                    writer.write("event: progress\ndata: " + MAPPER.writeValueAsString(Map.of(
                            "processed", processed, "total", totalDocs, "failed", failed,
                            "currentFile", docPath.getFileName().toString()
                    )) + "\n\n");
                    writer.flush();
                } catch (Exception ignored) {}
            }

            // Send done event
            var finalGraph = graphStore.getGraph(graphId);
            try {
                writer.write("event: done\ndata: " + MAPPER.writeValueAsString(Map.of(
                        "graphId", graphId, "status", finalGraph != null ? finalGraph.status() : "ready",
                        "processed", processed, "failed", failed,
                        "nodeCount", finalGraph != null ? finalGraph.nodeCount() : 0,
                        "edgeCount", finalGraph != null ? finalGraph.edgeCount() : 0
                )) + "\n\n");
                writer.flush();
            } catch (Exception ignored) {}
        });

        // Incrementally merge a document into an existing graph
        app.post("/api/graph/graphs/{id}/merge", ctx -> {
            String graphId = ctx.pathParam("id");
            JsonNode root = MAPPER.readTree(ctx.body());
            String sourceFile = root.path("sourceFile").asText("");
            int maxNodes = root.path("maxNodes").asInt(20);
            int maxEdges = root.path("maxEdges").asInt(30);

            var graph = graphStore.getGraph(graphId);
            if (graph == null) {
                ctx.status(404).json(Map.of("error", "Graph not found"));
                return;
            }
            if (sourceFile.isBlank()) {
                ctx.status(400).json(Map.of("error", "sourceFile is required"));
                return;
            }

            Path docPath = Path.of("knowledge").resolve(sourceFile.replaceAll("[\\\\/]", "_"));
            if (!Files.exists(docPath)) {
                ctx.status(404).json(Map.of("error", "Document not found"));
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    String text = AutoDocumentParser.load(docPath).text();
                    if (text == null || text.isBlank()) return;
                    if (text.length() > 8000) text = text.substring(0, 8000) + "...";
                    var request = new KnowledgeGraphExtractor.ExtractRequest(text, sourceFile, maxNodes, maxEdges);
                    var extraction = graphExtractor.extract(request);
                    graphStore.mergeIntoGraph(graphId, extraction, sourceFile);
                } catch (Exception e) {
                    System.err.println("[WARN] Merge failed for " + sourceFile + ": " + e.getMessage());
                }
            });

            ctx.json(Map.of("status", "merging", "message", "Merging " + sourceFile + " into graph"));
        });

        // List graphs
        app.get("/api/graph/graphs", ctx -> ctx.json(graphStore.listGraphs()));

        // Get graph detail (with nodes and edges)
        app.get("/api/graph/graphs/{id}", ctx -> {
            String id = ctx.pathParam("id");
            var graph = graphStore.getGraph(id);
            if (graph == null) {
                ctx.status(404).json(Map.of("error", "Graph not found"));
                return;
            }
            var nodes = graphStore.listNodesByGraph(id);
            var edges = graphStore.listEdgesByGraph(id);
            ctx.json(Map.of("graph", graph, "nodes", nodes, "edges", edges));
        });

        // Delete graph
        app.delete("/api/graph/graphs/{id}", ctx -> {
            graphStore.deleteGraph(ctx.pathParam("id"));
            ctx.json(Map.of("ok", true));
        });

        // Graph Q&A
        app.post("/api/graph/graphs/{id}/qa", ctx -> {
            String id = ctx.pathParam("id");
            JsonNode root = MAPPER.readTree(ctx.body());
            String question = root.path("question").asText("");
            if (question.isBlank()) {
                ctx.status(400).json(Map.of("error", "question is required"));
                return;
            }

            var graph = graphStore.getGraph(id);
            if (graph == null) {
                ctx.status(404).json(Map.of("error", "Graph not found"));
                return;
            }

            var nodes = graphStore.listNodesByGraph(id);
            var edges = graphStore.listEdgesByGraph(id);

            String graphContext = buildGraphContext(graph, nodes, edges);
            String template = PromptRegistry.getTemplate("knowledge_graph_qa");
            if (template == null || template.isBlank()) {
                template = "基于以下图谱上下文回答问题：\n\n${graphContext}\n\n问题：";
            }
            String systemPrompt = template.replace("${graphContext}", graphContext);

            var chatModel = ragService.getChatModel();
            if (chatModel == null) {
                ctx.status(500).json(Map.of("error", "LLM not available"));
                return;
            }
            String answer = chatModel.chat(systemPrompt + "\n\n" + question);
            answer = RagService.stripThinkTags(answer);
            ctx.json(Map.of("answer", answer));
        });

        // Suggest relationships between two nodes
        app.post("/api/graph/graphs/{id}/suggest", ctx -> {
            String id = ctx.pathParam("id");
            JsonNode root = MAPPER.readTree(ctx.body());
            String sourceNodeId = root.path("sourceNodeId").asText("");
            String targetNodeId = root.path("targetNodeId").asText("");
            if (sourceNodeId.isBlank() || targetNodeId.isBlank()) {
                ctx.status(400).json(Map.of("error", "sourceNodeId and targetNodeId are required"));
                return;
            }

            var sourceNode = graphStore.getNode(sourceNodeId);
            var targetNode = graphStore.getNode(targetNodeId);
            if (sourceNode == null || targetNode == null) {
                ctx.status(404).json(Map.of("error", "Node not found"));
                return;
            }

            String nodeInfo = "节点A: " + sourceNode.label() + " (" + sourceNode.nodeType() + ") - " + sourceNode.description()
                    + "\n节点B: " + targetNode.label() + " (" + targetNode.nodeType() + ") - " + targetNode.description();

            // Get related document context
            var graph = graphStore.getGraph(id);
            String docContext = graph != null && graph.sourceFile() != null
                    ? getDocumentSnippet(graph.sourceFile(), 1000) : "";

            String template = PromptRegistry.getTemplate("knowledge_graph_suggest");
            if (template == null || template.isBlank()) {
                template = "基于以下节点信息和文档上下文，推理两个节点之间可能的关系：\n\n${nodeInfo}\n\n${docContext}";
            }
            String systemPrompt = template
                    .replace("${nodeInfo}", nodeInfo)
                    .replace("${docContext}", docContext);

            var chatModel = ragService.getChatModel();
            if (chatModel == null) {
                ctx.status(500).json(Map.of("error", "LLM not available"));
                return;
            }
            String raw = chatModel.chat(systemPrompt);
            String cleaned = RagService.stripThinkTags(raw);

            // Parse suggestions
            try {
                String json = cleaned.trim();
                if (json.startsWith("```")) {
                    int start = json.indexOf('[');
                    int end = json.lastIndexOf(']');
                    if (start >= 0 && end > start) json = json.substring(start, end + 1);
                }
                int arrStart = json.indexOf('[');
                int arrEnd = json.lastIndexOf(']');
                if (arrStart >= 0 && arrEnd > arrStart) {
                    json = json.substring(arrStart, arrEnd + 1);
                }
                ctx.json(Map.of("suggestions", MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {})));
            } catch (Exception e) {
                ctx.json(Map.of("suggestions", List.of(), "raw", cleaned));
            }
        });

        // Export graph as JSON
        app.get("/api/graph/graphs/{id}/export", ctx -> {
            String id = ctx.pathParam("id");
            var graph = graphStore.getGraph(id);
            if (graph == null) {
                ctx.status(404).json(Map.of("error", "Graph not found"));
                return;
            }
            var nodes = graphStore.listNodesByGraph(id);
            var edges = graphStore.listEdgesByGraph(id);
            ctx.json(Map.of("graph", graph, "nodes", nodes, "edges", edges));
        });
    }

    private static String buildGraphContext(KnowledgeGraphStore.Graph graph,
                                             List<KnowledgeGraphStore.Node> nodes,
                                             List<KnowledgeGraphStore.Edge> edges) {
        StringBuilder sb = new StringBuilder();
        sb.append("图谱: ").append(graph.title()).append("\n");
        if (graph.description() != null) sb.append("概要: ").append(graph.description()).append("\n");
        sb.append("\n节点:\n");
        for (var node : nodes) {
            sb.append("- ").append(node.label());
            if (node.nodeType() != null) sb.append(" [").append(node.nodeType()).append("]");
            if (node.description() != null) sb.append(": ").append(node.description());
            sb.append("\n");
        }
        sb.append("\n关系:\n");
        Map<String, String> nodeLabelMap = nodes.stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgeGraphStore.Node::id, KnowledgeGraphStore.Node::label, (a, b) -> a));
        for (var edge : edges) {
            String srcLabel = nodeLabelMap.getOrDefault(edge.sourceId(), edge.sourceId());
            String tgtLabel = nodeLabelMap.getOrDefault(edge.targetId(), edge.targetId());
            sb.append("- ").append(srcLabel).append(" --[").append(edge.label()).append("]--> ").append(tgtLabel).append("\n");
        }
        return sb.toString();
    }

    private static String getDocumentSnippet(String sourceFile, int maxChars) {
        try {
            Path docPath = Path.of("knowledge").resolve(sourceFile.replaceAll("[\\\\/]", "_"));
            if (!Files.exists(docPath)) return "";
            String text = AutoDocumentParser.load(docPath).text();
            if (text != null && text.length() > maxChars) text = text.substring(0, maxChars) + "...";
            return text != null ? text : "";
        } catch (Exception e) {
            return "";
        }
    }
}
