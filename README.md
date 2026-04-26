# 文枢 · 藏书阁 (WenShu)

> 基于 RAG（检索增强生成）的智能知识库问答系统，支持混合检索、多模型动态切换、多种向量存储后端，兼容 A2A 协议实现跨 Agent 协作。

## 项目简介

**文枢** 是一个完整的 RAG 知识库解决方案，包含 Java 后端服务和 Vue 3 前端界面。用户上传文档后，系统自动完成文档解析、分块、向量化索引，之后即可通过自然语言进行智能问答。系统采用**混合检索策略**（向量语义检索 + BM25 关键词检索 → RRF 融合排序），确保检索的准确性和召回率。

### 核心特性

- **混合检索引擎**：向量语义检索 + BM25 关键词检索，通过 RRF（Reciprocal Rank Fusion）融合排序
- **多模型动态切换**：运行时切换 LLM 和 Embedding 模型，无需重启服务
- **多向量存储后端**：支持 In-Memory / Chroma / Milvus 三种向量数据库
- **流式输出**：基于 SSE（Server-Sent Events）实现逐 Token 流式响应
- **多轮对话**：支持对话历史上下文，自动生成摘要并存储为长期记忆
- **智能体系统**：支持创建多个 AI 人格，每个智能体拥有独立的系统提示词
- **文档类型管理**：不同文档类型（通用/技术/FAQ/日志/长文）采用差异化分块策略
- **A2A 协议兼容**：实现 Agent-to-Agent 协议，可与其他 AI Agent 节点协作
- **支持多种文档格式**：PDF、DOCX、Markdown、纯文本

### 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Java 17 + Javalin | 轻量级 HTTP 服务，端口 8080 |
| AI 框架 | LangChain4j 0.36.2 | LLM 调用、文档解析、向量存储 |
| 向量数据库 | InMemory / Chroma / Milvus | 可通过配置切换 |
| 数据持久化 | SQLite | 对话历史、智能体数据 |
| 前端框架 | Vue 3 + TypeScript | SPA 单页应用 |
| UI 组件库 | Naive UI | Vue 3 组件库 |
| 构建工具 | Vite | 前端开发服务器，端口 5173 |
| 文档解析 | Apache Tika + PDFBox | 支持 PDF/DOCX/MD/TXT |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     前端 (Vue 3 + Vite)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │  藏书阁   │ │  知识库   │ │ A2A 节点  │ │   设置     │  │
│  │ (Chat)   │ │(Knowledge)│ │  (A2A)   │ │ (Settings) │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬─────┘  │
│       │             │            │              │         │
│       └─────────────┴────────────┴──────────────┘         │
│                         │ HTTP / SSE                      │
│                    Vite Proxy (:5173)                     │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────┐
│                  后端 (Javalin :8080)                     │
│  ┌────────────────────────────────────────────────────┐  │
│  │                    API Routes                       │  │
│  │  /api/chat  /api/documents  /api/agents  /a2a/v1   │  │
│  └────────────────────┬───────────────────────────────┘  │
│                       │                                   │
│  ┌────────────────────▼───────────────────────────────┐  │
│  │                  RagService                         │  │
│  │  ┌────────────┐ ┌──────────────┐ ┌──────────────┐ │  │
│  │  │HybridSearch│ │ ModelFactory │ │VectorStore   │ │  │
│  │  │向量+BM25   │ │ LLM/Embed   │ │Factory       │ │  │
│  │  │→ RRF 融合  │ │ 动态切换    │ │ Memory/Chroma│ │  │
│  │  └────────────┘ └──────────────┘ │ /Milvus      │ │  │
│  │                                  └──────────────┘ │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐    │
│  │  ChatStore  │  │ AgentStore  │  │ AppConfig     │    │
│  │  (SQLite)   │  │ (SQLite)    │  │ (config.json) │    │
│  └─────────────┘  └─────────────┘  └───────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 后端模块说明

| 模块 | 文件 | 职责 |
|------|------|------|
| `RagApplication` | 入口 + API 路由 | 启动 Javalin 服务器，注册所有 REST 端点 |
| `RagService` | 核心服务 | 文档索引、RAG 检索生成、流式输出、长期记忆 |
| `HybridSearcher` | 混合检索 | 向量检索 + BM25 关键词检索 → RRF 融合排序 |
| `AppConfiguration` | 配置管理 | 加载/保存 config.json，支持运行时修改 |
| `ModelFactory` | 模型工厂 | 动态创建 ChatModel / StreamingChatModel / EmbeddingModel |
| `VectorStoreFactory` | 向量存储工厂 | 根据 config 创建 InMemory / Chroma / Milvus 实例 |
| `AutoDocumentParser` | 文档解析器 | 自动识别文件类型，使用 Tika/PDFBox 解析 |
| `RagPromptTemplate` | 提示词模板 | 构建带参考资料的 RAG Prompt |
| `ChatStore` | 对话存储 | SQLite 持久化对话和消息 |
| `AgentStore` | 智能体存储 | SQLite 持久化 AI 人格配置 |
| `TaskManager` | A2A 任务管理 | 管理 A2A 协议任务的生命周期 |

---

## 页面展示

### 藏书阁（智能问答）

<!-- 截图占位：藏书阁主页面 -->
![藏书阁主页面](.\docs\screenshots\chat-main.png)

### 知识库（文档管理）

<!-- 截图占位：知识库文件列表 -->
![知识库文件列表](.\docs\screenshots\knowledge-list.png)

### A2A 节点

<!-- 截图占位：A2A Agent 卡片 -->
![A2A Agent 卡片](.\docs\screenshots\a2a-agent.png)

### 设置

<!-- 截图占位：LLM 配置 -->
![LLM 配置](.\docs\screenshots\settings-llm.png)

<!-- 截图占位：智能体管理 -->
![智能体管理](.\docs\screenshots\settings-agents.png)

---

## 部署指南

### 环境要求

| 依赖 | 版本要求 | 用途 |
|------|----------|------|
| JDK | 17+ | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 18+ | 前端构建工具 |
| npm | 9+ | 前端包管理 |

### 服务端部署

#### 1. 克隆项目

```bash
git clone https://gitee.com/CAH1314/WenShu.git
cd WenShu
```

#### 2. 配置文件

复制配置模板并填入你的 API 密钥：

```bash
cp config.example.json config.json
```

编辑 `config.json`，填入以下信息：

```json
{
  "llm": {
    "provider": "minimax",
    "baseUrl": "https://api.minimax.chat/v1",
    "apiKey": "你的 LLM API Key",
    "modelName": "MiniMax-M2.5",
    "temperature": 0.7,
    "maxTokens": 32768,
    "streaming": true
  },
  "embedding": {
    "provider": "zhipu",
    "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
    "apiKey": "你的 Embedding API Key",
    "modelName": "embedding-3"
  },
  "vectorStore": {
    "type": "memory"
  }
}
```

> **支持的 LLM 提供商**（均兼容 OpenAI API 格式）：
> - **Ollama**：`baseUrl` 设为 `http://localhost:11434/v1`，`apiKey` 随意
> - **MiniMax**：`baseUrl` 设为 `https://api.minimax.chat/v1`
> - **OpenAI**：`baseUrl` 设为 `https://api.openai.com/v1`
> - **其他 OpenAI 兼容服务**：填入对应的 `baseUrl` 即可

> **支持的 Embedding 提供商**：
> - **Ollama**：`baseUrl` 设为 `http://localhost:11434/v1`
> - **智谱 AI**：`baseUrl` 设为 `https://open.bigmodel.cn/api/paas/v4`
> - **OpenAI**：`baseUrl` 设为 `https://api.openai.com/v1`

> **向量存储选项**：
> - `"memory"`：内存存储（默认，重启后数据丢失，适合开发测试）
> - `"chroma"`：需要额外部署 Chroma 服务，`chromaBaseUrl` 填入 Chroma 地址
> - `"milvus"`：需要额外部署 Milvus 服务，填入 `milvusHost` 和 `milvusPort`

#### 3. 构建项目

```bash
mvn clean package -DskipTests
```

构建产物为 `target/rag-knowledge-base-1.0-SNAPSHOT.jar`（包含所有依赖的 fat jar）。

#### 4. 启动服务

**开发模式**（使用 Maven 运行）：

```bash
mvn compile exec:java -Dexec.mainClass="com.example.rag.RagApplication"
```

**生产模式**（直接运行 jar）：

```bash
java -jar target/rag-knowledge-base-1.0-SNAPSHOT.jar
```

服务启动后，控制台输出：

```
[INIT] Configuration loaded
[INIT] Models initialized: LLM=MiniMax-M2.5, Embedding=embedding-3
[INIT] Knowledge base indexed: X docs, Y segments
========================================
  文枢 · 藏书阁  WenShu v1.0.0
  http://localhost:8080
  A2A endpoint: http://localhost:8080/a2a/v1
========================================
```

#### 5. 准备知识库文档（可选）

将文档放入 `knowledge/` 目录，支持以下格式：

- `.pdf` - PDF 文档
- `.docx` - Word 文档
- `.md` - Markdown 文档
- `.txt` - 纯文本

系统启动时会自动索引 `knowledge/` 目录下的所有文档。也可以通过前端界面上传。

---

### 客户端部署

#### 1. 进入前端目录

```bash
cd rag-knowledge-base-web
```

#### 2. 安装依赖

```bash
npm install
```

#### 3. 开发模式

```bash
npm run dev
```

前端开发服务器启动在 `http://localhost:5173`，已配置 Vite 代理将 `/api` 和 `/a2a` 请求转发到后端 `http://localhost:8080`。

> **注意**：开发模式下需要同时运行后端服务。

#### 4. 生产构建

```bash
npm run build
```

构建产物输出到 `dist/` 目录，为纯静态文件（HTML/JS/CSS）。

#### 5. 部署静态文件

将 `dist/` 目录部署到任意静态文件服务器（Nginx、Apache、Gitee Pages 等）。

**Nginx 配置示例**：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /path/to/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # A2A 协议反向代理
    location /a2a/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

### 完整启动流程（开发环境）

需要两个终端窗口：

**终端 1 - 启动后端**：

```bash
cd WenShu
mvn compile exec:java -Dexec.mainClass="com.example.rag.RagApplication"
```

**终端 2 - 启动前端**：

```bash
cd WenShu/rag-knowledge-base-web
npm install
npm run dev
```

浏览器访问 `http://localhost:5173` 即可使用。

---

## API 接口文档

### 问答接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 普通问答（同步） |
| POST | `/api/chat/stream` | 流式问答（SSE） |

### 对话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/conversations` | 创建新对话 |
| GET | `/api/conversations` | 获取对话列表 |
| GET | `/api/conversations/{id}` | 获取对话详情 |
| PUT | `/api/conversations/{id}` | 更新对话标题 |
| DELETE | `/api/conversations/{id}` | 删除对话 |

### 知识库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/documents` | 获取文档列表 |
| POST | `/api/documents/upload` | 上传文档 |
| DELETE | `/api/documents/{filename}` | 删除文档 |
| GET | `/api/documents/types` | 获取文档类型配置 |
| GET | `/api/knowledge/stats` | 获取知识库统计 |

### 智能体管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents` | 获取智能体列表 |
| POST | `/api/agents` | 创建智能体 |
| PUT | `/api/agents/{id}` | 更新智能体 |
| DELETE | `/api/agents/{id}` | 删除智能体 |

### 系统设置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/settings` | 获取当前配置 |
| POST | `/api/settings` | 保存配置（自动重建模型） |
| POST | `/api/settings/reindex` | 重新索引全部文档 |
| GET | `/api/health` | 健康检查 |

### A2A 协议

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/a2a/v1/agent` | 获取 Agent 卡片 |
| POST | `/a2a/v1/rpc` | JSON-RPC 调用（tasks/send、tasks/get、tasks/cancel） |
| GET | `/a2a/v1/tasks` | 获取任务历史 |

---

## 项目结构

```
WenShu/
├── config.example.json              # 配置模板（需复制为 config.json）
├── config.json                      # 实际配置（已 .gitignore，不提交）
├── pom.xml                          # Maven 项目配置
├── knowledge/                       # 知识库文档目录
├── src/main/java/com/example/rag/
│   ├── RagApplication.java          # 主入口 + API 路由
│   ├── RagKnowledgeBaseApp.java     # 命令行版本（独立运行）
│   ├── config/
│   │   ├── AppConfiguration.java    # 配置管理
│   │   ├── ModelFactory.java        # LLM/Embedding 模型工厂
│   │   └── VectorStoreFactory.java  # 向量存储工厂
│   ├── service/
│   │   └── RagService.java          # RAG 核心服务
│   ├── search/
│   │   └── HybridSearcher.java      # 混合检索（向量 + BM25）
│   ├── prompt/
│   │   └── RagPromptTemplate.java   # RAG 提示词模板
│   ├── parser/
│   │   ├── AutoDocumentParser.java  # 文档自动解析
│   │   └── DocumentMetaStore.java   # 文档元数据存储
│   ├── chat/
│   │   ├── ChatStore.java           # 对话持久化（SQLite）
│   │   └── AgentStore.java          # 智能体持久化（SQLite）
│   ├── a2a/
│   │   ├── AgentCard.java           # A2A Agent 卡片
│   │   ├── Task.java                # A2A 任务模型
│   │   └── TaskManager.java         # A2A 任务管理
│   └── eval/
│       ├── RagEvaluator.java        # RAG 评估器
│       └── RetrievalEvaluator.java  # 检索评估器
└── rag-knowledge-base-web/          # Vue 3 前端
    ├── index.html
    ├── package.json
    ├── vite.config.ts               # Vite 配置（含代理）
    ├── tsconfig.json
    ├── public/
    └── src/
        ├── App.vue                  # 应用主入口（含侧边栏导航）
        ├── main.ts
        ├── types/
        │   ├── chat.ts              # 对话相关类型定义
        │   └── a2a.ts               # A2A 相关类型定义
        └── components/
            ├── ChatPanel.vue        # 对话面板
            ├── ConversationList.vue # 对话列表
            ├── KnowledgeBase.vue    # 知识库管理
            ├── KnowledgeStats.vue   # 知识库统计
            ├── SettingsPage.vue     # 设置页面
            ├── AgentManager.vue     # 智能体管理
            ├── AgentCard.vue        # A2A Agent 卡片
            ├── TaskList.vue         # A2A 任务列表
            └── MarkdownRenderer.vue # Markdown 渲染器
```

---

## 常见问题

### Q: 启动后端报错 "Knowledge base indexing failed"

检查 `config.json` 中的 Embedding API Key 和 Base URL 是否正确。如果暂时没有 API Key，可以先在配置中将向量存储设为 `"memory"`，跳过知识库索引步骤。

### Q: 前端无法连接后端

确保后端已启动在 `8080` 端口。开发模式下 Vite 代理会自动转发请求，生产环境需配置 Nginx 反向代理。

### Q: 如何更换 LLM 模型

在设置页面可以直接修改 LLM Provider、Base URL、API Key 和模型名称，保存后立即生效，无需重启。也支持直接编辑 `config.json` 后重启服务。

### Q: 如何使用 Ollama 本地模型

1. 安装并启动 [Ollama](https://ollama.ai)
2. 拉取模型：`ollama pull qwen2.5` 和 `ollama pull nomic-embed-text`
3. 在设置中配置：
   - LLM Provider: `ollama`，Base URL: `http://localhost:11434/v1`
   - Embedding Provider: `ollama`，Base URL: `http://localhost:11434/v1`

---

## License

MIT
