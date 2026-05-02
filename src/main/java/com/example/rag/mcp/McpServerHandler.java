package com.example.rag.mcp;

import com.example.rag.service.RagService;
import com.example.rag.tools.ToolEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.javalin.http.Context;

import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP (Model Context Protocol) Server Handler
 * 通过 SSE + JSON-RPC 2.0 暴露 RAG 能力给外部 AI 工具
 */
public class McpServerHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final RagService ragService;
    private final ToolEngine toolEngine;
    private final Map<String, HttpServletResponse> sessions = new ConcurrentHashMap<>();

    public McpServerHandler(RagService ragService, ToolEngine toolEngine) {
        this.ragService = ragService;
        this.toolEngine = toolEngine;
    }

    /**
     * GET /mcp/sse — 建立 SSE 连接
     */
    public void handleSse(Context ctx) throws Exception {
        String sessionId = "mcp-" + UUID.randomUUID().toString().substring(0, 8);

        ctx.res().setContentType("text/event-stream; charset=UTF-8");
        ctx.res().setHeader("Cache-Control", "no-cache");
        ctx.res().setHeader("Connection", "keep-alive");
        ctx.res().setHeader("X-Accel-Buffering", "no");

        var writer = ctx.res().getWriter();
        sessions.put(sessionId, ctx.res());

        // Send endpoint event so client knows where to POST messages
        writer.write("event: endpoint\ndata: /mcp/message?sessionId=" + sessionId + "\n\n");
        writer.flush();

        // Keep connection alive with periodic pings
        try {
            while (!ctx.res().isCommitted() && sessions.containsKey(sessionId)) {
                Thread.sleep(15000);
                writer.write("event: ping\ndata: " + System.currentTimeMillis() + "\n\n");
                writer.flush();
            }
        } catch (Exception ignored) {
        } finally {
            sessions.remove(sessionId);
        }
    }

    /**
     * POST /mcp/message — 处理客户端 JSON-RPC 消息
     */
    public void handleMessage(Context ctx) throws Exception {
        String sessionId = ctx.queryParam("sessionId");
        Map<String, Object> request = MAPPER.readValue(ctx.body(), Map.class);
        String method = (String) request.get("method");
        Object id = request.get("id");

        Map<String, Object> result;
        try {
            result = switch (method) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(request);
                case "ping" -> Map.of("status", "ok");
                default -> Map.of("error", Map.of("code", -32601, "message", "Method not found: " + method));
            };
        } catch (Exception e) {
            sendToSession(sessionId, buildResponse(id, null, Map.of("code", -32603, "message", e.getMessage())));
            ctx.status(200).result("ok");
            return;
        }

        Object error = result.remove("error");
        Map<String, Object> response = error != null
                ? buildResponse(id, null, error)
                : buildResponse(id, result, null);

        sendToSession(sessionId, response);
        ctx.status(200).result("ok");
    }

    private Map<String, Object> handleInitialize(Map<String, Object> request) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "WenShu RAG Server");
        info.put("version", "1.0.0");

        return Map.of("protocolVersion", PROTOCOL_VERSION, "capabilities", capabilities, "serverInfo", info);
    }

    private Map<String, Object> handleToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // RAG query tool (always available)
        tools.add(Map.of(
                "name", "rag_query",
                "description", "基于知识库的智能问答：检索相关文档并生成精准答案",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "question", Map.of("type", "string", "description", "用户的问题")
                        ),
                        "required", List.of("question")
                )
        ));

        // Document search tool
        tools.add(Map.of(
                "name", "search_documents",
                "description", "在知识库中搜索相关文档片段",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "搜索关键词")
                        ),
                        "required", List.of("query")
                )
        ));

        // Knowledge stats tool
        tools.add(Map.of(
                "name", "get_knowledge_stats",
                "description", "获取知识库的统计信息",
                "inputSchema", Map.of("type", "object", "properties", Map.of())
        ));

        // Expose @Tool tools from ToolEngine
        if (toolEngine != null) {
            for (ToolSpecification spec : toolEngine.getSpecifications()) {
                if ("webSearch".equals(spec.name())) {
                    tools.add(Map.of(
                            "name", "web_search",
                            "description", spec.description(),
                            "inputSchema", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "query", Map.of("type", "string", "description", "搜索关键词")
                                    ),
                                    "required", List.of("query")
                            )
                    ));
                }
            }
        }

        return Map.of("tools", tools);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Map<String, Object> request) {
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        String result;
        try {
            result = switch (toolName) {
                case "rag_query" -> {
                    String question = (String) arguments.get("question");
                    RagService.RagAnswer answer = ragService.ask(question);
                    StringBuilder sb = new StringBuilder(answer.answer());
                    if (!answer.sources().isEmpty()) {
                        sb.append("\n\n来源:\n");
                        for (var src : answer.sources()) {
                            sb.append("- ").append(src.source() != null ? src.source() : "文档").append("\n");
                        }
                    }
                    yield sb.toString();
                }
                case "search_documents" -> {
                    String query = (String) arguments.get("query");
                    var answer = ragService.ask(query);
                    StringBuilder sb = new StringBuilder();
                    for (var src : answer.sources()) {
                        sb.append("[").append(src.index()).append("] ");
                        sb.append(src.text()).append(" (").append(src.source()).append(")\n");
                    }
                    yield sb.isEmpty() ? "未找到相关文档" : sb.toString();
                }
                case "get_knowledge_stats" -> {
                    var stats = ragService.getStats();
                    yield "文档: " + stats.documentCount() + ", 分段: " + stats.segmentCount()
                            + ", 模型: " + stats.llmModel() + ", 向量库: " + stats.vectorStoreType();
                }
                case "web_search" -> {
                    if (toolEngine != null) {
                        yield toolEngine.execute(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                .name("webSearch")
                                .arguments(MAPPER.writeValueAsString(arguments))
                                .build());
                    }
                    yield "Web search not available";
                }
                default -> "Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            return Map.of("isError", true, "content", List.of(
                    Map.of("type", "text", "text", "Error: " + e.getMessage())));
        }

        return Map.of("content", List.of(Map.of("type", "text", "text", result)));
    }

    private void sendToSession(String sessionId, Map<String, Object> response) {
        jakarta.servlet.http.HttpServletResponse res = sessions.get(sessionId);
        if (res == null) return;
        try {
            var writer = res.getWriter();
            writer.write("event: message\ndata: " + MAPPER.writeValueAsString(response) + "\n\n");
            writer.flush();
        } catch (Exception ignored) {}
    }

    private Map<String, Object> buildResponse(Object id, Object result, Object error) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.put("id", id);
        if (result != null) resp.put("result", result);
        if (error != null) resp.put("error", error);
        return resp;
    }
}
