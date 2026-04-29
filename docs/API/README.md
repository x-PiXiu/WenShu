# 文枢 · 藏书阁 API 使用文档

> Base URL: `http://localhost:8080`
>
> 所有接口均返回 `application/json`，编码 `UTF-8`

## 目录

- [通用说明](#通用说明)
- [1. 问答接口](#1-问答接口)
  - [1.1 普通问答](#11-普通问答)
  - [1.2 流式问答 (SSE)](#12-流式问答-sse)
- [2. 对话管理](#2-对话管理)
  - [2.1 创建对话](#21-创建对话)
  - [2.2 获取对话列表](#22-获取对话列表)
  - [2.3 获取对话详情](#23-获取对话详情)
  - [2.4 更新对话标题](#24-更新对话标题)
  - [2.5 删除对话](#25-删除对话)
- [3. 知识库管理](#3-知识库管理)
  - [3.1 获取文档列表](#31-获取文档列表)
  - [3.2 上传文档](#32-上传文档)
  - [3.3 删除文档](#33-删除文档)
  - [3.4 获取文档类型配置](#34-获取文档类型配置)
  - [3.5 获取知识库统计](#35-获取知识库统计)
- [4. 智能体管理](#4-智能体管理)
  - [4.1 获取智能体列表](#41-获取智能体列表)
  - [4.2 创建智能体](#42-创建智能体)
  - [4.3 更新智能体](#43-更新智能体)
  - [4.4 删除智能体](#44-删除智能体)
- [5. 系统设置](#5-系统设置)
  - [5.1 获取当前配置](#51-获取当前配置)
  - [5.2 保存配置](#52-保存配置)
  - [5.3 重新索引全部文档](#53-重新索引全部文档)
  - [5.4 健康检查](#54-健康检查)
- [6. A2A 协议接口](#6-a2a-协议接口)
  - [6.1 获取 Agent 卡片](#61-获取-agent-卡片)
  - [6.2 JSON-RPC 调用](#62-json-rpc-调用)
  - [6.3 获取任务历史](#63-获取任务历史)
- [7. 博客公开接口](#7-博客公开接口)
  - [7.1 获取文章列表](#71-获取文章列表)
  - [7.2 获取文章详情](#72-获取文章详情)
  - [7.3 获取分类列表](#73-获取分类列表)
  - [7.4 获取标签列表](#74-获取标签列表)
  - [7.5 搜索文章](#75-搜索文章)
  - [7.6 博客统计](#76-博客统计)
  - [7.7 博客问答](#77-博客问答)
  - [7.8 博客流式问答 (SSE)](#78-博客流式问答-sse)
- [8. 管理员接口](#8-管理员接口)
  - [8.1 管理员登录](#81-管理员登录)
  - [8.2 获取全部文章](#82-获取全部文章)
  - [8.3 创建文章](#83-创建文章)
  - [8.4 更新文章](#84-更新文章)
  - [8.5 删除文章](#85-删除文章)
  - [8.6 发布文章](#86-发布文章)
  - [8.7 取消发布文章](#87-取消发布文章)
  - [8.8 AI 生成摘要](#88-ai-生成摘要)
  - [8.9 文档导入](#89-文档导入)
  - [8.10 创建分类](#810-创建分类)
  - [8.11 更新分类](#811-更新分类)
  - [8.12 删除分类](#812-删除分类)
- [9. 媒体管理](#9-媒体管理)
  - [9.1 获取媒体列表](#91-获取媒体列表)
  - [9.2 上传媒体文件](#92-上传媒体文件)
  - [9.3 删除媒体文件](#93-删除媒体文件)
- [附录：数据模型](#附录数据模型)

---

## 通用说明

### 请求格式

- POST/PUT 请求使用 `Content-Type: application/json`
- 文件上传使用 `Content-Type: multipart/form-data`
- 时间戳均为 Unix 毫秒时间戳（`long`）

### 错误响应

所有接口在出错时返回统一格式：

```json
{
  "error": "错误描述信息"
}
```

HTTP 状态码说明：

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 1. 问答接口

### 1.1 普通问答

向知识库提问，同步返回完整答案。

```
POST /api/chat
```

**请求参数**

```json
{
  "question": "LangChain4j 是什么？",
  "conversationId": "conv-1713619200000",
  "agentId": "default"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | string | 是 | 用户问题 |
| `conversationId` | string | 否 | 对话 ID，不传则自动创建新对话 |
| `agentId` | string | 否 | 智能体 ID，不传则使用默认智能体 |

**响应示例**

```json
{
  "answer": "LangChain4j 是一个用于在 Java 中构建 LLM 应用的框架...",
  "sources": [
    {
      "index": 1,
      "text": "LangChain4j 是一个 Java 框架，旨在简化 LLM 应用的开发...",
      "source": "XH-001-Java也能做AI应用？.md",
      "rrfScore": 0.0323,
      "vectorScore": 0.8712
    },
    {
      "index": 2,
      "text": "LangChain4j 提供了统一的 API 来对接不同的 LLM 提供商...",
      "source": "XH-002-Spring-AI-vs-LangChain4j怎么选.md",
      "rrfScore": 0.0294,
      "vectorScore": 0.8456
    }
  ],
  "references": [
    "LangChain4j 是一个 Java 框架，旨在简化 LLM 应用的开发...",
    "LangChain4j 提供了统一的 API 来对接不同的 LLM 提供商..."
  ],
  "conversationId": "conv-1713619200000"
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| `answer` | string | LLM 生成的回答（Markdown 格式） |
| `sources` | array | 检索到的参考来源列表，按 RRF 分数降序 |
| `sources[].index` | int | 来源序号（从 1 开始） |
| `sources[].text` | string | 匹配的文档片段文本（超过 120 字符会截断） |
| `sources[].source` | string | 来源文件名 |
| `sources[].rrfScore` | double | RRF 融合得分 |
| `sources[].vectorScore` | double | 向量相似度得分 |
| `references` | array[string] | 参考来源文本摘要列表（超过 80 字符会截断） |
| `conversationId` | string | 对话 ID（用于后续多轮对话） |

**工作流程**

1. 接收用户问题
2. 如未传 `conversationId`，自动创建新对话
3. 加载该对话的历史消息（最近 10 条）
4. 解析 `agentId` 获取对应的系统提示词
5. 执行混合检索（向量 + BM25 → RRF 融合）
6. 将检索结果 + 历史上下文 + 系统提示词组装后调用 LLM
7. 保存用户消息和助手消息到 SQLite
8. 每 6 轮对话自动生成摘要并存入向量库作为长期记忆
9. 返回答案

---

### 1.2 流式问答 (SSE)

向知识库提问，通过 Server-Sent Events 逐 Token 流式返回。

```
POST /api/chat/stream
```

**请求参数**

与 [1.1 普通问答](#11-普通问答) 完全相同。

**响应格式**

`Content-Type: text/event-stream; charset=UTF-8`

SSE 事件流按顺序发送以下事件：

#### 事件 1：`meta`（元信息）

```
event: meta
data: {"conversationId":"conv-1713619200000"}
```

> 对话 ID，用于前端绑定后续消息。

#### 事件 2：`sources`（检索来源）

```
event: sources
data: [{"index":1,"text":"...","source":"file.md","rrfScore":0.0323,"vectorScore":0.87},...]
```

> 格式与普通问答的 `sources` 字段相同。

#### 事件 3：`token`（逐 Token 输出，可多次）

```
event: token
data: {"t":"Lang"}
```

```
event: token
data: {"t":"Chain"}
```

```
event: token
data: {"t":"4j"}
```

> 每个 token 事件包含一个文字片段，前端拼接显示。

#### 事件 4：`done`（完成）

```
event: done
data: {"answer":"完整回答文本...","conversationId":"conv-1713619200000","sources":[...]}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `answer` | string | 完整的回答文本 |
| `conversationId` | string | 对话 ID |
| `sources` | array | 检索来源列表（格式同 1.1） |

#### 异常事件：`error`

```
event: error
data: {"error":"Embedding API call failed: ..."}
```

**前端调用示例**

```javascript
const response = await fetch('/api/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ question: '什么是 RAG？' })
})

const reader = response.body.getReader()
const decoder = new TextDecoder()
let buffer = ''

while (true) {
  const { done, value } = await reader.read()
  if (done) break

  buffer += decoder.decode(value, { stream: true })
  const lines = buffer.split('\n')
  buffer = lines.pop() || ''

  let currentEvent = ''
  for (const line of lines) {
    if (line.startsWith('event: ')) {
      currentEvent = line.slice(7)
    } else if (line.startsWith('data: ')) {
      const data = JSON.parse(line.slice(6))
      switch (currentEvent) {
        case 'meta':
          console.log('对话ID:', data.conversationId)
          break
        case 'sources':
          console.log('检索来源:', data)
          break
        case 'token':
          process.stdout.write(data.t)  // 逐字输出
          break
        case 'done':
          console.log('\n完整回答:', data.answer)
          break
        case 'error':
          console.error('错误:', data.error)
          break
      }
    }
  }
}
```

---

## 2. 对话管理

### 2.1 创建对话

```
POST /api/conversations
```

**请求参数**

```json
{
  "agentId": "default"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `agentId` | string | 否 | 绑定的智能体 ID，不传则使用默认智能体 |

> 也支持空请求体 `{}`，此时不绑定智能体。

**响应示例**

```json
{
  "id": "conv-1713619200000",
  "title": "新对话",
  "agentId": "default",
  "createdAt": 1713619200000,
  "updatedAt": 1713619200000
}
```

---

### 2.2 获取对话列表

按最后更新时间降序返回所有对话。

```
GET /api/conversations
```

**请求参数**

无。

**响应示例**

```json
[
  {
    "id": "conv-1713619500000",
    "title": "LangChain4j 是什么？...",
    "agentId": "default",
    "createdAt": 1713619500000,
    "updatedAt": 1713619560000
  },
  {
    "id": "conv-1713619200000",
    "title": "RAG 的原理是什么...",
    "agentId": null,
    "createdAt": 1713619200000,
    "updatedAt": 1713619300000
  }
]
```

---

### 2.3 获取对话详情

获取指定对话的基本信息及全部消息列表。

```
GET /api/conversations/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 对话 ID |

**响应示例**

```json
{
  "conversation": {
    "id": "conv-1713619200000",
    "title": "RAG 的原理是什么...",
    "agentId": "default",
    "createdAt": 1713619200000,
    "updatedAt": 1713619560000
  },
  "messages": [
    {
      "id": 1,
      "conversationId": "conv-1713619200000",
      "role": "user",
      "content": "RAG 的原理是什么？",
      "sources": null,
      "createdAt": 1713619200000
    },
    {
      "id": 2,
      "conversationId": "conv-1713619200000",
      "role": "assistant",
      "content": "RAG（Retrieval-Augmented Generation）是一种...",
      "sources": [
        {
          "index": 1,
          "text": "RAG 的核心思想是将检索与生成结合...",
          "source": "第11篇-RAG核心原理.docx",
          "rrfScore": 0.0323,
          "vectorScore": 0.8712
        }
      ],
      "createdAt": 1713619205000
    }
  ]
}
```

**消息字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int | 消息自增 ID |
| `conversationId` | string | 所属对话 ID |
| `role` | string | 角色：`user`（用户）或 `assistant`（助手） |
| `content` | string | 消息内容 |
| `sources` | array/null | 仅 `assistant` 消息有值，检索来源列表 |
| `createdAt` | long | 创建时间戳 |

**错误响应**

```json
{
  "error": "Conversation not found"
}
```
> HTTP 404

---

### 2.4 更新对话标题

```
PUT /api/conversations/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 对话 ID |

**请求参数**

```json
{
  "title": "关于 RAG 的讨论"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 新标题，不能为空 |

**响应示例**

```json
{
  "status": "ok"
}
```

---

### 2.5 删除对话

删除对话及其所有消息。

```
DELETE /api/conversations/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 对话 ID |

**响应示例**

```json
{
  "status": "ok"
}
```

---

## 3. 知识库管理

### 3.1 获取文档列表

返回 `knowledge/` 目录下所有已索引文档的信息，按最后修改时间降序排列。

```
GET /api/documents
```

**请求参数**

无。

**响应示例**

```json
[
  {
    "name": "第12篇-实战：从0构建一个RAG知识库问答系统.docx",
    "size": 38258,
    "lastModified": 1713619560000,
    "format": "DOCX",
    "docType": "TECHNICAL",
    "docTypeLabel": "技术文档",
    "chunkSize": 300,
    "chunkOverlap": 100
  },
  {
    "name": "XH-001-Java也能做AI应用？.md",
    "size": 1669,
    "lastModified": 1713619200000,
    "format": "MD",
    "docType": "GENERAL",
    "docTypeLabel": "通用文档",
    "chunkSize": 200,
    "chunkOverlap": 50
  }
]
```

**文档字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 文件名 |
| `size` | long | 文件大小（字节） |
| `lastModified` | long | 最后修改时间戳 |
| `format` | string | 文件格式（大写）：`PDF`/`DOCX`/`MD`/`TXT` |
| `docType` | string | 文档类型标识：`GENERAL`/`TECHNICAL`/`FAQ`/`LOG`/`ARTICLE` |
| `docTypeLabel` | string | 文档类型中文标签 |
| `chunkSize` | int | 该文档使用的分块大小 |
| `chunkOverlap` | int | 该文档使用的分块重叠大小 |

---

### 3.2 上传文档

上传一个或多个文档到知识库，自动解析、分块并建立索引。

```
POST /api/documents/upload
Content-Type: multipart/form-data
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `files` | File(s) | 是 | 上传的文件（支持多文件） |
| `type` | string | 否 | 文档类型，默认 `GENERAL` |

支持的文档类型：

| 类型标识 | 说明 |
|----------|------|
| `GENERAL` | 通用文档 |
| `TECHNICAL` | 技术文档 |
| `FAQ` | FAQ/问答对 |
| `LOG` | 日志/结构化数据 |
| `ARTICLE` | 长文/手册 |
| `TEST` | 测试 |

支持的文件格式：`.pdf`、`.docx`、`.md`、`.txt`

**cURL 示例**

```bash
# 上传单个文件
curl -X POST http://localhost:8080/api/documents/upload \
  -F "files=@/path/to/document.pdf" \
  -F "type=TECHNICAL"

# 上传多个文件
curl -X POST http://localhost:8080/api/documents/upload \
  -F "files=@/path/to/doc1.pdf" \
  -F "files=@/path/to/doc2.docx" \
  -F "type=GENERAL"
```

**响应示例**

```json
{
  "indexed": [
    "document.pdf",
    "notes.docx"
  ],
  "failed": [],
  "documentCount": 17,
  "segmentCount": 1250
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `indexed` | array[string] | 成功索引的文件名列表 |
| `failed` | array[string] | 索引失败的文件名及原因 |
| `documentCount` | int | 索引后总文档数 |
| `segmentCount` | int | 索引后总分段数 |

**错误响应**

```json
{
  "error": "No file uploaded"
}
```
> HTTP 400

---

### 3.3 删除文档

删除指定文档文件，并自动重建剩余文档的索引。

```
DELETE /api/documents/{filename}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `filename` | 文件名（与文档列表中的 `name` 字段对应） |

**响应示例**

```json
{
  "status": "ok",
  "message": "Deleted: document.pdf",
  "documentCount": 14,
  "segmentCount": 980
}
```

**错误响应**

```json
{
  "error": "Document not found: filename.pdf"
}
```
> HTTP 404

> **注意**：删除文档后会触发全量重新索引（`reindexAll`），因为 InMemory 向量存储不支持按 ID 删除向量。

---

### 3.4 获取文档类型配置

返回当前系统中配置的所有文档类型及其分块策略。

```
GET /api/documents/types
```

**响应示例**

```json
[
  {
    "name": "GENERAL",
    "label": "通用文档",
    "chunkSize": 200,
    "chunkOverlap": 50
  },
  {
    "name": "TECHNICAL",
    "label": "技术文档",
    "chunkSize": 300,
    "chunkOverlap": 100
  },
  {
    "name": "FAQ",
    "label": "FAQ/问答对",
    "chunkSize": 256,
    "chunkOverlap": 20
  },
  {
    "name": "LOG",
    "label": "日志/结构化数据",
    "chunkSize": 512,
    "chunkOverlap": 50
  },
  {
    "name": "ARTICLE",
    "label": "长文/手册",
    "chunkSize": 1024,
    "chunkOverlap": 150
  }
]
```

---

### 3.5 获取知识库统计

```
GET /api/knowledge/stats
```

**响应示例**

```json
{
  "documentCount": 15,
  "segmentCount": 1000,
  "llmProvider": "minimax",
  "llmModel": "MiniMax-M2.5",
  "embeddingProvider": "zhipu",
  "embeddingModel": "embedding-3",
  "vectorStoreType": "memory"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentCount` | int | 已索引文档总数 |
| `segmentCount` | int | 已索引分段总数 |
| `llmProvider` | string | 当前 LLM 提供商 |
| `llmModel` | string | 当前 LLM 模型名称 |
| `embeddingProvider` | string | 当前 Embedding 提供商 |
| `embeddingModel` | string | 当前 Embedding 模型名称 |
| `vectorStoreType` | string | 向量存储类型 |

---

## 4. 智能体管理

智能体（Agent）是具有独立系统提示词的 AI 人格。每个智能体拥有不同的角色定位和回答风格。

系统初始化时会自动创建一个默认智能体（`id: "default"`），不可删除。

### 4.1 获取智能体列表

按默认优先、创建时间升序返回所有智能体。

```
GET /api/agents
```

**响应示例**

```json
[
  {
    "id": "default",
    "name": "文枢知识助手",
    "description": "默认的知识库问答助手",
    "systemPrompt": "你是「文枢·藏书阁」的知识助手...",
    "avatar": "📚",
    "isDefault": true,
    "createdAt": 1713619200000,
    "updatedAt": 1713619200000
  },
  {
    "id": "agent-a1b2c3d4",
    "name": "技术专家",
    "description": "专注于技术问题的解答",
    "systemPrompt": "你是一位资深的技术专家...",
    "avatar": "🔧",
    "isDefault": false,
    "createdAt": 1713620000000,
    "updatedAt": 1713620000000
  }
]
```

---

### 4.2 创建智能体

```
POST /api/agents
```

**请求参数**

```json
{
  "name": "技术专家",
  "description": "专注于技术问题的解答",
  "systemPrompt": "你是一位资深的技术专家，擅长解答编程和架构问题。请用专业但易懂的语言回答。",
  "avatar": "🔧"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 智能体名称，不能为空 |
| `description` | string | 否 | 智能体描述 |
| `systemPrompt` | string | 是 | 系统提示词，决定智能体的回答风格和行为，不能为空 |
| `avatar` | string | 否 | 头像（emoji 或 URL） |

**响应示例**

```json
{
  "id": "agent-a1b2c3d4",
  "name": "技术专家",
  "description": "专注于技术问题的解答",
  "systemPrompt": "你是一位资深的技术专家...",
  "avatar": "🔧",
  "isDefault": false,
  "createdAt": 1713620000000,
  "updatedAt": 1713620000000
}
```

**错误响应**

```json
{
  "error": "name and systemPrompt are required"
}
```
> HTTP 400

---

### 4.3 更新智能体

```
PUT /api/agents/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 智能体 ID |

**请求参数**

与创建智能体相同（`name`、`description`、`systemPrompt`、`avatar`）。

**响应示例**

与创建智能体响应格式相同。

**错误响应**

```json
{
  "error": "Agent not found: agent-xxx"
}
```
> HTTP 404

---

### 4.4 删除智能体

```
DELETE /api/agents/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 智能体 ID（不能为 `"default"`） |

**响应示例**

```json
{
  "status": "ok"
}
```

**错误响应**

```json
{
  "error": "Cannot delete the default agent"
}
```
> HTTP 400

---

## 5. 系统设置

### 5.1 获取当前配置

返回当前完整的系统配置。

```
GET /api/settings
```

**响应示例**

```json
{
  "llm": {
    "provider": "minimax",
    "baseUrl": "https://api.minimax.chat/v1",
    "apiKey": "sk-***",
    "modelName": "MiniMax-M2.5",
    "temperature": 0.7,
    "maxTokens": 32768,
    "streaming": true
  },
  "embedding": {
    "provider": "zhipu",
    "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
    "apiKey": "55fd***",
    "modelName": "embedding-3"
  },
  "vectorStore": {
    "type": "memory",
    "chromaBaseUrl": "http://localhost:8000",
    "collectionName": "rag_knowledge_base",
    "milvusHost": "localhost",
    "milvusPort": 19530,
    "embeddingDimension": 2048
  },
  "rag": {
    "chunkSize": 200,
    "chunkOverlap": 30,
    "vectorTopK": 5,
    "keywordTopK": 10,
    "rrfK": 60.0,
    "minScore": 0.3
  },
  "a2a": {
    "enabled": true,
    "agentName": "WenShu Agent",
    "agentDescription": "基于 RAG 的智能知识库问答 Agent..."
  },
  "documentTypes": [
    {
      "name": "GENERAL",
      "label": "通用文档",
      "chunkSize": 200,
      "chunkOverlap": 50
    }
  ]
}
```

> **注意**：此接口会返回明文 API Key，请勿在公开环境暴露。

---

### 5.2 保存配置

保存新配置并立即重建所有模型实例（LLM、Embedding、VectorStore）。保存后需手动调用 [5.3 重新索引](#53-重新索引全部文档) 来重建知识库索引。

```
POST /api/settings
```

**请求参数**

只需传需要修改的字段，未传的字段保持原值。

```json
{
  "llm": {
    "provider": "ollama",
    "baseUrl": "http://localhost:11434/v1",
    "apiKey": "ollama",
    "modelName": "qwen2.5",
    "temperature": 0.5,
    "maxTokens": 4096,
    "streaming": true
  },
  "embedding": {
    "provider": "ollama",
    "baseUrl": "http://localhost:11434/v1",
    "apiKey": "ollama",
    "modelName": "nomic-embed-text"
  },
  "vectorStore": {
    "type": "chroma",
    "chromaBaseUrl": "http://localhost:8000",
    "collectionName": "my_collection"
  },
  "rag": {
    "chunkSize": 300,
    "chunkOverlap": 50,
    "vectorTopK": 8,
    "keywordTopK": 15,
    "rrfK": 60.0
  },
  "documentTypes": [
    {
      "name": "GENERAL",
      "label": "通用文档",
      "chunkSize": 512,
      "chunkOverlap": 50
    },
    {
      "name": "CUSTOM",
      "label": "自定义类型",
      "chunkSize": 256,
      "chunkOverlap": 30
    }
  ]
}
```

**配置字段详解**

#### LLM 配置 (`llm`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `provider` | string | 提供商标识：`ollama`/`minimax`/`openai`/自定义 |
| `baseUrl` | string | API Base URL（OpenAI 兼容格式） |
| `apiKey` | string | API 密钥 |
| `modelName` | string | 模型名称 |
| `temperature` | double | 生成温度（0.0-2.0），值越大越随机 |
| `maxTokens` | int | 最大生成 Token 数 |
| `streaming` | boolean | 是否启用流式输出 |

#### Embedding 配置 (`embedding`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `provider` | string | 提供商标识 |
| `baseUrl` | string | API Base URL |
| `apiKey` | string | API 密钥 |
| `modelName` | string | Embedding 模型名称 |

#### 向量存储配置 (`vectorStore`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 存储类型：`memory`/`chroma`/`milvus` |
| `chromaBaseUrl` | string | Chroma 服务地址（type=chroma 时必填） |
| `collectionName` | string | 集合名称 |
| `milvusHost` | string | Milvus 主机地址（type=milvus 时必填） |
| `milvusPort` | int | Milvus 端口 |
| `embeddingDimension` | int | Embedding 向量维度 |

#### RAG 参数配置 (`rag`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `chunkSize` | int | 文档分块大小（Token 数） |
| `chunkOverlap` | int | 分块重叠大小 |
| `vectorTopK` | int | 向量检索返回的 Top-K 数量 |
| `keywordTopK` | int | 关键词检索返回的 Top-K 数量 |
| `rrfK` | double | RRF 融合参数 K（通常 60） |

**响应示例**

```json
{
  "status": "ok",
  "message": "Settings saved and models rebuilt"
}
```

---

### 5.3 重新索引全部文档

清除现有索引，重新加载 `knowledge/` 目录下的所有文档并建立索引。

```
POST /api/settings/reindex
```

**请求参数**

无（空请求体即可）。

**响应示例**

```json
{
  "status": "ok",
  "documents": 15,
  "segments": 1000
}
```

**错误响应**

```json
{
  "error": "Reindex failed: No documents found in knowledge/"
}
```
> HTTP 500

---

### 5.4 健康检查

```
GET /api/health
```

**响应示例**

```json
{
  "status": "running",
  "documents": 15,
  "segments": 1000,
  "llm": "MiniMax-M2.5",
  "embedding": "embedding-3"
}
```

---

## 6. A2A 协议接口

A2A（Agent-to-Agent）协议允许不同 AI Agent 节点之间相互发现和协作。

当前系统作为 A2A 服务端，暴露以下端点供其他 Agent 调用。

### 6.1 获取 Agent 卡片

返回当前 Agent 的身份和能力描述。

```
GET /a2a/v1/agent
```

**响应示例**

```json
{
  "name": "WenShu Agent",
  "description": "基于 RAG 的智能知识库问答 Agent，支持混合检索...",
  "url": "http://localhost:8080/a2a/v1",
  "version": "1.0.0",
  "capabilities": {
    "streaming": true,
    "pushNotifications": false,
    "stateTransitionHistory": false
  },
  "skills": [
    {
      "id": "rag-query",
      "name": "藏书阁问答",
      "description": "基于企业知识库的智能问答，检索并生成精准答案",
      "tags": ["knowledge", "Q&A", "RAG", "文枢"]
    }
  ],
  "securitySchemes": null
}
```

---

### 6.2 JSON-RPC 调用

A2A 协议的核心通信端点，使用 JSON-RPC 2.0 格式。

```
POST /a2a/v1/rpc
```

#### tasks/send - 发送任务

向本 Agent 发送一个知识库问答任务。

**请求示例**

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "method": "tasks/send",
  "params": {
    "id": "task-550e8400",
    "skillId": "rag-query",
    "input": {
      "question": "什么是混合检索？"
    }
  }
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 否 | 任务 ID，不传则自动生成 |
| `skillId` | string | 是 | 技能 ID，目前仅支持 `rag-query` |
| `input.question` | string | 是 | 要查询的问题 |

**成功响应**

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "task": {
      "id": "task-550e8400",
      "status": {
        "state": "completed"
      },
      "artifacts": [
        {
          "name": "藏书阁问答结果",
          "parts": [
            {
              "type": "text",
              "text": "混合检索是将向量语义检索和关键词检索（BM25）结合起来..."
            }
          ]
        }
      ]
    }
  }
}
```

**失败响应**

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "task": {
      "id": "task-550e8400",
      "status": {
        "state": "failed",
        "message": "Unknown skill: unknown-skill"
      }
    }
  }
}
```

#### tasks/get - 查询任务状态

```json
{
  "jsonrpc": "2.0",
  "id": "req-002",
  "method": "tasks/get",
  "params": {
    "taskId": "task-550e8400"
  }
}
```

#### tasks/cancel - 取消任务

```json
{
  "jsonrpc": "2.0",
  "id": "req-003",
  "method": "tasks/cancel",
  "params": {
    "taskId": "task-550e8400"
  }
}
```

#### 错误响应

当方法不存在或服务端内部错误时：

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "error": {
    "code": -32603,
    "message": "Internal error"
  }
}
```

---

### 6.3 获取任务历史

返回最近 20 条 A2A 任务记录。

```
GET /a2a/v1/tasks
```

**响应示例**

```json
[
  {
    "id": "task-550e8400",
    "skillId": "rag-query",
    "state": "completed",
    "result": "混合检索是将向量语义检索和关键词检索...",
    "error": null,
    "createdAt": 1713619200000,
    "question": "什么是混合检索？",
    "sources": [
      {
        "index": 1,
        "text": "混合检索 = 向量检索 + BM25 关键词检索...",
        "source": "第8篇-向量数据库.docx",
        "rrfScore": 0.0323,
        "vectorScore": 0.8912
      }
    ]
  },
  {
    "id": "task-6ba7b810",
    "skillId": "rag-query",
    "state": "failed",
    "result": null,
    "error": "question is required in input",
    "createdAt": 1713619300000,
    "question": null,
    "sources": null
  }
]
```

**任务状态 (`state`) 说明**

| 状态 | 说明 |
|------|------|
| `submitted` | 已提交，等待处理 |
| `working` | 正在处理中 |
| `completed` | 处理完成 |
| `failed` | 处理失败 |
| `canceled` | 已取消 |

---

## 7. 博客公开接口

博客公开接口无需鉴权，用于前端博客浏览和文章问答。

### 7.1 获取文章列表

返回已发布文章的分页列表，支持按分类和标签筛选。

```
GET /api/blog/posts
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | int | 否 | 页码，默认 1 |
| `size` | int | 否 | 每页条数，默认取配置中 `blog.postsPerPage` |
| `category` | string | 否 | 按分类名称筛选 |
| `tag` | string | 否 | 按标签名称筛选 |

**响应示例**

```json
{
  "items": [
    {
      "id": "post-a1b2c3d4",
      "title": "LangChain4j 入门指南",
      "slug": "langchain4j-guide-1234",
      "summary": "本文介绍 LangChain4j 的核心概念...",
      "content": "# LangChain4j 入门指南\n\n...",
      "contentType": "md",
      "category": "Java",
      "tags": ["LangChain4j", "AI", "Java"],
      "coverImage": "/uploads/media-abc.png",
      "status": "published",
      "isTop": false,
      "viewCount": 42,
      "wordCount": 3200,
      "publishedAt": 1713619200000,
      "createdAt": 1713619000000,
      "updatedAt": 1713619200000
    }
  ],
  "total": 15,
  "page": 1,
  "size": 10
}
```

> 列表接口返回的 `content` 为完整内容。前端可按需截取摘要展示。

---

### 7.2 获取文章详情

根据 slug 获取已发布文章的完整内容，同时自动递增阅读量。

```
GET /api/blog/posts/{slug}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `slug` | 文章的 URL 友好标识 |

**响应示例**

与 7.1 中单篇文章格式相同。

**错误响应**

```json
{ "error": "Article not found" }
```
> HTTP 404

---

### 7.3 获取分类列表

```
GET /api/blog/categories
```

**响应示例**

```json
[
  {
    "id": "cat-abc12345",
    "name": "Java",
    "slug": "java",
    "description": "Java 编程相关文章",
    "sortOrder": 0,
    "createdAt": 1713619200000,
    "updatedAt": 1713619200000
  }
]
```

---

### 7.4 获取标签列表

返回所有已发布文章中出现过的标签。

```
GET /api/blog/tags
```

**响应示例**

```json
["LangChain4j", "AI", "Java", "RAG", "向量数据库"]
```

---

### 7.5 搜索文章

按关键词搜索已发布文章（标题、内容、标签模糊匹配）。

```
GET /api/blog/search?q=keyword
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `q` | string | 是 | 搜索关键词 |

**响应示例**

返回最多 20 条匹配文章（格式同 7.1 中的 items）：

```json
[
  { "id": "post-...", "title": "...", ... }
]
```

---

### 7.6 博客统计

```
GET /api/blog/stats
```

**响应示例**

```json
{
  "totalArticles": 15,
  "totalCategories": 5,
  "totalTags": 12,
  "blogTitle": "文枢博客",
  "blogDescription": "基于 RAG 的智能博客"
}
```

---

### 7.7 博客问答

基于博客文章内容的 RAG 问答（限定 `blog:` 来源范围）。

```
POST /api/blog/chat
```

**请求参数**

```json
{
  "question": "LangChain4j 支持哪些模型？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | string | 是 | 用户问题 |

**响应示例**

```json
{
  "answer": "LangChain4j 支持多种模型提供商...",
  "sources": [
    {
      "index": 1,
      "text": "LangChain4j 支持的模型包括...",
      "source": "blog:langchain4j-guide-1234",
      "rrfScore": 0.0323,
      "vectorScore": 0.8712
    }
  ]
}
```

---

### 7.8 博客流式问答 (SSE)

与 7.7 相同的问答功能，但通过 SSE 逐 Token 流式返回。

```
POST /api/blog/chat/stream
```

**请求参数**

与 7.7 相同。

**响应格式**

`Content-Type: text/event-stream; charset=UTF-8`

事件格式与 [1.2 流式问答](#12-流式问答-sse) 相同，包含 `sources` → `token`(多次) → `done` 事件。

> 注意：博客流式问答不包含 `meta` 事件（无 conversationId）。

---

## 8. 管理员接口

所有 `/api/admin/*` 接口（除登录外）需要在请求头中携带 `X-Admin-Token` 进行鉴权。

### 8.1 管理员登录

```
POST /api/admin/login
```

**请求参数**

```json
{
  "password": "your-admin-password"
}
```

**响应示例**

```json
{
  "token": "generated-token-string",
  "status": "ok"
}
```

> 后续所有管理接口请求需携带 `X-Admin-Token: generated-token-string` 头。

**错误响应**

```json
{ "error": "Invalid password" }
```
> HTTP 401

---

### 8.2 获取全部文章

返回所有文章（含草稿），按更新时间降序。

```
GET /api/admin/posts?page=1&size=20
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | int | 否 | 页码，默认 1 |
| `size` | int | 否 | 每页条数，默认 20 |

**响应格式**

与 [7.1 获取文章列表](#71-获取文章列表) 相同的 `PageResult<Article>` 格式，但包含所有状态的文章。

---

### 8.3 创建文章

```
POST /api/admin/posts
```

**请求参数**

```json
{
  "title": "文章标题",
  "content": "# Markdown 正文\n\n...",
  "contentType": "md",
  "category": "Java",
  "tags": ["AI", "RAG"],
  "summary": "文章摘要",
  "coverImage": "/uploads/media-abc.png"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 文章标题 |
| `content` | string | 否 | Markdown 正文（默认空） |
| `contentType` | string | 否 | 内容格式，默认 `md` |
| `category` | string | 否 | 分类名称 |
| `tags` | string[] | 否 | 标签列表 |
| `summary` | string | 否 | 文章摘要 |
| `coverImage` | string | 否 | 封面图 URL |

**响应示例**

返回创建的文章对象（状态为 `draft`）。

---

### 8.4 更新文章

```
PUT /api/admin/posts/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| `id` | 文章 ID |

**请求参数**

与 8.3 相同（不含 `contentType`）。更新后如果文章已发布，向量索引会自动重建。

**响应示例**

返回更新后的文章对象。

---

### 8.5 删除文章

```
DELETE /api/admin/posts/{id}
```

如果文章已发布，同时移除向量索引。

**响应示例**

```json
{ "status": "ok" }
```

---

### 8.6 发布文章

将草稿文章发布，同时自动向量化索引到知识库。

```
POST /api/admin/posts/{id}/publish
```

**响应示例**

返回更新后的文章对象（状态为 `published`）。

---

### 8.7 取消发布文章

将已发布文章回退为草稿，同时移除向量索引。

```
POST /api/admin/posts/{id}/unpublish
```

**响应示例**

返回更新后的文章对象（状态为 `draft`）。

---

### 8.8 AI 生成摘要

调用 LLM 为指定文章自动生成摘要并更新到数据库。

```
POST /api/admin/posts/{id}/summarize
```

**说明**

- 取文章内容的前 2000 字符作为输入
- 使用当前配置的 LLM 生成 1-2 句摘要（不超过 100 字）
- 生成后自动更新文章的 `summary` 字段

**响应示例**

返回更新后的文章对象（含新的 `summary`）。

---

### 8.9 文档导入

将 DOCX/PDF 文件导入为博客文章。使用 `AutoDocumentParser` 解析文件内容后创建文章。

```
POST /api/admin/import
Content-Type: multipart/form-data
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 上传的文件（支持 .docx、.pdf） |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/api/admin/import \
  -H "X-Admin-Token: your-token" \
  -F "file=@/path/to/document.docx"
```

**响应示例**

返回创建的文章对象（状态为 `draft`，标题为文件名去扩展名）。

---

### 8.10 创建分类

```
POST /api/admin/categories
```

**请求参数**

```json
{
  "name": "Java",
  "slug": "java",
  "description": "Java 编程相关文章"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 分类名称，不能为空 |
| `slug` | string | 是 | URL 标识，不能为空 |
| `description` | string | 否 | 分类描述 |

**响应示例**

返回创建的分类对象。

---

### 8.11 更新分类

```
PUT /api/admin/categories/{id}
```

**请求参数**

与 8.10 相同。

**响应示例**

返回更新后的分类对象。

---

### 8.12 删除分类

```
DELETE /api/admin/categories/{id}
```

**响应示例**

```json
{ "status": "ok" }
```

---

## 9. 媒体管理

媒体管理接口需要管理员鉴权。上传的文件存储在 `uploads/` 目录，通过 `/uploads/{filename}` 访问。

### 9.1 获取媒体列表

```
GET /api/admin/media
```

**响应示例**

```json
[
  {
    "id": "media-abc12345",
    "filename": "screenshot.png",
    "storedName": "media-abc12345.png",
    "url": "/uploads/media-abc12345.png",
    "fileSize": 102400,
    "mimeType": "image/png",
    "createdAt": 1713619200000
  }
]
```

---

### 9.2 上传媒体文件

```
POST /api/admin/media/upload
Content-Type: multipart/form-data
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `files` | File(s) | 是 | 上传的文件（支持多文件） |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/api/admin/media/upload \
  -H "X-Admin-Token: your-token" \
  -F "files=@/path/to/image.png" \
  -F "files=@/path/to/doc.pdf"
```

**响应示例**

```json
[
  {
    "id": "media-abc12345",
    "filename": "image.png",
    "url": "/uploads/media-abc12345.png",
    "fileSize": 102400,
    "mimeType": "image/png"
  },
  {
    "id": "media-def67890",
    "filename": "doc.pdf",
    "url": "/uploads/media-def67890.pdf",
    "fileSize": 256000,
    "mimeType": "application/pdf"
  }
]
```

---

### 9.3 删除媒体文件

删除媒体记录及对应的磁盘文件。

```
DELETE /api/admin/media/{id}
```

**响应示例**

```json
{ "status": "ok" }
```

**错误响应**

```json
{ "error": "Media not found" }
```
> HTTP 404

---

## 附录：数据模型

### Conversation（对话）

```
{
  "id":          string   // 对话 ID，格式 "conv-{timestamp}"
  "title":       string   // 对话标题，初始为 "新对话"
  "agentId":     string?  // 绑定的智能体 ID，可为 null
  "createdAt":   long     // 创建时间戳（毫秒）
  "updatedAt":   long     // 最后更新时间戳（毫秒）
}
```

### Message（消息）

```
{
  "id":              int         // 消息自增 ID
  "conversationId":  string      // 所属对话 ID
  "role":            string      // "user" | "assistant"
  "content":         string      // 消息内容
  "sources":         SourceInfo[] | null  // 检索来源（仅 assistant）
  "createdAt":       long        // 创建时间戳
}
```

### SourceInfo（检索来源）

```
{
  "index":       int     // 序号（从 1 开始）
  "text":        string  // 匹配的文档片段
  "source":      string  // 来源文件名
  "rrfScore":    double  // RRF 融合得分
  "vectorScore": double  // 向量相似度得分
}
```

### Agent（智能体）

```
{
  "id":           string   // 智能体 ID，默认为 "default"
  "name":         string   // 智能体名称
  "description":  string   // 智能体描述
  "systemPrompt": string   // 系统提示词
  "avatar":       string   // 头像（emoji 或 URL）
  "isDefault":    boolean  // 是否为默认智能体
  "createdAt":    long     // 创建时间戳
  "updatedAt":    long     // 更新时间戳
}
```

### A2A Task（任务）

```
{
  "id":        string    // 任务 ID
  "skillId":   string    // 技能 ID
  "state":     string    // submitted | working | completed | failed | canceled
  "result":    string?   // 任务结果（完成时）
  "error":     string?   // 错误信息（失败时）
  "createdAt": long      // 创建时间戳
  "question":  string?   // 原始问题
  "sources":   SourceInfo[]?  // 检索来源
}
```

### Article（文章）

```
{
  "id":           string    // 文章 ID，格式 "post-{uuid}"
  "title":        string    // 标题
  "slug":         string    // URL 友好标识
  "summary":      string?   // 摘要
  "content":      string    // Markdown 正文
  "contentType":  string    // 内容格式：md
  "category":     string?   // 分类名称
  "tags":         string[]  // 标签列表
  "coverImage":   string?   // 封面图 URL
  "status":       string    // 状态：draft | published | archived
  "isTop":        boolean   // 是否置顶
  "viewCount":    int       // 阅读量
  "wordCount":    int       // 字数
  "publishedAt":  long?     // 发布时间戳（未发布为 null）
  "createdAt":    long      // 创建时间戳
  "updatedAt":    long      // 更新时间戳
}
```

### PageResult（分页结果）

```
{
  "items":  T[]     // 数据列表
  "total":  int     // 总记录数
  "page":   int     // 当前页码
  "size":   int     // 每页条数
}
```

### Category（分类）

```
{
  "id":          string   // 分类 ID，格式 "cat-{uuid}"
  "name":        string   // 分类名称
  "slug":        string   // URL 标识
  "description": string   // 分类描述
  "sortOrder":   int      // 排序权重
  "createdAt":   long     // 创建时间戳
  "updatedAt":   long     // 更新时间戳
}
```

### MediaFile（媒体文件）

```
{
  "id":         string   // 媒体 ID，格式 "media-{uuid}"
  "filename":   string   // 原始文件名
  "storedName": string   // 存储文件名（含扩展名）
  "url":        string   // 访问 URL，格式 "/uploads/{storedName}"
  "fileSize":   long     // 文件大小（字节）
  "mimeType":   string   // MIME 类型
  "createdAt":  long     // 创建时间戳
}
```
