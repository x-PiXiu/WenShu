package com.example.rag.mcp;

import com.example.rag.config.AppConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * MCP Client Connector：连接外部 MCP Server，获取工具列表并注册为本地工具
 * 通过 SSE 接收 server 的消息端点，通过 HTTP POST 发送 JSON-RPC 请求
 */
public class McpClientConnector {

    private static final Logger LOG = Logger.getLogger(McpClientConnector.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();

    /**
     * 连接配置中的所有 MCP Server
     */
    public void connectAll(List<AppConfiguration.McpServerConfig> servers) {
        for (AppConfiguration.McpServerConfig server : servers) {
            if (server.enabled && server.sseUrl != null && !server.sseUrl.isBlank()) {
                try {
                    connect(server.name, server.sseUrl);
                } catch (Exception e) {
                    LOG.warning("[MCP-CLIENT] Failed to connect to " + server.name + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 连接单个 MCP Server 并获取工具列表
     */
    public void connect(String name, String sseUrl) throws Exception {
        LOG.info("[MCP-CLIENT] Connecting to " + name + " at " + sseUrl);

        // Discover message endpoint from SSE
        String messageUrl = discoverEndpoint(sseUrl);
        if (messageUrl == null) {
            throw new RuntimeException("Failed to discover message endpoint from " + sseUrl);
        }

        // Resolve relative URL
        if (!messageUrl.startsWith("http")) {
            URI baseUri = URI.create(sseUrl);
            messageUrl = baseUri.getScheme() + "://" + baseUri.getAuthority() + messageUrl;
        }

        // Initialize
        JsonNode initResult = sendRpc(messageUrl, "initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "WenShu RAG Client", "version", "1.0.0")
        ));

        // Send initialized notification
        sendRpc(messageUrl, "notifications/initialized", Map.of());

        // Get tools list
        JsonNode toolsResult = sendRpc(messageUrl, "tools/list", Map.of());
        List<ExternalTool> tools = parseTools(toolsResult);

        McpConnection conn = new McpConnection(name, messageUrl, tools);
        connections.put(name, conn);

        LOG.info("[MCP-CLIENT] Connected to " + name + ": " + tools.size() + " tools available");
    }

    /**
     * 获取所有外部工具的 ToolSpecification（用于注册到 ToolEngine）
     */
    public List<ToolSpecification> getAllToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (McpConnection conn : connections.values()) {
            for (ExternalTool tool : conn.tools) {
                specs.add(tool.specification);
            }
        }
        return specs;
    }

    /**
     * 获取所有外部工具的 ToolSpecification → ToolExecutor 映射
     */
    public Map<ToolSpecification, ToolExecutor> getToolMap() {
        Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        for (McpConnection conn : connections.values()) {
            for (ExternalTool tool : conn.tools) {
                map.put(tool.specification, tool.executor);
            }
        }
        return map;
    }

    /**
     * 执行外部工具调用
     */
    public String executeTool(String toolName, Map<String, Object> arguments) {
        for (McpConnection conn : connections.values()) {
            for (ExternalTool tool : conn.tools) {
                if (tool.mcpName.equals(toolName) || tool.specification.name().equals(toolName)) {
                    return tool.execute(arguments);
                }
            }
        }
        return "Unknown tool: " + toolName;
    }

    public void disconnect(String name) {
        connections.remove(name);
        LOG.info("[MCP-CLIENT] Disconnected from " + name);
    }

    public List<Map<String, String>> listConnections() {
        List<Map<String, String>> result = new ArrayList<>();
        for (var entry : connections.entrySet()) {
            result.add(Map.of(
                    "name", entry.getKey(),
                    "url", entry.getValue().messageUrl,
                    "tools", String.valueOf(entry.getValue().tools.size())
            ));
        }
        return result;
    }

    // ===== Internal =====

    private String discoverEndpoint(String sseUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sseUrl))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Parse SSE events to find endpoint
        for (String line : response.body().split("\n")) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if (data.startsWith("/")) return data;
            }
        }
        return null;
    }

    private JsonNode sendRpc(String url, String method, Object params) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString().substring(0, 8));
        request.put("method", method);
        if (params != null && !((Map<?, ?>) params).isEmpty()) {
            request.put("params", params);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("MCP RPC failed: " + response.statusCode() + " " + response.body());
        }

        return MAPPER.readTree(response.body());
    }

    private JsonNode sendRpc(String url, String method) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", method);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                .build();

        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<ExternalTool> parseTools(JsonNode result) {
        List<ExternalTool> tools = new ArrayList<>();
        if (result == null) return tools;

        JsonNode toolsNode = result.has("result") ? result.get("result").get("tools") : result.get("tools");
        if (toolsNode == null || !toolsNode.isArray()) return tools;

        for (JsonNode toolNode : toolsNode) {
            String name = toolNode.path("name").asText("");
            String description = toolNode.path("description").asText("");

            // Build ToolSpecification with JsonObjectSchema from inputSchema
            ToolSpecification.Builder specBuilder = ToolSpecification.builder()
                    .name("mcp_" + name)
                    .description("[MCP] " + description);

            JsonNode schema = toolNode.path("inputSchema");
            JsonNode properties = schema.path("properties");
            JsonNode required = schema.path("required");

            if (properties.isObject()) {
                JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
                List<String> requiredFields = new ArrayList<>();
                if (required.isArray()) {
                    for (JsonNode r : required) requiredFields.add(r.asText());
                }
                for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> prop = it.next();
                    String propName = prop.getKey();
                    String propDesc = prop.getValue().path("description").asText("");
                    schemaBuilder.addProperty(propName, propDesc.isEmpty()
                            ? JsonStringSchema.builder().build()
                            : JsonStringSchema.builder().description(propDesc).build());
                }
                if (!requiredFields.isEmpty()) {
                    schemaBuilder.required(requiredFields);
                }
                specBuilder.parameters(schemaBuilder.build());
            }

            ToolSpecification spec = specBuilder.build();
            tools.add(new ExternalTool(name, spec, this));
        }
        return tools;
    }

    // ===== Inner Classes =====

    private static class McpConnection {
        final String name;
        final String messageUrl;
        final List<ExternalTool> tools;

        McpConnection(String name, String messageUrl, List<ExternalTool> tools) {
            this.name = name;
            this.messageUrl = messageUrl;
            this.tools = tools;
        }
    }

    /**
     * 外部 MCP 工具：持有 ToolSpecification 和对应的执行器
     */
    public class ExternalTool {
        final String mcpName;
        final ToolSpecification specification;
        final ToolExecutor executor;

        ExternalTool(String mcpName, ToolSpecification specification, McpClientConnector connector) {
            this.mcpName = mcpName;
            this.specification = specification;
            this.executor = (req, memoryId) -> {
                try {
                    Map<String, Object> args = MAPPER.readValue(req.arguments(), Map.class);
                    return execute(args);
                } catch (Exception e) {
                    return "Error executing MCP tool " + mcpName + ": " + e.getMessage();
                }
            };
        }

        String execute(Map<String, Object> arguments) {
            try {
                // Find the connection that owns this tool
                for (McpConnection conn : connections.values()) {
                    for (ExternalTool t : conn.tools) {
                        if (t == this) {
                            JsonNode result = sendRpc(conn.messageUrl, "tools/call", Map.of(
                                    "name", mcpName,
                                    "arguments", arguments != null ? arguments : Map.of()
                            ));

                            if (result != null && result.has("result")) {
                                JsonNode content = result.get("result").path("content");
                                if (content.isArray()) {
                                    StringBuilder sb = new StringBuilder();
                                    for (JsonNode item : content) {
                                        sb.append(item.path("text").asText(""));
                                    }
                                    return sb.toString();
                                }
                                return result.get("result").toString();
                            }
                            return "No result from MCP tool";
                        }
                    }
                }
                return "MCP connection not found";
            } catch (Exception e) {
                return "MCP tool error: " + e.getMessage();
            }
        }
    }
}
