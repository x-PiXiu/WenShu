package com.example.rag.a2a;

import com.example.rag.config.AppConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A2A Agent 发现服务：自动发现远程 Agent 并注册其 Skills
 * 通过 GET /a2a/v1/agent 获取远程 AgentCard，将 skills 注册到本地 SkillRegistry
 */
public class AgentDiscovery {

    private static final Logger LOG = Logger.getLogger(AgentDiscovery.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final SkillRegistry skillRegistry;
    private final Map<String, RemoteAgent> discoveredAgents = new ConcurrentHashMap<>();

    public AgentDiscovery(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 发现并注册配置中的所有远程 Agent
     */
    public void discoverAll(List<AppConfiguration.RemoteAgentConfig> agents) {
        for (AppConfiguration.RemoteAgentConfig agent : agents) {
            if (agent.enabled && agent.url != null && !agent.url.isBlank()) {
                try {
                    discover(agent.name, agent.url);
                } catch (Exception e) {
                    LOG.warning("[A2A-DISCOVERY] Failed to discover " + agent.name + " at " + agent.url + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 发现单个远程 Agent：获取 AgentCard → 注册 Skills
     */
    public void discover(String name, String baseUrl) throws Exception {
        String agentUrl = baseUrl.replaceAll("/+$", "") + "/a2a/v1/agent";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(agentUrl))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Agent card request failed: " + response.statusCode());
        }

        JsonNode card = MAPPER.readTree(response.body());
        String agentName = card.path("name").asText(name);
        String agentDescription = card.path("description").asText("");
        String rpcUrl = baseUrl.replaceAll("/+$", "") + "/a2a/v1/rpc";

        // Parse skills
        List<RemoteSkill> remoteSkills = new ArrayList<>();
        JsonNode skillsNode = card.path("skills");
        if (skillsNode.isArray()) {
            for (JsonNode skillNode : skillsNode) {
                String skillId = skillNode.path("id").asText("");
                String skillName = skillNode.path("name").asText("");
                String skillDesc = skillNode.path("description").asText("");

                if (!skillId.isEmpty()) {
                    String localId = "remote_" + sanitize(name) + "_" + skillId;
                    List<String> tags = new ArrayList<>();
                    JsonNode tagsNode = skillNode.path("tags");
                    if (tagsNode.isArray()) {
                        for (JsonNode tag : tagsNode) tags.add(tag.asText());
                    }
                    tags.add("remote");
                    tags.add(agentName);

                    remoteSkills.add(new RemoteSkill(localId, skillName,
                            "[" + agentName + "] " + skillDesc, tags, skillId, rpcUrl));

                    // Register in SkillRegistry
                    skillRegistry.register(localId, skillName,
                            "[" + agentName + "] " + skillDesc, tags, null);
                }
            }
        }

        RemoteAgent agent = new RemoteAgent(name, agentName, baseUrl, rpcUrl, remoteSkills);
        discoveredAgents.put(name, agent);

        LOG.info("[A2A-DISCOVERY] Discovered " + agentName + ": " + remoteSkills.size() + " skills");
    }

    /**
     * 调用远程 Agent 的 skill
     */
    public String callRemoteSkill(String localSkillId, Map<String, Object> input) throws Exception {
        for (RemoteAgent agent : discoveredAgents.values()) {
            for (RemoteSkill skill : agent.skills) {
                if (skill.localId.equals(localSkillId)) {
                    return executeRemoteRpc(agent.rpcUrl, skill.remoteSkillId, input);
                }
            }
        }
        throw new IllegalArgumentException("Remote skill not found: " + localSkillId);
    }

    /**
     * 检查 skillId 是否属于远程技能
     */
    public boolean isRemoteSkill(String skillId) {
        if (!skillId.startsWith("remote_")) return false;
        for (RemoteAgent agent : discoveredAgents.values()) {
            for (RemoteSkill skill : agent.skills) {
                if (skill.localId.equals(skillId)) return true;
            }
        }
        return false;
    }

    public List<Map<String, Object>> listDiscovered() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RemoteAgent agent : discoveredAgents.values()) {
            List<Map<String, String>> skillList = agent.skills.stream()
                    .map(s -> Map.of("id", s.localId, "name", s.name, "remoteSkillId", s.remoteSkillId))
                    .toList();
            result.add(Map.of("name", (Object) agent.discoveredName, "url", agent.baseUrl,
                    "skills", skillList));
        }
        return result;
    }

    // ===== Internal =====

    private String executeRemoteRpc(String rpcUrl, String remoteSkillId, Map<String, Object> input) throws Exception {
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", taskId);
        params.put("skillId", remoteSkillId);
        params.put("input", input != null ? input : Map.of());

        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString().substring(0, 8),
                "method", "tasks/send",
                "params", params
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(rpcUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(rpcRequest)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode result = MAPPER.readTree(response.body());

        // Extract result from response
        JsonNode taskResult = result.path("result").path("task");
        String state = taskResult.path("status").path("state").asText("");

        if ("completed".equals(state)) {
            JsonNode artifacts = taskResult.path("artifacts");
            if (artifacts.isArray() && !artifacts.isEmpty()) {
                return artifacts.get(0).path("parts").get(0).path("text").asText("");
            }
        } else if ("failed".equals(state)) {
            String msg = taskResult.path("status").path("message").asText("Remote task failed");
            throw new RuntimeException("Remote agent error: " + msg);
        }

        return taskResult.toString();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    // ===== Inner Classes =====

    private static class RemoteAgent {
        final String configName;
        final String discoveredName;
        final String baseUrl;
        final String rpcUrl;
        final List<RemoteSkill> skills;

        RemoteAgent(String configName, String discoveredName, String baseUrl, String rpcUrl, List<RemoteSkill> skills) {
            this.configName = configName;
            this.discoveredName = discoveredName;
            this.baseUrl = baseUrl;
            this.rpcUrl = rpcUrl;
            this.skills = skills;
        }
    }

    private static class RemoteSkill {
        final String localId;
        final String name;
        final String description;
        final List<String> tags;
        final String remoteSkillId;
        final String rpcUrl;

        RemoteSkill(String localId, String name, String description, List<String> tags, String remoteSkillId, String rpcUrl) {
            this.localId = localId;
            this.name = name;
            this.description = description;
            this.tags = tags;
            this.remoteSkillId = remoteSkillId;
            this.rpcUrl = rpcUrl;
        }
    }
}
