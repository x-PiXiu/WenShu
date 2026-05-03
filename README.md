# 文枢 · 藏书阁 (WenShu)

> 基于 RAG（检索增强生成）的智能知识库问答系统，支持混合检索、多模型动态切换、多种向量存储后端，兼容 A2A/MCP 协议实现跨 Agent 协作。

## 项目简介

**文枢** 是一个全栈 RAG 知识库解决方案，包含 Java 后端服务和 Vue 3 前端界面。用户上传文档后，系统自动完成文档解析、分块、向量化索引，之后即可通过自然语言进行智能问答。系统采用**混合检索策略**（向量语义检索 + BM25 关键词检索 → RRF 融合排序），确保检索的准确性和召回率。

### 核心特性

- **混合检索引擎**：向量语义检索 + BM25 关键词检索，通过 RRF（Reciprocal Rank Fusion）融合排序
- **多模型动态切换**：运行时切换 LLM 和 Embedding 模型，无需重启服务
- **多向量存储后端**：支持 In-Memory / Chroma / Milvus 三种向量数据库
- **流式输出**：基于 SSE（Server-Sent Events）实现逐 Token 流式响应
- **多轮对话**：支持对话历史上下文，自动生成摘要并存储为长期记忆
- **智能体系统**：支持创建多个 AI 人格，每个智能体拥有独立的系统提示词和工具集
- **Agent 工具调用**：内置 12+ 工具（知识库检索、Web 搜索、翻译、计算、文档对比等），LLM 可自主调用
- **Prompt 统一管理**：PromptRegistry 作为唯一事实来源，统一管理核心功能/工具/Agent 提示词，支持热更新
- **文档类型管理**：不同文档类型（通用/技术/FAQ/日志/长文）采用差异化分块策略
- **博客系统**：内置博客发布、分类、标签管理，支持文章智能问答，媒体文件管理
- **闪卡学习系统**：AI 自动生成闪卡，SM-2 间隔重复算法，翻卡/打字双模式学习，雷达图能力分析
- **LLM 调用监控**：实时追踪 LLM 调用次数、Token 消耗、延迟分布等指标
- **RAG 评估系统**：自动化检索质量评估，支持 Recall@K、Precision@K、MRR 等指标
- **A2A 协议**：实现 Agent-to-Agent 协议，可与其他 AI Agent 节点协作
- **MCP 服务器**：兼容 Model Context Protocol，支持 SSE + JSON-RPC 2.0
- **支持多种文档格式**：PDF、DOCX、Markdown、纯文本
- **前后端分离部署**：支持一体化部署和前后端分离部署

### 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Java 17 + Javalin 6.x | 轻量级 HTTP 服务，端口 8081 |
| AI 框架 | LangChain4j 1.14.0 | LLM 调用、文档解析、向量存储、工具调用 |
| 向量数据库 | InMemory / Chroma / Milvus | 可通过配置切换 |
| 数据持久化 | SQLite (WAL) | 共享连接池，对话/智能体/闪卡/博客/记忆等 |
| 前端框架 | Vue 3 + TypeScript | SPA 单页应用 |
| UI 组件库 | Naive UI | Vue 3 组件库 |
| 构建工具 | Vite | 前端开发服务器（5173）/ 生产构建（输出到 static/） |
| 文档解析 | Apache Tika + PDFBox | 支持 PDF/DOCX/MD/TXT |

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────────┐
│                       前端 (Vue 3 + Vite)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌────────┐ │
│  │  藏书阁   │ │  知识库   │ │  闪卡    │ │  博客    │ │  设置  │ │
│  │  (Chat)  │ │(Knowledge)│ │(Flashcard)│ │  (Blog)  │ │(Config)│ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬────┘ └───┬────┘ │
│       └─────────────┴────────────┴────────────┴──────────┘       │
│                            │ HTTP / SSE                          │
│              Vite Proxy (:5173) 或 Javalin (:8081)               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    后端 (Javalin :8081)                           │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                      API Routes (70+)                      │  │
│  │  /api/chat  /api/documents  /api/agents  /api/flashcard    │  │
│  │  /api/blog  /api/admin/*   /api/settings  /a2a/v1  /mcp   │  │
│  └──────────────────────┬─────────────────────────────────────┘  │
│                         │                                        │
│  ┌──────────────────────▼─────────────────────────────────────┐  │
│  │                     RagService (核心)                       │  │
│  │  ┌────────────┐ ┌──────────────┐ ┌──────────────┐         │  │
│  │  │HybridSearch│ │ ModelFactory │ │VectorStore   │         │  │
│  │  │向量+BM25   │ │ LLM/Embed   │ │Factory       │         │  │
│  │  │→ RRF 融合  │ │ 动态切换    │ │ Mem/Chr/Mil  │         │  │
│  │  └────────────┘ └──────────────┘ └──────────────┘         │  │
│  │  ┌────────────┐ ┌──────────────┐ ┌──────────────┐         │  │
│  │  │ RagTools   │ │PromptRegistry│ │ MemoryStore  │         │  │
│  │  │ 12+ 工具   │ │ 统一管理+热更新│ │ 长期记忆     │         │  │
│  │  └────────────┘ └──────────────┘ └──────────────┘         │  │
│  └────────────────────────────────────────────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ DatabasePool │  │ BlogStore    │  │FlashcardStore│          │
│  │ (SQLite 共享) │  │ BlogIndexer  │  │ (含 SM-2)    │          │
│  └──────────────┘  │ MediaStore   │  └──────────────┘          │
│  ┌──────────────┐  └──────────────┘  ┌──────────────┐          │
│  │  ChatStore   │  ┌──────────────┐  │LlmCallStore  │          │
│  │  AgentStore  │  │EvalResultStore│ │ LlmMonitor   │          │
│  └──────────────┘  │RagEvaluator  │  └──────────────┘          │
│                    └──────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

### 后端模块说明

| 包 | 文件 | 职责 |
|------|------|------|
| `config/` | `AppConfiguration` | 加载/保存 config.json，含 LLM/Embedding/向量存储/RAG/Blog/WebSearch/Prompt 配置 |
| `config/` | `DatabasePool` | SQLite 共享连接池（6 连接），替代各 Store 独立连接池 |
| `config/` | `ModelFactory` | 动态创建 ChatModel / StreamingChatModel / EmbeddingModel |
| `config/` | `VectorStoreFactory` | 根据 config 创建 InMemory / Chroma / Milvus 实例 |
| `service/` | `RagService` | 核心服务：文档索引、RAG 检索生成、流式输出、长期记忆 |
| `search/` | `HybridSearcher` | 向量检索 + BM25 关键词检索 → RRF 融合排序 |
| `prompt/` | `PromptRegistry` | 全局 Prompt 注册表（唯一事实来源），统一管理核心/工具/Agent 提示词，支持读写与持久化 |
| `prompt/` | `RagPromptTemplate` | 构建 RAG 问答 Prompt（含参考资料格式化） |
| `tools/` | `RagTools` | Agent 可调用工具集（12+ @Tool 注解方法） |
| `tools/` | `ToolEngine` | 工具规范提取与执行引擎 |
| `tools/` | `WebSearcher` | Web 搜索集成（Tavily / SerpAPI / 自定义） |
| `parser/` | `AutoDocumentParser` | 自动识别文件类型，使用 Tika/PDFBox 解析 |
| `parser/` | `SemanticSplitter` | 语义感知的文档分块，支持可配置重叠 |
| `parser/` | `DocumentTypeDetector` | 自动检测文档类型（通用/技术/FAQ/日志/长文） |
| `parser/` | `DocumentMetaStore` | 文档元数据持久化（类型、分块大小、检测方式） |
| `chat/` | `ChatStore` | 对话与消息持久化（SQLite） |
| `chat/` | `AgentStore` | AI 智能体持久化，通过 prompt_key 引用 PromptRegistry 条目 |
| `blog/` | `BlogStore` | 博客文章 CRUD + 分类标签 + SHA-256 增量索引 |
| `blog/` | `BlogIndexer` | 博客文章向量化索引（发布时自动索引） |
| `blog/` | `MediaStore` | 媒体文件管理（上传、查询、删除） |
| `blog/` | `AuthFilter` | 管理后台认证过滤，支持可选密码（个人版免登录） |
| `flashcard/` | `FlashcardStore` | 闪卡 Deck + Card 持久化，内置 SM-2 间隔重复算法 |
| `flashcard/` | `FlashcardGenerator` | LLM 驱动的闪卡生成器 |
| `service/` | `MemoryStore` | 长期记忆持久化（重要性评分 + 衰减机制） |
| `service/` | `MemoryScorer` | 记忆重要性评分与衰减计算 |
| `observability/` | `LlmCallStore` | LLM 调用日志持久化（Token、延迟、模型、完成原因） |
| `observability/` | `LlmCallListener` | LLM 调用实时指标捕获 |
| `a2a/` | `TaskManager` | A2A 协议任务生命周期管理 |
| `a2a/` | `AgentCard` | A2A Agent 身份与能力描述 |
| `mcp/` | `McpServerHandler` | MCP 服务器（SSE + JSON-RPC 2.0） |

---

## 页面展示

### 藏书阁（智能问答）

<!-- TODO: 上传截图 — 藏书阁主界面，左侧对话列表，右侧聊天面板，流式输出效果 -->
![藏书阁主界面](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/chat-main.png?raw=true)

### 知识库（文档管理）

<!-- TODO: 上传截图 — 知识库文件列表，显示文档类型、分块数、上传时间等 -->
![知识库文件列表](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/knowledge-list.png)

### 闪卡学习

<!-- TODO: 上传截图 — 闪卡生成器界面，上方为上传文件/粘贴文本双标签页，支持配置卡片数量和难度 -->
![闪卡生成器](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/flashcard-generator.png)

<!-- TODO: 上传截图 — 卡组详情页，编号列表展示问题，点击展开答案，显示难度和标签，支持行内编辑 -->
![卡组详情](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/flashcard-detail.png)

<!-- TODO: 上传截图 — 翻卡学习模式，顶部进度条 + 卡片计数，点击卡片翻转查看答案，支持键盘快捷键 -->
![翻卡学习模式](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/flashcard-study-flip.png)

### 博客系统

<!-- TODO: 上传截图 — 博客文章列表页，显示文章卡片、分类、标签 -->
![博客文章列表](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/blog-list.png)

<!-- TODO: 上传截图 — 博客文章详情页，右侧智能问答面板 -->
![博客文章问答](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/blog-chat.png)

### 管理后台

<!-- TODO: 上传截图 — LLM 调用监控面板，显示调用趋势和延迟分布 -->
![LLM 监控面板](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/admin-llm-monitor.png)

<!-- TODO: 上传截图 — Prompt 模板管理界面 -->
![Prompt 管理](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/admin-prompt-manage.png)

<!-- TODO: 上传截图 — RAG 评估仪表盘 -->
![RAG 评估](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/admin-memory.png)

### 设置

<!-- TODO: 上传截图 — LLM/Embedding 模型配置界面 -->
![模型配置](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/settings-llm.png)

<!-- TODO: 上传截图 — 智能体管理界面 -->
![智能体管理](https://github.com/CAH1314/WenShu/blob/master/docs/screenshots/settings-agents.png)

---

## 部署指南

### 环境要求

| 依赖 | 版本要求 | 用途 |
|------|----------|------|
| JDK | 17+ | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 18+ | 前端构建工具（仅开发/构建时需要） |
| npm | 9+ | 前端包管理 |

### 部署方式一：一体化部署（推荐）

前端构建后嵌入后端，只需运行一个 Java 进程。

#### 1. 克隆项目

```bash
git clone https://gitee.com/CAH1314/WenShu.git
cd WenShu
```

#### 2. 构建前端

```bash
cd rag-knowledge-base-web
npm install
npm run build
# 产物输出到 ../static/ 目录
cd ..
```

#### 3. 配置文件

```bash
cp config.example.json config.json
```

编辑 `config.json`：

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
  "vectorStore": { "type": "memory" }
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
> - `"chroma"`：需要额外部署 Chroma 服务
> - `"milvus"`：需要额外部署 Milvus 服务

#### 4. 构建并启动

```bash
mvn clean package -DskipTests
java -jar target/rag-knowledge-base-1.0-SNAPSHOT.jar
```

浏览器访问 `http://localhost:8081` 即可使用。

---

### 部署方式二：前后端分离

适合需要独立扩展前端或使用 CDN 加速的场景。

**后端**（纯 API 模式）：

```bash
mvn clean package -DskipTests
java -jar target/rag-knowledge-base-1.0-SNAPSHOT.jar
# 自动检测无 static/ 目录，进入 API-only 模式
```

**前端**（Nginx 部署）：

```bash
cd rag-knowledge-base-web
npm install
npm run build
# 将 dist/ 部署到 Nginx
```

Nginx 配置：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        root /path/to/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /a2a/ {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
    }

    location /uploads/ {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
    }
}
```

---

### 开发环境启动

需要两个终端窗口：

**终端 1 — 后端**：

```bash
cd WenShu
mvn compile exec:java -Dexec.mainClass="com.example.rag.RagApplication"
```

**终端 2 — 前端**：

```bash
cd WenShu/rag-knowledge-base-web
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`（Vite 代理自动转发 API 到 8081）。

### 准备知识库文档（可选）

将文档放入 `knowledge/` 目录，支持 PDF / DOCX / Markdown / 纯文本。系统启动时自动索引。

---

## API 接口文档

### 问答与对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 普通问答（同步） |
| POST | `/api/chat/stream` | 流式问答（SSE） |
| POST | `/api/chat/cancel` | 取消当前流式输出 |
| GET | `/api/conversations` | 获取对话列表 |
| POST | `/api/conversations` | 创建新对话 |
| GET | `/api/conversations/{id}` | 获取对话详情（含消息） |
| PUT | `/api/conversations/{id}` | 更新对话标题 |
| DELETE | `/api/conversations/{id}` | 删除对话 |

### 知识库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/documents` | 获取文档列表（含元数据） |
| GET | `/api/documents/types` | 获取文档类型配置 |
| POST | `/api/documents/upload` | 上传并索引文档 |
| DELETE | `/api/documents/{filename}` | 删除文档 |
| GET | `/api/knowledge/stats` | 获取知识库统计 |
| POST | `/api/settings/reindex` | 重新索引全部文档 |

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
| GET | `/api/health` | 健康检查 |

### 博客（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/blog/posts` | 文章列表（分页、分类、标签筛选） |
| GET | `/api/blog/posts/{slug}` | 文章详情 |
| GET | `/api/blog/categories` | 分类列表 |
| GET | `/api/blog/tags` | 所有标签 |
| GET | `/api/blog/search` | 搜索文章 |
| POST | `/api/blog/chat` | 文章问答（同步） |
| POST | `/api/blog/chat/stream` | 文章问答（流式） |

### 博客管理（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/login` | 管理员登录 |
| GET | `/api/admin/auth-status` | 获取认证状态（是否需要密码） |
| GET | `/api/admin/posts` | 文章列表（含草稿） |
| POST | `/api/admin/posts` | 创建文章 |
| PUT | `/api/admin/posts/{id}` | 更新文章 |
| DELETE | `/api/admin/posts/{id}` | 删除文章 |
| POST | `/api/admin/posts/{id}/publish` | 发布文章 |
| POST | `/api/admin/posts/{id}/unpublish` | 取消发布 |
| POST | `/api/admin/posts/{id}/summarize` | AI 自动生成摘要 |
| POST | `/api/admin/import` | 导入 DOCX/PDF 为文章 |
| GET/POST/PUT/DELETE | `/api/admin/categories/*` | 分类管理 |
| GET/POST/DELETE | `/api/admin/media/*` | 媒体文件管理 |

### 闪卡系统

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/flashcard/generate` | 生成闪卡（文件或文本） |
| GET | `/api/flashcard/decks` | 卡组列表 |
| GET | `/api/flashcard/decks/{id}` | 卡组详情（含卡片） |
| DELETE | `/api/flashcard/decks/{id}` | 删除卡组 |
| PUT | `/api/flashcard/cards/{id}` | 编辑卡片 |
| DELETE | `/api/flashcard/cards/{id}` | 删除卡片 |
| GET | `/api/flashcard/study/{deckId}` | 获取待学习卡片（`?mode=all` 全部） |
| POST | `/api/flashcard/review` | 提交单张卡片评分 |
| POST | `/api/flashcard/review/batch` | 批量提交评分 |
| GET | `/api/flashcard/stats` | 全局学习统计 |
| GET | `/api/flashcard/decks/{id}/export` | 导出为可打印 HTML |

### 管理后台

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/llm/calls` | 最近 LLM 调用记录 |
| GET | `/api/admin/llm/stats` | LLM 调用统计 |
| GET | `/api/admin/llm/daily` | 每日调用统计 |
| GET | `/api/admin/llm/hourly` | 每小时调用统计 |
| GET | `/api/admin/llm/latency-distribution` | 延迟分布 |
| GET | `/api/admin/memories` | 长期记忆列表 |
| DELETE | `/api/admin/memories/{id}` | 删除记忆 |
| GET | `/api/admin/eval/cases` | 评估测试用例 |
| POST | `/api/admin/eval/run` | 执行评估 |
| GET | `/api/admin/eval/results` | 评估结果历史 |

### A2A 协议

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/a2a/v1/agent` | 获取 Agent 卡片 |
| POST | `/a2a/v1/rpc` | JSON-RPC（tasks/send、tasks/get、tasks/cancel） |
| GET | `/a2a/v1/tasks` | 获取任务历史 |

### MCP 服务器

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mcp/sse` | SSE 连接端点 |
| POST | `/mcp/message` | JSON-RPC 消息处理 |

---

## 项目结构

```
WenShu/
├── config.example.json                  # 配置模板
├── config.json                          # 实际配置（.gitignore）
├── pom.xml                              # Maven 项目配置
├── knowledge/                           # 知识库文档目录
├── uploads/                             # 上传文件目录
├── static/                              # 前端生产构建产物（Javalin 直接服务）
├── src/main/java/com/example/rag/
│   ├── RagApplication.java              # 主入口 + API 路由（70+ 端点）
│   ├── config/
│   │   ├── AppConfiguration.java        # 配置管理（10 个配置分区）
│   │   ├── DatabasePool.java            # SQLite 共享连接池
│   │   ├── ModelFactory.java            # LLM/Embedding 模型工厂
│   │   └── VectorStoreFactory.java      # 向量存储工厂
│   ├── service/
│   │   ├── RagService.java              # RAG 核心服务
│   │   ├── MemoryStore.java             # 长期记忆持久化
│   │   └── MemoryScorer.java            # 记忆评分与衰减
│   ├── search/
│   │   └── HybridSearcher.java          # 混合检索（向量 + BM25 → RRF）
│   ├── prompt/
│   │   ├── PromptRegistry.java          # Prompt 全局注册表
│   │   └── RagPromptTemplate.java       # RAG 提示词模板
│   ├── tools/
│   │   ├── RagTools.java                # Agent 工具集（12+ @Tool）
│   │   ├── ToolEngine.java              # 工具引擎
│   │   └── WebSearcher.java             # Web 搜索集成
│   ├── parser/
│   │   ├── AutoDocumentParser.java      # 文档自动解析
│   │   ├── SemanticSplitter.java        # 语义分块器
│   │   ├── DocumentTypeDetector.java    # 文档类型自动检测
│   │   └── DocumentMetaStore.java       # 文档元数据存储
│   ├── chat/
│   │   ├── ChatStore.java               # 对话持久化
│   │   └── AgentStore.java              # 智能体持久化
│   ├── blog/
│   │   ├── BlogStore.java               # 博客文章存储
│   │   ├── BlogIndexer.java             # 博客向量化索引
│   │   ├── MediaStore.java              # 媒体文件管理
│   │   └── AuthFilter.java              # 管理后台认证
│   ├── flashcard/
│   │   ├── FlashcardStore.java          # 闪卡数据存储（含 SM-2 算法）
│   │   └── FlashcardGenerator.java      # AI 闪卡生成器
│   ├── observability/
│   │   ├── LlmCallStore.java            # LLM 调用日志
│   │   └── LlmCallListener.java         # LLM 调用监听
│   ├── eval/
│   │   ├── RagEvaluator.java            # RAG 评估器
│   │   ├── RetrievalEvaluator.java      # 检索质量评估
│   │   ├── EvalResultStore.java         # 评估结果存储
│   │   └── EvalTestCaseStore.java       # 测试用例存储
│   ├── a2a/
│   │   ├── AgentCard.java               # A2A Agent 卡片
│   │   ├── Task.java                    # A2A 任务模型
│   │   └── TaskManager.java             # A2A 任务管理
│   └── mcp/
│       └── McpServerHandler.java        # MCP 服务器
└── rag-knowledge-base-web/              # Vue 3 前端
    ├── index.html
    ├── package.json
    ├── vite.config.ts                   # Vite 配置（代理 + 生产构建）
    ├── src/
    │   ├── App.vue                      # 应用主入口（侧边栏导航 + 路由）
    │   ├── main.ts
    │   ├── types/                       # TypeScript 类型定义
    │   │   ├── chat.ts                  # 对话/消息/智能体类型
    │   │   ├── blog.ts                  # 博客/分类/媒体类型
    │   │   ├── flashcard.ts             # 闪卡/卡组/评分类型
    │   │   └── a2a.ts                   # A2A 协议类型
    │   ├── composables/                 # Vue Composables（API 客户端）
    │   │   ├── useChat.ts               # 聊天 API（SSE 流式）
    │   │   ├── useAgents.ts             # 智能体管理
    │   │   ├── useSettings.ts           # 系统配置
    │   │   ├── useBlog.ts               # 博客 API
    │   │   ├── useFlashcard.ts          # 闪卡 API
    │   │   ├── useAdmin.ts              # 管理后台 API
    │   │   ├── useMedia.ts              # 媒体文件 API
    │   │   └── useA2aClient.ts          # A2A 协议客户端
    │   └── components/
    │       ├── ChatPanel.vue            # 对话面板（流式输出）
    │       ├── ConversationList.vue     # 对话列表
    │       ├── KnowledgeBase.vue        # 知识库管理
    │       ├── KnowledgeStats.vue       # 知识库统计
    │       ├── SettingsPage.vue         # 设置页面（LLM/Embedding/向量存储/安全配置）
    │       ├── PromptManagePage.vue     # Prompt 模板管理（分类编辑 + 热更新）
    │       ├── AgentManager.vue         # 智能体管理
    │       ├── AgentCard.vue            # Agent 信息卡片
    │       ├── TaskList.vue             # A2A 任务列表
    │       ├── MarkdownRenderer.vue     # Markdown 渲染器
    │       ├── flashcard/               # 闪卡模块
    │       │   ├── FlashcardPage.vue    # 卡组列表与生成
    │       │   ├── DeckDetail.vue       # 卡组详情
    │       │   ├── StudyMode.vue        # 学习模式（翻卡/打字）
    │       │   └── RadarChart.vue       # 雷达图能力分析
    │       ├── blog/                    # 博客模块
    │       │   ├── BlogList.vue         # 文章列表
    │       │   ├── BlogDetail.vue       # 文章详情
    │       │   ├── BlogChat.vue         # 文章问答
    │       │   └── BlogSidebar.vue      # 侧边栏
    │       └── admin/                   # 管理后台
    │           ├── PostEditor.vue       # 文章编辑器
    │           ├── PostList.vue         # 文章管理
    │           ├── CategoryManager.vue  # 分类管理
    │           ├── MediaManager.vue     # 媒体管理
    │           ├── LlmMonitor.vue       # LLM 调用监控
    │           ├── EvalDashboard.vue    # 评估仪表盘
    │           └── MemoryManager.vue    # 记忆管理
    └── dist/                            # npm run build 产物（部署用）
```

---

## 常见问题

### Q: 启动后端报错 "Knowledge base indexing failed"

检查 `config.json` 中的 Embedding API Key 和 Base URL 是否正确。如果暂时没有 API Key，可以先在配置中将向量存储设为 `"memory"`，跳过知识库索引步骤。

### Q: 前端无法连接后端

确保后端已启动在 `8081` 端口。开发模式下 Vite 代理会自动转发请求，生产环境需配置 Nginx 反向代理。

### Q: 如何更换 LLM 模型

在设置页面可以直接修改 LLM Provider、Base URL、API Key 和模型名称，保存后立即生效，无需重启。也支持直接编辑 `config.json` 后重启服务。

### Q: 如何使用 Ollama 本地模型

1. 安装并启动 [Ollama](https://ollama.ai)
2. 拉取模型：`ollama pull qwen2.5` 和 `ollama pull nomic-embed-text`
3. 在设置中配置：
   - LLM Provider: `ollama`，Base URL: `http://localhost:11434/v1`
   - Embedding Provider: `ollama`，Base URL: `http://localhost:11434/v1`

### Q: 闪卡学习后卡片消失

学完一轮后所有卡片进入间隔重复的巩固期。可以点击「重新学习全部」立即复习所有卡片。

### Q: 管理后台是否需要密码

个人开源版默认免密码。在设置页面的「安全设置」中可以设置管理员密码，设置后所有管理后台操作需要先登录。密码可随时修改或清空（清空即回到免密码模式）。

### Q: Prompt 模板如何修改

在设置页面点击「Prompt 管理」进入专用 Prompt 编辑页面。按类别分组展示所有可编辑的 Prompt（系统 Agent / 核心功能 / 工具提示词 / 自定义 Agent），修改后点击「保存设置」立即生效。所有 Prompt 统一由 PromptRegistry 管理，包括自定义 Agent 的系统提示词。

---

## License

MIT
