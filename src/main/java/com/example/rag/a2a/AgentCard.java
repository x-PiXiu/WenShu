package com.example.rag.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Card - Agent 的身份和能力描述
 * 遵循 A2A 协议规范的简化版
 */
public class AgentCard {

    private String name;
    private String description;
    private String url;
    private String version;
    private Capabilities capabilities;
    private List<Skill> skills;
    private Map<String, Object> securitySchemes;

    public static AgentCard create(String agentName, String description, String url) {
        AgentCard card = new AgentCard();
        card.name = agentName;
        card.description = description;
        card.url = url;
        card.version = "1.0.0";
        card.capabilities = new Capabilities(true, false, false);
        card.skills = List.of(
                new Skill("rag-query", "藏书阁问答",
                        "基于企业知识库的智能问答，检索并生成精准答案",
                        List.of("knowledge", "Q&A", "RAG", "文枢"))
        );
        return card;
    }

    // Inner types
    public record Capabilities(boolean streaming, boolean pushNotifications,
                               boolean stateTransitionHistory) {}

    public record Skill(String id, String name, String description, List<String> tags) {}

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
    public String getVersion() { return version; }
    public Capabilities getCapabilities() { return capabilities; }
    public List<Skill> getSkills() { return skills; }
    public Map<String, Object> getSecuritySchemes() { return securitySchemes; }
}
