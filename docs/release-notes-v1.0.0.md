## 文枢 · 藏书阁 (WenShu) v1.0.0

> 开源 RAG 知识库，让 AI 只回答「你的」文档。

---

### 这是什么？

一个基于 RAG（检索增强生成）的全栈知识库系统。把你的 PDF、Word、Markdown 丢进去，AI 就能基于这些文档回答问题——每个回答都附带来源引用，不再是一本正经地胡说八道。

### 核心功能

- **混合检索**：向量语义检索 + BM25 关键词检索，RRF 融合排序，兼顾语义理解和精确匹配
- **流式问答**：SSE 逐 Token 输出，支持多轮对话与长期记忆
- **模型热切换**：运行时切换 LLM / Embedding 模型，零停机
- **智能体 + 工具调用**：自定义 AI 人格，内置 13 个工具（检索、搜索、翻译、计算、文档对比等）
- **Prompt 统一管理**：所有提示词一个注册表管完，后台编辑即时生效
- **闪卡学习**：AI 生成深度闪卡，SM-2 间隔重复算法，PDF 导出
- **博客系统**：文章发布 + 读者智能问答 + 媒体管理
- **LLM 监控**：调用追踪、Token 消耗分析、延迟分布、RAG 质量评估
- **A2A / MCP**：Agent 协作协议就绪

### 技术栈

Java 17 + Javalin 6.x + LangChain4j + Vue 3 + TypeScript + SQLite

### 快速开始

1. 安装 [JDK 17+](https://adoptium.net/)（唯一前置要求）
2. 下载对应平台的压缩包：
   - Windows → `wenshu-v1.0.0-release.zip`
   - Linux / macOS → `wenshu-v1.0.0-release.tar.gz`
3. 解压到任意目录
4. 启动：
   - Windows：双击 `start.bat`
   - Linux / macOS：`./start.sh`
5. 首次启动自动创建 `config.json`，编辑填入 API Key 后再次启动
6. 浏览器访问 `http://localhost:8081`

### 支持的模型提供商

| 类型 | 支持的提供商 |

|------|-------------|
| LLM | Ollama（本地免费）、MiniMax、OpenAI 及所有 OpenAI 兼容 API |
| Embedding | Ollama、智谱 AI、OpenAI |
| 向量存储 | 内存（默认）、Chroma、Milvus |

### 从源码构建

```bash
git clone https://github.com/x-PiXiu/WenShu.git
cd WenShu

# 构建前端
cd rag-knowledge-base-web && npm install && npm run build && cd ..

# 构建并打包
mvn clean package -DskipTests

# 发布包在 target/ 目录下
```
