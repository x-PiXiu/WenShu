package com.example.rag.a2a;

import com.example.rag.service.RagService;
import com.example.rag.tools.ToolEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * A2A Skill 注册表：统一管理所有可暴露的技能
 * 每个 Skill 对应一个工具或复合流程，可供外部 Agent 发现和调用
 */
public class SkillRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, SkillEntry> skills = new LinkedHashMap<>();
    private final RagService ragService;
    private final ToolEngine toolEngine;
    private volatile AgentDiscovery agentDiscovery;

    public record SkillEntry(
            String id,
            String name,
            String description,
            List<String> tags,
            String toolName  // 对应的 @Tool 方法名，null 表示复合技能
    ) {}

    public SkillRegistry(RagService ragService, ToolEngine toolEngine) {
        this.ragService = ragService;
        this.toolEngine = toolEngine;
        registerDefaults();
    }

    public void setAgentDiscovery(AgentDiscovery discovery) {
        this.agentDiscovery = discovery;
    }

    private void registerDefaults() {
        // 复合技能
        register("rag-query", "藏书阁问答", "基于知识库的智能问答，检索并生成精准答案",
                List.of("knowledge", "Q&A", "RAG", "文枢"), null);

        // 工具技能 — 对应 @Tool 方法
        register("document-search", "文档搜索", "在知识库中搜索相关文档片段",
                List.of("search", "document"), "searchKnowledge");
        register("document-read", "读取文档", "读取知识库中指定文档的完整文本",
                List.of("read", "document"), "readDocument");
        register("document-create", "创建文档", "创建新文档并自动索引到知识库",
                List.of("create", "document"), "createDocument");
        register("document-update", "更新文档", "更新已有文档内容并重新索引",
                List.of("update", "document"), "updateDocument");
        register("document-info", "文档详情", "获取文档的元数据和详细信息",
                List.of("info", "document"), "getDocumentInfo");
        register("web-search", "互联网搜索", "在互联网上搜索信息，补充知识库内容",
                List.of("web", "search", "internet"), "webSearch");
        register("blog-search", "博客搜索", "搜索博客中已发布的文章",
                List.of("blog", "search"), "blogSearch");
        register("blog-write", "博客写作", "创建新的博客文章",
                List.of("blog", "write"), "blogWrite");
        register("summarize", "内容摘要", "使用 LLM 生成文本内容的摘要",
                List.of("summary", "AI"), "blogSummarize");
        register("translate", "翻译", "将文本翻译为指定语言",
                List.of("translate", "language"), "translate");
        register("calculate", "计算器", "计算数学表达式",
                List.of("math", "calculate"), "calculate");
    }

    public void register(String id, String name, String description, List<String> tags, String toolName) {
        skills.put(id, new SkillEntry(id, name, description, tags, toolName));
    }

    public List<SkillEntry> listSkills() {
        return List.copyOf(skills.values());
    }

    public List<AgentCard.Skill> toAgentSkills() {
        return skills.values().stream()
                .map(s -> new AgentCard.Skill(s.id(), s.name(), s.description(), s.tags()))
                .toList();
    }

    /**
     * 执行指定的 skill，返回结果文本
     */
    public String execute(String skillId, Map<String, Object> input) throws Exception {
        // 远程技能：委托给 AgentDiscovery
        if (agentDiscovery != null && agentDiscovery.isRemoteSkill(skillId)) {
            return agentDiscovery.callRemoteSkill(skillId, input);
        }

        // 复合技能：rag-query
        if ("rag-query".equals(skillId)) {
            String question = (String) input.get("question");
            if (question == null || question.isBlank()) throw new IllegalArgumentException("question is required");
            RagService.RagAnswer answer = ragService.ask(question);
            StringBuilder sb = new StringBuilder(answer.answer());
            if (!answer.sources().isEmpty()) {
                sb.append("\n\n来源:\n");
                for (var src : answer.sources()) {
                    sb.append("- ").append(src.source() != null ? src.source() : "文档").append("\n");
                }
            }
            return sb.toString();
        }

        // 工具技能：委托给 ToolEngine
        SkillEntry skill = skills.get(skillId);
        if (skill == null) throw new IllegalArgumentException("Unknown skill: " + skillId);
        if (skill.toolName() == null) throw new IllegalArgumentException("Skill has no tool binding: " + skillId);
        if (toolEngine == null) throw new IllegalStateException("Tool engine not available");

        return toolEngine.execute(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .name(skill.toolName())
                .arguments(MAPPER.writeValueAsString(input))
                .build());
    }
}
