package com.example.rag.graph;

import com.example.rag.prompt.PromptRegistry;
import com.example.rag.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KnowledgeGraphExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RagService ragService;

    public KnowledgeGraphExtractor(RagService ragService) {
        this.ragService = ragService;
    }

    public record ExtractRequest(String text, String sourceFile,
                                  int maxNodes, int maxEdges) {}

    public KnowledgeGraphStore.ExtractionResult extract(ExtractRequest request) {
        String template = PromptRegistry.getTemplate("knowledge_graph_extract");
        if (template == null || template.isBlank()) {
            template = DEFAULT_EXTRACT_PROMPT;
        }
        String systemPrompt = template
                .replace("${maxNodes}", String.valueOf(request.maxNodes()))
                .replace("${maxEdges}", String.valueOf(request.maxEdges()));

        String userMessage = "【文档内容】\n" + request.text();

        var chatModel = ragService.getChatModel();
        if (chatModel == null) throw new RuntimeException("LLM 模型不可用");

        String raw = chatModel.chat(systemPrompt + "\n\n" + userMessage);
        String cleaned = RagService.stripThinkTags(raw);

        return parseExtraction(cleaned);
    }

    private KnowledgeGraphStore.ExtractionResult parseExtraction(String response) {
        String json = response.trim();
        // Strip code fences
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        // Extract first JSON object
        int objStart = json.indexOf('{');
        int objEnd = json.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            json = json.substring(objStart, objEnd + 1);
        }

        try {
            Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {});
            List<KnowledgeGraphStore.ExtractedNode> nodes = parseNodes(root);
            List<KnowledgeGraphStore.ExtractedEdge> edges = parseEdges(root);
            String summary = (String) root.getOrDefault("summary", "");
            return new KnowledgeGraphStore.ExtractionResult(nodes, edges, summary);
        } catch (Exception e) {
            throw new RuntimeException("解析知识图谱 JSON 失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgeGraphStore.ExtractedNode> parseNodes(Map<String, Object> root) {
        List<KnowledgeGraphStore.ExtractedNode> nodes = new ArrayList<>();
        Object nodesObj = root.get("nodes");
        if (!(nodesObj instanceof List)) return nodes;

        for (Object item : (List<?>) nodesObj) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) item;
            String id = str(m, "id");
            String label = str(m, "label");
            String type = str(m, "type");
            String description = str(m, "description");
            String group = str(m, "group");
            Map<String, String> props = null;
            if (m.get("properties") instanceof Map) {
                props = new java.util.HashMap<>();
                for (var entry : ((Map<String, Object>) m.get("properties")).entrySet()) {
                    props.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            nodes.add(new KnowledgeGraphStore.ExtractedNode(id, label, type, description, group, props));
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgeGraphStore.ExtractedEdge> parseEdges(Map<String, Object> root) {
        List<KnowledgeGraphStore.ExtractedEdge> edges = new ArrayList<>();
        Object edgesObj = root.get("edges");
        if (!(edgesObj instanceof List)) return edges;

        for (Object item : (List<?>) edgesObj) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) item;
            String source = str(m, "source");
            String target = str(m, "target");
            String label = str(m, "label");
            String type = str(m, "type");
            double weight = m.get("weight") instanceof Number
                    ? ((Number) m.get("weight")).doubleValue()
                    : 1.0;
            edges.add(new KnowledgeGraphStore.ExtractedEdge(source, target, label, type, weight));
        }
        return edges;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static final String DEFAULT_EXTRACT_PROMPT = """
            你是「文枢·藏书阁」的知识图谱提取专家。你的任务是从文档中提取核心知识实体及其关系。

            ## 任务
            - 阅读下方【文档内容】，提取最多 ${maxNodes} 个知识实体（节点）和 ${maxEdges} 条关系（边）。
            - 优先提取核心概念、关键人物、重要技术、核心事件。

            ## 节点类型
            - person: 人物
            - org: 组织/机构
            - concept: 概念/理论
            - event: 事件
            - technology: 技术/工具
            - location: 地点
            - other: 其他

            ## 边类型
            - belongs_to: 属于
            - depends_on: 依赖
            - causes: 导致
            - part_of: 组成部分
            - related_to: 相关
            - derived_from: 派生自
            - contrasts_with: 对比

            ## 输出格式
            严格输出 JSON 对象，不要包含任何其他文字或 markdown 标记：

            {
              "nodes": [
                {
                  "id": "n1",
                  "label": "节点名称",
                  "type": "concept",
                  "description": "一句话描述",
                  "group": "分组名"
                }
              ],
              "edges": [
                {
                  "source": "n1",
                  "target": "n2",
                  "label": "关系描述",
                  "type": "related_to",
                  "weight": 1.0
                }
              ],
              "summary": "整篇文档的知识概要（2-3句话）"
            }

            ## 注意事项
            - 严格遵守 JSON 格式，不要输出 ```json 等代码围栏标记。
            - 每个节点必须有唯一的 id（如 n1, n2, n3...）。
            - edge 的 source 和 target 必须引用已定义的节点 id。
            - label 使用中文，简洁明了。
            - description 简短但信息丰富。
            - group 用于视觉聚类，将相关节点分到同一组。
            - weight 表示关系强度，1.0 为标准，更强的关系可用 1.5-2.0。
            """;
}
