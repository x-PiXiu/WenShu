# A2A 协议详解与 Multi-Agent 协作架构

> **文档说明**：本篇为第13篇（Tool/Function Calling）的扩展阅读材料，补充 Agent 间协作通信的核心协议知识，可作为第14-16篇 Agent 内容的先导。

---

## 一、为什么 Agent 需要专属通信协议？

### 1.1 背景：Multi-Agent 时代的到来

单个 Agent（AI 助手）能做的事有限。当复杂任务需要**多个专业 Agent 协作**时，问题出现了：

```
问题：两个 Agent 之间怎么"说话"？

❌ 方案1：直接调用对方函数
   Agent A 直接调用 Agent B 的内部方法
   → 严重耦合：B 的代码改了就挂了

❌ 方案2：共享数据库
   A 写数据到 DB，B 读出来
   → 实时性差，没有"对话"语义

❌ 方案3：同一套框架内调用
   两个 Agent 必须用同一个框架（LangChain4j）
   → 无法跨框架协作（Python Agent 无法调用 Java Agent）

✅ 方案4：A2A 协议
   A 和 B 通过标准 HTTP + JSON-RPC 通信
   → 跨框架、跨语言、跨平台，真正解耦
```

A2A（Agent-to-Agent）就是**第四种方案**——Google 2025 年推出的开放协议，让不同框架、不同语言、不同供应商的 Agent 能够标准化地互相通信。

---

## 二、A2A 协议核心概念

### 2.1 与 MCP 的关系：不是竞争，是互补

很多人搞不清 A2A 和 MCP 的关系。记住这个比喻：

```
MCP = Agent 的"手"（工具调用）
A2A = Agent 的"嘴"（Agent 间对话）

一个人（Agent）要完成工作：
- 需要用手操作工具（MCP）→ 查数据库、调API、读文件
- 也需要用嘴和别人沟通（A2A）→ 发任务、问问题、要结果

MCP 和 A2A 是正交的两个维度，配合使用：
  Agent A → MCP（调用工具）→ 数据库
  Agent A → A2A（协作）→ Agent B
```

| 维度 | MCP | A2A |
|------|-----|-----|
| **定位** | Agent 的工具层 | Agent 的通信层 |
| **谁调用谁** | Agent 调用外部工具/API | Agent 之间互相调用 |
| **发起方** | Agent | Agent（对等通信）|
| **典型场景** | 查数据库、调 API、读文件 | 委托任务、协作推理、结果汇总 |
| **标准化组织** | Anthropic → Linux Foundation | Google → Linux Foundation |
| **生态** | Anthropic/OpenAI/Google 等 | 50+ 企业伙伴（Google/MS/ AWS/Salesforce）|

### 2.2 A2A 协议的四大核心概念

A2A 协议围绕 **Task（任务）** 这个核心抽象来设计：

```
┌──────────────────────────────────────────────────────┐
│                    A2A 协议四要素                      │
├──────────────────────────────────────────────────────┤
│                                                      │
│  1️⃣ Agent Card（Agent 身份证）                        │
│     每个 Agent 公布自己的：能力、Skill列表、服务地址      │
│     → 相当于"名片"，让其他 Agent 发现自己            │
│                                                      │
│  2️⃣ Task（任务）                                      │
│     一次 A2A 对话就是一个 Task，有唯一ID和生命周期       │
│     → 相当于"对话线程"，有开始/进行中/完成/失败状态    │
│                                                      │
│  3️⃣ Message（消息）                                   │
│     Task 内的消息传递，支持文本/数据/附件               │
│     → 相当于"对话内容"                               │
│                                                      │
│  4️⃣ Skill（技能）                                     │
│     Agent 能处理的任务类型，每个 Skill 有ID/名称/描述    │
│     → 相当于"岗位说明书"，让对方知道该找谁干活        │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 2.3 Task 生命周期

```
  ┌─────────┐
  │ submitted │ ← 任务提交
  └────┬────┘
       │  (开始处理)
       ▼
  ┌─────────┐
  │ working  │ ← 处理中（可能产生多个 intermediate 消息）
  └────┬────┘
       │  (处理完成或失败)
       ▼
  ┌──────────────┐
  │ completed    │ ← 成功完成（有最终结果）
  └──────────────┘
       OR
  ┌──────────────┐
  │ failed       │ ← 执行失败（有错误信息）
  └──────────────┘
       OR
  ┌──────────────┐
  │ canceled     │ ← 被取消
  └──────────────┘
```

关键点：**Task 不是一次请求-响应就结束**。复杂任务可能持续几秒到几分钟，期间 Agent 会通过 `intermediate` 消息不断推送进度。

---

## 三、A2A 协议规范详解

### 3.1 Agent Card：Agent 的自我介绍

每个 A2A Agent 必须发布一个 JSON 格式的 Agent Card，让其他 Agent 能发现它、理解它的能力：

```json
{
  "name": "物流查询助手",
  "description": "专业物流查询助手，支持订单追踪、时效预测、快递公司选择",
  "url": "https://agent.example.com/a2a/v1",
  "version": "1.0.0",
  "provider": {
    "organization": "某物流科技公司",
    "url": "https://www.example.com"
  },
  "capabilities": {
    "streaming": true,
    "pushNotifications": true,
    "stateTransitionHistory": false
  },
  "skills": [
    {
      "id": "query-logistics",
      "name": "物流查询",
      "description": "根据订单号查询物流状态和详细信息",
      "tags": ["物流", "快递", "订单追踪"],
      "inputModes": ["application/json", "text/plain"],
      "outputModes": ["application/json", "text/plain"]
    },
    {
      "id": "route-optimize",
      "name": "路径规划",
      "description": "根据收发货地址规划最优配送路径",
      "tags": ["路径", "优化", "配送"],
      "inputModes": ["application/json"],
      "outputModes": ["application/json", "application/vnd.geo+json"]
    }
  ],
  "securitySchemes": {
    "bearer": {
      "type": "http",
      "scheme": "bearer"
    }
  },
  "defaultInputModes": ["application/json", "text/plain"],
  "defaultOutputModes": ["application/json", "text/plain"]
}
```

**Agent Card 的关键字段：**

| 字段 | 含义 | 为什么重要 |
|------|------|---------|
| `url` | Agent 的 A2A 服务地址 | 其他 Agent 通过这个地址找到它 |
| `capabilities.streaming` | 是否支持 SSE 流式推送 | 长时间任务可以流式返回进度 |
| `capabilities.pushNotifications` | 是否支持推送通知 | 任务完成后主动通知对方 |
| `skills` | 能处理的技能列表 | **核心字段**——告诉对方"我能干什么" |

### 3.2 A2A JSON-RPC 接口

A2A 基于 JSON-RPC 2.0，有 5 个核心方法：

#### 方法一：tasks/send —— 发送任务（核心方法）

**请求方 Agent A → 被调用方 Agent B**

```json
// POST https://agent-b.example.com/a2a/v1/rpc
{
  "jsonrpc": "2.0",
  "id": "msg-001",
  "method": "tasks/send",
  "params": {
    "id": "task-12345",
    "sessionId": "session-abc",
    "skillId": "query-logistics",
    "input": {
      "orderId": "SF123456"
    },
    "pushNotification": {
      "url": "https://agent-a.example.com/a2a/v1/notifications",
      "token": "xxx"
    }
  }
}
```

**响应：**

```json
{
  "jsonrpc": "2.0",
  "id": "msg-001",
  "result": {
    "task": {
      "id": "task-12345",
      "status": {
        "state": "completed",
        "message": null
      },
      "artifacts": [
        {
          "name": "物流结果",
          "description": "订单 SF123456 的物流信息",
          "parts": [
            {
              "type": "text",
              "text": "订单已发货，正在配送途中，预计明天18:00送达。"
            }
          ]
        }
      ],
      "history": [
        {
          "role": "agent",
          "parts": [{"type": "text", "text": "正在查询订单 SF123456..."}]
        },
        {
          "role": "agent",
          "parts": [{"type": "text", "text": "订单已发货，正在配送途中，预计明天18:00送达。"}]
        }
      ]
    }
  }
}
```

#### 方法二：tasks/sendSubscribe —— 发送任务并订阅进度

和 `tasks/send` 一样，但支持 **SSE 流式返回中间进度**：

```json
// 请求（不变）
// 响应：Content-Type: text/event-stream
event: message
data: {"jsonrpc":"2.0","id":"msg-001","result":{"task":{"id":"task-12345","status":{"state":"working"},"artifacts":null,"history":[{"role":"agent","parts":[{"type":"text","text":"正在查询..."}]}]}}}

event: message
data: {"jsonrpc":"2.0","id":"msg-002","result":{"task":{"id":"task-12345","status":{"state":"completed"},"artifacts":[...]}}}
```

#### 方法三：tasks/get —— 查询任务状态

轮询获取任务当前状态（当不支持推送时使用）：

```json
// GET https://agent-b.example.com/a2a/v1/tasks/task-12345
{
  "id": "task-12345",
  "status": {"state": "completed"},
  "artifacts": [...]
}
```

#### 方法四：tasks/cancel —— 取消任务

```json
{
  "jsonrpc": "2.0",
  "id": "msg-003",
  "method": "tasks/cancel",
  "params": {"taskId": "task-12345"}
}
```

#### 方法五：agents/anthropic羊 —— 获取 Agent Card

```json
// GET https://agent-b.example.com/a2a/v1/agent
// 响应：完整的 Agent Card JSON
```

---

## 四、MCP + A2A 协作模式

### 4.1 经典模式：MCP 管工具，A2A 管协作

```
                    ┌─────────────────────────────────────┐
                    │           用户（Human）               │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
                    │         Agent A（调度员）              │
                    │  • 理解用户需求                       │
                    │  • 分解任务                          │
                    │  • 协调其他 Agent                    │
                    └──────┬─────────────────┬───────────┘
                           │                 │
              ┌─────────────▼───┐   ┌────────▼───────────┐
              │  MCP（工具层）  │   │  A2A（协作层）     │
              │                 │   │                   │
              │  • 查数据库     │   │  Agent B ←───────→ Agent C
              │  • 调外部API    │   │  (物流)    (仓储)
              │  • 读文件       │   │
              └─────────────────┘   └───────────────────┘
```

### 4.2 A2A 的 Skill 路由机制

当 Agent A 要调用 Agent B 时，它会：

```
Step 1：获取 Agent Card（发现阶段）
  GET https://agent-b.example.com/a2a/v1/agent
  → 收到 B 的 Skill 列表

Step 2：匹配 Skill（路由阶段）
  用户需求："查一下我的订单到哪了"
  A 分析需求 → 需要"物流查询"技能
  A 在 B 的 Skill 列表中找到 skillId = "query-logistics"

Step 3：发送任务（执行阶段）
  POST https://agent-b.example.com/a2a/v1/rpc
  method: tasks/send
  params.skillId = "query-logistics"
  params.input = {orderId: "SF123456"}

Step 4：接收结果（返回阶段）
  B 处理完毕 → 返回 Task 结果给 A
```

---

## 五、LangChain4j 1.x 中的 A2A 实现

### 5.1 LangChain4j 对 A2A 的支持现状

**重要说明**：截至 2026 年，LangChain4j 官方**尚未内置完整的 A2A 协议实现**。但 LangChain4j 的架构设计完全支持自行实现 A2A 兼容通信。

> 💡 这是为什么？我们需要自己实现 A2A 协议，但用 LangChain4j 的组件来完成 Agent 逻辑。

### 5.2 手写一个 A2A 兼容的 LangChain4j Agent

#### 项目结构

```
src/main/java/com/example/a2a/
├── agent/
│   ├── LogisticsAgent.java        ← 物流查询 Agent（Agent B）
│   └── OrderAgent.java           ← 订单 Agent（Agent A）
├── a2a/
│   ├── AgentCard.java            ← Agent Card 模型
│   ├── A2AClient.java            ← A2A 客户端（调用其他 Agent）
│   ├── A2AServer.java           ← A2A 服务器（接收任务）
│   ├── TaskManager.java          ← Task 生命周期管理
│   └── JsonRpcRequest.java       ← JSON-RPC 请求封装
├── skill/
│   ├── LogisticsSkill.java       ← 物流查询 Skill 实现
│   └── SkillRegistry.java         ← Skill 注册表
└── LogisticsAgentApplication.java ← 启动入口
```

#### 5.2.1 Agent Card 定义

```java
package com.example.a2a.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Card——Agent 的自我介绍卡片
 * 对应 A2A 协议规范中的 Agent Card
 */
public class AgentCard {

    private String name;
    private String description;
    private String url;              // A2A 服务地址
    private String version;
    private Provider provider;
    private Capabilities capabilities;
    private List<Skill> skills;
    private Map<String, SecurityScheme> securitySchemes;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;

    public static AgentCard createLogisticsAgentCard() {
        return AgentCard.builder()
                .name("物流查询助手")
                .description("专业物流查询助手，支持订单追踪、时效预测、快递公司选择")
                .url("http://localhost:8081/a2a/v1")
                .version("1.0.0")
                .provider(new Provider("某物流科技", "https://www.example.com"))
                .capabilities(new Capabilities(true, true, false))
                .skills(List.of(
                        new Skill(
                                "query-logistics",
                                "物流查询",
                                "根据订单号查询物流状态和详细信息",
                                List.of("物流", "快递", "订单追踪"),
                                List.of("application/json", "text/plain"),
                                List.of("application/json", "text/plain")
                        ),
                        new Skill(
                                "route-optimize",
                                "路径规划",
                                "根据收发货地址规划最优配送路径",
                                List.of("路径", "优化", "配送"),
                                List.of("application/json"),
                                List.of("application/json", "application/vnd.geo+json")
                        )
                ))
                .defaultInputModes(List.of("application/json", "text/plain"))
                .defaultOutputModes(List.of("application/json", "text/plain"))
                .build();
    }

    // --- inner classes ---
    public record Provider(String organization, String url) {}
    public record Capabilities(boolean streaming, boolean pushNotifications, boolean stateTransitionHistory) {}
    public record Skill(
            String id,
            String name,
            String description,
            List<String> tags,
            List<String> inputModes,
            List<String> outputModes
    ) {}

    // Builder（简化版）
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String name, description, url, version;
        private Provider provider;
        private Capabilities capabilities;
        private List<Skill> skills;
        private Map<String, SecurityScheme> securitySchemes;
        private List<String> defaultInputModes, defaultOutputModes;

        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder url(String v) { this.url = v; return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder provider(Provider v) { this.provider = v; return this; }
        public Builder capabilities(Capabilities v) { this.capabilities = v; return this; }
        public Builder skills(List<Skill> v) { this.skills = v; return this; }
        public Builder securitySchemes(Map<String, SecurityScheme> v) { this.securitySchemes = v; return this; }
        public Builder defaultInputModes(List<String> v) { this.defaultInputModes = v; return this; }
        public Builder defaultOutputModes(List<String> v) { this.defaultOutputModes = v; return this; }
        public AgentCard build() {
            AgentCard c = new AgentCard();
            c.name = name; c.description = description; c.url = url; c.version = version;
            c.provider = provider; c.capabilities = capabilities; c.skills = skills;
            c.securitySchemes = securitySchemes;
            c.defaultInputModes = defaultInputModes; c.defaultOutputModes = defaultOutputModes;
            return c;
        }
    }
    private static class AgentCard { /* fields */ }
    public record SecurityScheme(String type, String scheme) {}
}
```

#### 5.2.2 A2A 服务器（接收并处理任务）

```java
package com.example.a2a.a2a;

import com.example.a2a.skill.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A2A 服务器——暴露 HTTP 端点，接收其他 Agent 的任务调用
 */
public class A2AServer {

    private final HttpServer server;
    private final ObjectMapper om = new ObjectMapper();
    private final SkillRegistry skillRegistry;
    private final TaskManager taskManager;
    private final AgentCard agentCard;

    public A2AServer(int port, SkillRegistry skillRegistry, AgentCard agentCard) throws IOException {
        this.skillRegistry = skillRegistry;
        this.agentCard = agentCard;
        this.taskManager = new TaskManager();

        this.server = HttpServer.create(new java.net.InetSocketAddress(port), 0);
        this.server.setExecutor(null);

        // 暴露 Agent Card
        server.createContext("/a2a/v1/agent", onAgentCard());

        // JSON-RPC 入口
        server.createContext("/a2a/v1/rpc", onRpc());

        // SSE 流式订阅（可选）
        server.createContext("/a2a/v1/tasks/", onTaskStream());
    }

    public void start() {
        server.start();
        System.out.println("🤖 A2A Server 已启动，端口：" + server.getAddress().getPort());
        System.out.println("📋 Agent Card：" + agentCard.url() + "/a2a/v1/agent");
        System.out.println("🔧 已注册 Skills：" + skillRegistry.listSkillIds());
    }

    private HttpHandler onAgentCard() {
        return exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            sendJson(exchange, 200, agentCard);
        };
    }

    private HttpHandler onRpc() {
        return exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            try {
                ObjectNode request = om.readValue(exchange.getRequestBody(), ObjectNode.class);
                String method = request.get("method").asText();
                ObjectNode params = request.has("params") ? (ObjectNode) request.get("params") : om.createObjectNode();
                String id = request.has("id") ? request.get("id").asText() : null;

                Object result = switch (method) {
                    case "tasks/send" -> handleTaskSend(params);
                    case "tasks/sendSubscribe" -> handleTaskSendSubscribe(exchange, params);
                    case "tasks/cancel" -> handleTaskCancel(params);
                    case "tasks/get" -> handleTaskGet(params);
                    case "agents/anthropic羊" -> agentCard;  // 注意：实际协议中就是"agents/anthropic羊"
                    default -> Map.of("error", "Method not found: " + method);
                };

                sendJson(exchange, 200, Map.of("jsonrpc", "2.0", "id", id != null ? id : "null", "result", result));

            } catch (Exception e) {
                sendJson(exchange, 200, Map.of(
                        "jsonrpc", "2.0",
                        "id", "null",
                        "error", Map.of("code", -32603, "message", e.getMessage())
                ));
            }
        };
    }

    /**
     * 处理 tasks/send——核心任务分发
     */
    private Object handleTaskSend(ObjectNode params) {
        String taskId = params.has("id") && !params.get("id").isNull()
                ? params.get("id").asText()
                : "task-" + UUID.randomUUID();
        String skillId = params.get("skillId").asText();
        ObjectNode input = (ObjectNode) params.get("input");

        // 创建 Task
        Task task = taskManager.createTask(taskId, skillId, input);

        // 找到 Skill 并执行
        var skill = skillRegistry.get(skillId);
        if (skill == null) {
            taskManager.fail(taskId, "Unknown skill: " + skillId);
            return taskManager.getTaskResult(taskId);
        }

        try {
            Object output = skill.execute(input);
            taskManager.complete(taskId, output);
        } catch (Exception e) {
            taskManager.fail(taskId, e.getMessage());
        }

        return taskManager.getTaskResult(taskId);
    }

    private Object handleTaskSendSubscribe(ObjectNode params) {
        // SSE 流式处理，篇幅有限省略具体实现
        return handleTaskSend(params);
    }

    private Object handleTaskCancel(ObjectNode params) {
        String taskId = params.get("taskId").asText();
        taskManager.cancel(taskId);
        return taskManager.getTaskResult(taskId);
    }

    private Object handleTaskGet(ObjectNode params) {
        String taskId = params.get("taskId").asText();
        return taskManager.getTaskResult(taskId);
    }

    private HttpHandler onTaskStream() {
        // SSE 流式任务状态推送（篇幅有限省略）
        return exchange -> sendJson(exchange, 200, Map.of("error", "not implemented"));
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = om.writeValueAsBytes(body);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    public void stop() { server.stop(0); }
}
```

#### 5.2.3 A2A 客户端（调用其他 Agent）

```java
package com.example.a2a.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A2A 客户端——调用其他 Agent 的工具类
 */
public class A2AClient {

    private final HttpClient httpClient;
    private final ObjectMapper om;
    private final String baseUrl;

    public A2AClient(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.om = new ObjectMapper();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 发现阶段：获取对方的 Agent Card
     */
    public AgentCard discoverAgent() throws Exception {
        String url = baseUrl + "/a2a/v1/agent";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return om.readValue(response.body(), AgentCard.class);
    }

    /**
     * 核心方法：向对方 Agent 发送任务
     * @param skillId   对方 Agent 的 Skill ID
     * @param input     任务输入参数
     * @return Task 执行结果
     */
    public TaskResult sendTask(String skillId, Map<String, Object> input) throws Exception {
        ObjectNode params = om.createObjectNode();
        params.put("skillId", skillId);
        ObjectNode inputNode = om.valueToTree(input);
        params.set("input", inputNode);

        ObjectNode request = om.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "req-" + System.currentTimeMillis());
        request.put("method", "tasks/send");
        request.set("params", params);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/a2a/v1/rpc"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(request)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

        ObjectNode rpcResponse = om.readValue(response.body(), ObjectNode.class);
        ObjectNode result = (ObjectNode) rpcResponse.get("result");
        return parseTaskResult(result);
    }

    /**
     * 异步发送任务（支持长时间任务）
     */
    public CompletableFuture<TaskResult> sendTaskAsync(String skillId, Map<String, Object> input) {
        return CompletableFuture.supplyAsync(() -> {
            try { return sendTask(skillId, input); }
            catch (Exception e) { throw new RuntimeException("A2A调用失败", e); }
        });
    }

    /**
     * 解析 Task 结果
     */
    private TaskResult parseTaskResult(ObjectNode result) {
        ObjectNode taskNode = (ObjectNode) result.get("task");
        String taskId = taskNode.get("id").asText();
        String state = taskNode.get("status").get("state").asText();

        String output = null;
        if (taskNode.has("artifacts") && !taskNode.get("artifacts").isNull()) {
            var artifacts = taskNode.get("artifacts");
            if (artifacts.isArray() && artifacts.size() > 0) {
                var first = (com.fasterxml.jackson.databind.node.ObjectNode) artifacts.get(0);
                if (first.has("parts")) {
                    var parts = first.get("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        output = parts.get(0).get("text").asText();
                    }
                }
            }
        }

        return new TaskResult(taskId, state, output);
    }

    /** A2A 任务结果 */
    public record TaskResult(String taskId, String state, String output) {
        public boolean isCompleted() { return "completed".equals(state); }
        public boolean isFailed() { return "failed".equals(state); }
    }
}
```

---

## 六、双 Agent 协作实战：各司其职，互相调用

### 6.1 场景描述

```
两个 Agent 协作完成用户需求：

Agent A（订单 Agent）：负责订单管理
  Skill: "create-order", "query-order", "cancel-order"

Agent B（物流 Agent）：负责物流查询
  Skill: "query-logistics", "route-optimize"

用户说："帮我查一下昨天买的订单发货了没"
  → Agent A 接到任务 → 查订单状态 → 发现已发货
  → Agent A 发现需要物流信息 → 通过 A2A 调用 Agent B
  → Agent B 返回物流状态 → Agent A 汇总给用户
```

### 6.2 Agent A：订单 Agent（主动调用方）

```java
package com.example.a2a.agent;

import com.example.a2a.a2a.A2AClient;
import com.example.a2a.a2a.AgentCard;
import com.example.a2a.a2a.A2AServer;
import com.example.a2a.a2a.TaskManager;
import com.example.a2a.skill.SkillRegistry;
import com.example.a2a.skill.OrderSkill;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Map;

/**
 * 订单 Agent（Agent A）——负责订单管理，主动调用物流 Agent
 */
public class OrderAgent {

    private final ChatModel chatModel;
    private final A2AClient logisticsClient;  // A2A 客户端（调用物流 Agent）
    private final AgentCard logisticsAgentCard; // 物流 Agent 的 Card（缓存）

    public OrderAgent(String logisticsAgentUrl) {
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("MINIMAX_API_KEY"))
                .baseUrl("https://api.minimax.chat/v1")
                .modelName("MiniMax-M2.5")
                .build();

        this.logisticsClient = new A2AClient(logisticsAgentUrl);

        // 启动时先发现物流 Agent 的能力（也可以缓存起来）
        try {
            this.logisticsAgentCard = logisticsClient.discoverAgent();
            System.out.println("✅ 已发现物流Agent：" + logisticsAgentCard.name());
            System.out.println("📋 Skills：" + logisticsAgentCard.skills().stream()
                    .map(s -> s.id()).toList());
        } catch (Exception e) {
            throw new RuntimeException("无法连接物流Agent", e);
        }
    }

    /**
     * 处理用户查询：我的订单发货了吗？
     */
    public String handle(String userQuestion) {
        // Step 1：LLM 理解用户意图
        String intent = analyzeIntent(userQuestion);
        System.out.println("🔍 分析意图：" + intent);

        if (intent.contains("查订单物流")) {
            // Step 2：从用户问题中提取订单号（实际项目用 NLP/正则）
            String orderId = extractOrderId(userQuestion);
            
            // Step 3：查订单状态（本地 Tool）
            String orderStatus = queryLocalOrder(orderId);
            
            if ("已发货".equals(orderStatus)) {
                // Step 4：订单已发货，需要物流信息 → A2A 调用物流 Agent
                try {
                    System.out.println("📦 订单已发货，通过 A2A 查询物流...");
                    
                    // 通过 A2A 协议调用物流 Agent
                    A2AClient.TaskResult logisticsResult = logisticsClient.sendTask(
                            "query-logistics",           // 物流 Agent 的 Skill ID
                            Map.of("orderId", orderId)   // 输入参数
                    );
                    
                    if (logisticsResult.isCompleted()) {
                        String response = String.format(
                                "您的订单 %s 已发货！物流状态：%s",
                                orderId, logisticsResult.output()
                        );
                        return response;
                    } else {
                        return "物流查询遇到问题，请稍后重试。";
                    }
                } catch (Exception e) {
                    return "物流 Agent 暂时不可用，请稍后重试。错误：" + e.getMessage();
                }
            } else {
                return String.format("您的订单 %s 当前状态：%s（尚未发货）", orderId, orderStatus);
            }
        }

        // 其他意图交给 LLM 直接处理
        return chatModel.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(userQuestion))
                        .build()
        ).aiMessage().text();
    }

    private String analyzeIntent(String question) {
        // 简化版：实际用 RAG 或关键词匹配
        if (question.contains("发货")) return "查订单+物流";
        if (question.contains("订单")) return "查订单";
        if (question.contains("取消")) return "取消订单";
        return "其他";
    }

    private String extractOrderId(String text) {
        // 简化：从文本中提取订单号
        return text.replaceAll(".*(SF\\d+|YT\\d+|JD\\d+).*", "$1");
    }

    private String queryLocalOrder(String orderId) {
        // 模拟本地订单数据库查询
        return "已发货";
    }

    public static void main(String[] args) {
        // Agent A 启动，指定物流 Agent（Agent B）的地址
        OrderAgent agent = new OrderAgent("http://localhost:8081");
        
        String answer = agent.handle("帮我查一下 SF123456 发货了没？");
        System.out.println("💬 最终回答：" + answer);
    }
}
```

### 6.3 Agent B：物流 Agent（被动接收方）

```java
package com.example.a2a.agent;

import com.example.a2a.a2a.A2AServer;
import com.example.a2a.a2a.AgentCard;
import com.example.a2a.skill.LogisticsSkill;
import com.example.a2a.skill.SkillRegistry;
import com.example.a2a.a2a.TaskManager;

/**
 * 物流 Agent（Agent B）——暴露 A2A 接口，被 Agent A 调用
 */
public class LogisticsAgent {

    public static void main(String[] args) throws Exception {
        // 1. 创建 Skill 注册表
        SkillRegistry registry = new SkillRegistry();
        registry.register(new LogisticsSkill());  // 物流查询 Skill

        // 2. 创建 Agent Card
        AgentCard card = AgentCard.createLogisticsAgentCard();

        // 3. 启动 A2A 服务器（暴露 HTTP 端点）
        A2AServer server = new A2AServer(8081, registry, card);
        server.start();

        System.out.println("🚚 物流Agent已启动，等待A2A任务...");
        System.out.println("📡 A2A地址：http://localhost:8081/a2a/v1");
    }
}
```

### 6.4 协作时序图

```
用户
  │
  │ 帮我查一下 SF123456 发货了没？
  ▼
┌─────────────────┐
│  Agent A（订单）  │
│  LLM 理解意图    │
│  发现需要物流    │
└───────┬─────────┘
        │ A2A: tasks/send(skillId="query-logistics", input={orderId:"SF123456"})
        │ HTTP POST
        ▼
┌─────────────────┐
│  Agent B（物流）  │
│  接收任务        │
│  执行 Skill      │
│  查询物流API     │
└───────┬─────────┘
        │ A2A Response: {state:"completed", output:"订单已发货,预计明天送达"}
        │ HTTP Response
        ▼
┌─────────────────┐
│  Agent A（订单）  │
│  汇总结果        │
│  返回用户        │
└───────┬─────────┘
        │
        ▼
     用户：您的订单已发货！物流状态：正在配送，预计明天送达。
```

---

## 七、实战案例：把第12篇的 RAG 项目变成 A2A Agent

### 7.1 需求回顾

第12篇我们做了一个完整的 RAG 知识库问答系统。现在我们把它改造成一个 **A2A Agent**——让它可以被其他 Agent 通过 A2A 协议调用，成为企业知识库网络中的一个节点。

### 7.2 架构设计

```
                          ┌─────────────────────┐
                          │   用户 / 前端        │
                          └──────────┬──────────┘
                                     │
                          ┌──────────▼──────────┐
                          │  Agent A（调度员）   │
                          │  • 理解用户需求      │
                          │  • 路由到专业Agent   │
                          └──────┬─────────┬─────┘
                                 │         │
                   ┌─────────────▼──┐  ┌───▼─────────────────┐
                   │  A2A           │  │  A2A                 │
                   │  Agent B（RAG）│  │  Agent C（客服）     │
                   │  知识库问答     │  │  处理投诉/退款       │
                   └─────────────┘  └───────────────────────┘
```

### 7.3 RAG Agent 改造代码

```java
package com.example.a2a.agent;

import com.example.a2a.a2a.A2AServer;
import com.example.a2a.a2a.AgentCard;
import com.example.a2a.skill.SkillRegistry;
import com.example.a2a.skill.RagSkill;  // ← 新增：RAG Skill
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;

import java.nio.file.Path;

/**
 * RAG Agent（基于第12篇项目改造）
 * 原本是一个独立的 RAG 问答系统
 * 现在改造为 A2A Agent，对外暴露 Skill 接口
 */
public class RagAgent {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;

    public static void main(String[] args) throws Exception {
        // 1. 初始化模型
        String apiKey = System.getenv("MINIMAX_API_KEY");
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey).baseUrl("https://api.minimax.chat/v1")
                .modelName("MiniMax-M2.5").build();

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey).baseUrl("https://api.minimax.chat/v1")
                .modelName("embo-01").build();

        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // 2. Indexing（复用第12篇逻辑）
        System.out.println("📚 正在加载知识库...");
        var docs = loadKnowledgeBase(Path.of("knowledge/"));
        ingestDocuments(docs, embeddingModel, store);
        System.out.println("✅ 知识库索引完成");

        // 3. 创建 Skill
        RagSkill ragSkill = new RagSkill(chatModel, embeddingModel, store);

        // 4. 创建 Skill 注册表
        SkillRegistry registry = new SkillRegistry();
        registry.register(ragSkill);

        // 5. 创建 Agent Card
        AgentCard card = createRagAgentCard();

        // 6. 启动 A2A 服务器
        A2AServer server = new A2AServer(8082, registry, card);
        server.start();

        System.out.println("📚 RAG Agent已启动，端口8082");
        System.out.println("🔗 A2A地址：http://localhost:8082/a2a/v1");
    }

    private static AgentCard createRagAgentCard() {
        return AgentCard.builder()
                .name("企业知识库助手")
                .description("基于 RAG 的企业知识库问答系统，支持退货政策、物流信息、优惠券规则等常见问题")
                .url("http://localhost:8082/a2a/v1")
                .version("1.0.0")
                .provider(new AgentCard.Provider("某科技公司", "https://www.example.com"))
                .capabilities(new AgentCard.Capabilities(true, true, false))
                .skills(List.of(
                        new AgentCard.Skill(
                                "rag-query",
                                "知识库问答",
                                "基于企业内部知识库回答用户问题，如退货政策、物流信息、优惠券使用规则等",
                                List.of("知识库", "问答", "退货", "物流", "优惠券"),
                                List.of("application/json", "text/plain"),
                                List.of("application/json", "text/plain")
                        )
                ))
                .defaultInputModes(List.of("application/json", "text/plain"))
                .defaultOutputModes(List.of("application/json", "text/plain"))
                .build();
    }

    private static java.util.List<Document> loadKnowledgeBase(Path dir) {
        // 复用第12篇 AutoDocumentParser.loadDirectory() 逻辑
        return com.example.rag.parser.AutoDocumentParser.loadDirectory(dir);
    }

    private static void ingestDocuments(java.util.List<Document> docs,
                                        EmbeddingModel embeddingModel,
                                        EmbeddingStore<TextSegment> store) {
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 30,
                        new OpenAiTokenCountEstimator("gpt-4o")))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
        docs.forEach(ingestor::ingest);
    }
}
```

### 7.4 RAG Skill 实现

```java
package com.example.a2a.skill;

import com.example.a2a.a2a.Skill;

/**
 * RAG Skill——封装第12篇的 RAG 逻辑，供 A2A 调用
 */
public class RagSkill implements Skill {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;

    public static final String ID = "rag-query";

    @Override
    public String id() { return ID; }

    @Override
    public String description() {
        return "基于企业内部知识库回答用户问题，如退货政策、物流信息、优惠券使用规则等";
    }

    @Override
    public Object execute(ObjectNode input) {
        // A2A 输入：{question: "退货政策是什么？"}
        String question = input.get("question").asText();
        String answer = answer(question);
        return Map.of("answer", answer);
    }

    private String answer(String question) {
        try {
            // Step 1: 向量检索
            var results = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(embeddingModel.embed(question).content())
                            .maxResults(3)
                            .minScore(0.5)
                            .build()
            ).matches();

            if (results.isEmpty()) {
                return "知识库中未找到相关信息。";
            }

            // Step 2: 构建 Prompt
            String prompt = buildPrompt(question, results);

            // Step 3: 生成回答
            ChatResponse response = chatModel.chat(
                    ChatRequest.builder()
                            .messages(UserMessage.from(prompt))
                            .build()
            );
            return response.aiMessage().text();

        } catch (Exception e) {
            return "知识库查询遇到问题：" + e.getMessage();
        }
    }

    private String buildPrompt(String question, List<EmbeddingMatch<TextSegment>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业知识库助手，基于以下参考资料回答用户问题。\n\n【参考资料】\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
              .append(results.get(i).embedded().text()).append("\n");
        }
        sb.append("\n用户问题：").append(question);
        return sb.toString();
    }
}
```

### 7.5 协作场景：调度员 Agent 调用 RAG Agent

```java
/**
 * 调度员 Agent——用户说"我想退货"，调度员不确定政策，
 * → 通过 A2A 调用 RAG Agent 获取最新退货政策
 */
public class DispatcherAgent {

    private final A2AClient ragAgentClient;

    public DispatcherAgent() {
        // 指向 RAG Agent
        this.ragAgentClient = new A2AClient("http://localhost:8082");
    }

    public String handle(String userMessage) {
        if (userMessage.contains("退货") || userMessage.contains("退款")) {
            try {
                // A2A 调用 RAG Agent 查退货政策
                A2AClient.TaskResult result = ragAgentClient.sendTask(
                        "rag-query",
                        Map.of("question", "退货政策是什么？")
                );
                return result.isCompleted()
                        ? result.output()
                        : "知识库暂时不可用，请稍后重试。";
            } catch (Exception e) {
                return "系统繁忙，请稍后重试。";
            }
        }
        // ... 其他路由逻辑
        return "抱歉，我暂时无法处理这个问题。";
    }
}
```

---

## 八、工程实践：企业级 A2A 注意事项

### 8.1 安全：Agent 身份认证

A2A 协议支持多种安全方案，生产环境必须启用：

```json
// Agent Card 中声明安全方案
"securitySchemes": {
  "bearer": {
    "type": "http",
    "scheme": "bearer"
  }
},
"security": [{"bearer": ["openid", "profile", "email"]}]

// 客户端请求时携带 Token
Authorization: Bearer <jwt-token>
```

### 8.2 推送通知：长时间任务的处理

当 A2A 任务耗时较长（如批量查询）时，客户端不能一直等待 HTTP 连接：

```
方案1：轮询 tasks/get
  客户端每隔 N 秒调用一次 GET /tasks/{id} 查询状态
  → 简单，但效率低

方案2：SSE 流式订阅（推荐）
  客户端调用 tasks/sendSubscribe
  服务器通过 SSE 推送中间进度和最终结果
  → 实时、高效

方案3：Webhook 回调
  客户端在请求中传入 pushNotification.url
  服务器处理完成后主动 POST 到该地址
  → 适合异步处理
```

### 8.3 错误处理与重试

```java
// A2A 客户端重试逻辑
public TaskResult sendTaskWithRetry(String skillId, Map<String, Object> input, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        try {
            return sendTask(skillId, input);
        } catch (Exception e) {
            if (i == maxRetries - 1) throw new RuntimeException("A2A调用失败", e);
            try { Thread.sleep(1000 * (i + 1)); } catch (InterruptedException ie) {} // 指数退避
        }
    }
    throw new RuntimeException("不应该走到这里");
}
```

### 8.4 Agent 发现机制

除了手动配置 URL，还可以实现**中心化 Agent 注册中心**：

```java
/**
 * 简易版 Agent 注册中心
 * 生产环境建议使用 Consul / etcd / Nacos
 */
public class AgentRegistry {

    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();
    private final Map<String, A2AClient> clients = new ConcurrentHashMap<>();

    public void register(AgentCard card, A2AClient client) {
        cards.put(card.name(), card);
        clients.put(card.name(), client);
    }

    public AgentCard findBySkill(String skillId) {
        return cards.values().stream()
                .filter(c -> c.skills().stream().anyMatch(s -> s.id().equals(skillId)))
                .findFirst()
                .orElse(null);
    }

    public A2AClient getClient(String agentName) {
        return clients.get(agentName);
    }
}
```

---

## 九、总结：A2A 协议核心要点

| 要点 | 说明 |
|------|------|
| **A2A 是什么** | Google 2025 年推出的开放协议，让不同框架/语言的 Agent 标准化通信 |
| **核心概念** | Agent Card（自我介绍）+ Task（任务）+ Message（消息）+ Skill（技能） |
| **与 MCP 的关系** | MCP = Agent 的工具层，A2A = Agent 的通信层，两者互补 |
| **LangChain4j 支持** | 官方暂无内置 A2A 实现，但架构完全支持自行实现（本文代码） |
| **双 Agent 协作** | Agent A 主动调用 → Agent B 被动接收 → A2A JSON-RPC 通信 |
| **RAG 项目改造** | 第12篇项目加一层 A2A Server + Skill 封装，即可成为可协作 Agent |
| **Skill 路由** | 通过 Agent Card 发现能力 → 按 Skill ID 路由 → tasks/send 执行 |

---

## 十、附录：A2A 协议速查表

### 核心接口

| 方法 | 用途 |
|------|------|
| `GET /a2a/v1/agent` | 获取 Agent Card |
| `POST /a2a/v1/rpc` (tasks/send) | 发送任务（同步） |
| `POST /a2a/v1/rpc` (tasks/sendSubscribe) | 发送任务并订阅进度（SSE） |
| `GET /a2a/v1/tasks/{id}` | 查询任务状态 |
| `POST /a2a/v1/rpc` (tasks/cancel) | 取消任务 |

### Task 状态

| 状态 | 含义 |
|------|------|
| `submitted` | 已提交，待处理 |
| `working` | 处理中 |
| `completed` | 成功完成 |
| `failed` | 执行失败 |
| `canceled` | 被取消 |

### Skill 定义

```json
{
  "id": "unique-skill-id",
  "name": "技能名称",
  "description": "技能描述（让对方知道何时该调用你）",
  "tags": ["标签1", "标签2"],
  "inputModes": ["application/json"],
  "outputModes": ["application/json", "text/plain"]
}
```
