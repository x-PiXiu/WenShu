# 文枢 · 博客平台开发计划

> 将现有 RAG 知识库系统扩展为「博客 + 智能问答」一体化平台

---

## 1. 需求分析

### 1.1 核心需求

| 需求 | 说明 |
|------|------|
| 博客写作 | 管理员通过内置编辑器编写 Markdown 文档 |
| 自动向量化 | 发布的文章自动分块、向量化并存储到向量库 |
| 公开阅读 | 访客可浏览文章列表、阅读文章详情 |
| 智能关联 | 访客阅读文章时可触发 RAG 问答，AI 基于博客内容回答 |
| 多格式支持 | 支持 Markdown、DOCX、PDF 作为文章来源 |
| 后台管理 | 文章 CRUD、分类/标签管理、发布状态控制 |

### 1.2 角色定义

| 角色 | 权限 |
|------|------|
| 访客（匿名） | 浏览已发布文章列表、阅读文章内容、使用博客问答 |
| 管理员 | 编写/编辑/删除文章、管理分类标签、系统设置 |

### 1.3 现有可复用模块

| 模块 | 复用方式 |
|------|----------|
| `MarkdownRenderer.vue` | 直接用于文章内容渲染 |
| `RagService` | 博客文章发布时调用 `indexDocument()` 进行向量化 |
| `HybridSearcher` | 博客问答复用混合检索引擎 |
| `AutoDocumentParser` | 解析 DOCX/PDF 导入为博客文章 |
| `AppConfiguration` | 扩展管理员密码等博客配置 |
| `ChatStore` SQLite 连接池 | 复用数据库基础设施 |
| `MarkdownIt` + `highlight.js` | 前端 Markdown 渲染和代码高亮 |

---

## 2. 系统架构设计

### 2.1 整体架构

```
┌────────────────────────────────────────────────────────────────┐
│                      前端 (Vue 3 + Vite)                       │
│                                                                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │  藏书阁   │ │  博客     │ │ 知识库    │ │  管理后台         │  │
│  │ (Chat)   │ │ (Blog)   │ │(Upload)  │ │ (Admin)          │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│       │            │            │              │                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              App.vue (导航栏扩展)                         │   │
│  └────────────────────────┬────────────────────────────────┘   │
└───────────────────────────┼────────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────────┐
│                    后端 (Javalin :8080)                          │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                     API Routes                           │   │
│  │  /api/blog/*     博客公开接口                              │   │
│  │  /api/admin/*    管理员接口（需鉴权）                       │   │
│  │  /api/chat       问答接口（已有）                          │   │
│  │  /api/documents  知识库接口（已有）                        │   │
│  └──────────────────────┬──────────────────────────────────┘   │
│                         │                                       │
│  ┌──────────────────────▼──────────────────────────────────┐   │
│  │                   BlogService                            │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │   │
│  │  │ BlogStore    │  │ BlogIndexer  │  │ AuthFilter   │  │   │
│  │  │ (SQLite)     │  │ (向量化)      │  │ (管理员鉴权)  │  │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐    │
│  │  RagService  │  │  ChatStore   │  │  AppConfig        │    │
│  │  (已有)       │  │  (已有)       │  │  (扩展博客配置)   │    │
│  └──────────────┘  └──────────────┘  └───────────────────┘    │
└────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

```
管理员编写文章 → 保存草稿 (SQLite)
                    │
                    ├── 发布 → BlogIndexer.indexArticle()
                    │            │
                    │            ├── 解析 Markdown → 纯文本
                    │            ├── 按配置策略分块 (DocumentSplitter)
                    │            ├── 调用 Embedding API 向量化
                    │            └── 存入向量存储 + BM25 索引
                    │
                    └── 取消发布 → BlogIndexer.removeFromIndex()

访客阅读文章 ← BlogStore.getPublishedArticle() ← SQLite
                    │
                    └── 访客提问 → RagService.askWithContext()
                                    │
                                    ├── HybridSearcher.search()
                                    │     (向量 + BM25 → RRF)
                                    └── LLM 生成回答
```

---

## 3. 数据库设计

### 3.1 blog_article（文章表）

```sql
CREATE TABLE blog_article (
    id              TEXT PRIMARY KEY,           -- 文章 ID: "post-{uuid}"
    title           TEXT NOT NULL,              -- 标题
    slug            TEXT NOT NULL UNIQUE,       -- URL 友好标识: my-first-post
    summary         TEXT,                       -- 摘要（自动生成或手动填写）
    content         TEXT NOT NULL,              -- Markdown 正文
    content_type    TEXT NOT NULL DEFAULT 'md', -- 内容格式: md / docx / pdf
    category        TEXT,                       -- 分类
    tags            TEXT,                       -- 标签 JSON 数组: ["Java","AI"]
    cover_image     TEXT,                       -- 封面图 URL
    status          TEXT NOT NULL DEFAULT 'draft', -- draft / published / archived
    is_top          INTEGER NOT NULL DEFAULT 0, -- 置顶
    view_count      INTEGER NOT NULL DEFAULT 0, -- 阅读量
    word_count      INTEGER NOT NULL DEFAULT 0, -- 字数
    published_at    INTEGER,                    -- 发布时间戳
    created_at      INTEGER NOT NULL,           -- 创建时间戳
    updated_at      INTEGER NOT NULL            -- 更新时间戳
);

CREATE INDEX idx_article_status ON blog_article(status);
CREATE INDEX idx_article_slug ON blog_article(slug);
CREATE INDEX idx_article_category ON blog_article(category);
CREATE INDEX idx_article_published_at ON blog_article(published_at);
```

### 3.2 blog_category（分类表）

```sql
CREATE TABLE blog_category (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,       -- 分类名称
    slug        TEXT NOT NULL UNIQUE,       -- URL 标识
    description TEXT,                       -- 分类描述
    sort_order  INTEGER NOT NULL DEFAULT 0, -- 排序权重
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);
```

### 3.3 blog_media（媒体文件表）

```sql
CREATE TABLE blog_media (
    id          TEXT PRIMARY KEY,
    filename    TEXT NOT NULL,           -- 原始文件名
    file_path   TEXT NOT NULL,           -- 存储路径
    file_size   INTEGER NOT NULL,        -- 文件大小
    mime_type   TEXT NOT NULL,           -- MIME 类型
    created_at  INTEGER NOT NULL
);
```

---

## 4. 后端设计

### 4.1 新增类结构

```
src/main/java/com/example/rag/
├── blog/
│   ├── BlogStore.java            # 文章 SQLite CRUD
│   ├── CategoryStore.java        # 分类 SQLite CRUD
│   ├── BlogIndexer.java          # 文章向量化索引管理
│   ├── MediaStore.java           # 媒体文件管理
│   └── AuthFilter.java           # 管理员鉴权过滤器
├── config/
│   └── AppConfiguration.java     # 扩展博客配置字段
└── RagApplication.java           # 注册新路由
```

### 4.2 BlogStore

```java
public class BlogStore {

    // 文章 CRUD
    Article createArticle(String title, String content, String category, List<String> tags);
    Article updateArticle(String id, String title, String content, String category, List<String> tags);
    void deleteArticle(String id);

    // 状态管理
    Article publishArticle(String id);    // draft → published
    Article archiveArticle(String id);    // published → archived
    Article unpublishArticle(String id);  // published → draft

    // 查询
    Article getById(String id);
    Article getBySlug(String slug);
    PageResult<Article> listPublished(int page, int size, String category, String tag);
    PageResult<Article> listAll(int page, int size);        // 管理员
    List<Article> searchArticles(String keyword);

    // 统计
    int countPublished();
    int countByCategory(String category);

    // Records
    record Article(String id, String title, String slug, String summary,
                   String content, String contentType, String category,
                   List<String> tags, String coverImage, String status,
                   boolean isTop, int viewCount, int wordCount,
                   Long publishedAt, long createdAt, long updatedAt) {}

    record PageResult<T>(List<T> items, int total, int page, int size) {}
}
```

### 4.3 BlogIndexer

```java
public class BlogIndexer {

    private final RagService ragService;
    private final AppConfiguration config;

    /**
     * 将已发布文章索引到向量库
     * - 从 Markdown 中提取纯文本
     * - 按文章专用分块策略分割
     * - 向量化并存储
     */
    public void indexArticle(BlogStore.Article article) {
        // 1. 解析 Markdown → 纯文本（去掉标记语法）
        String plainText = extractPlainText(article.content());

        // 2. 创建 Document 并设置 metadata
        //    metadata: source="blog:{slug}", type="blog", title=..., category=...
        Document doc = new Document(plainText, new Metadata()
            .add("source", "blog:" + article.slug())
            .add("type", "blog")
            .add("title", article.title())
            .add("articleId", article.id()));

        // 3. 使用 ARTICLE 分块策略
        DocumentSplitter splitter = createSplitter("ARTICLE");
        List<TextSegment> segments = splitter.split(doc);

        // 4. 向量化并存入向量库 + BM25 索引
        ragService.indexSegments(segments, "blog:" + article.slug());
    }

    /**
     * 从文档导入创建文章（DOCX/PDF → Markdown 转换）
     */
    public BlogStore.Article importDocument(Path file, String title, String category) {
        // 解析文档 → 纯文本
        Document parsed = AutoDocumentParser.load(file);
        // 保存为文章
        // ...
    }
}
```

### 4.4 AuthFilter

```java
public class AuthFilter {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private final String adminPassword;

    /**
     * 验证管理员身份
     * - 从 config.json 读取 admin.password
     * - 前端在管理操作时携带 X-Admin-Token 头
     */
    public boolean isAuthenticated(Context ctx) {
        String token = ctx.header(ADMIN_TOKEN_HEADER);
        return adminPassword.equals(token);
    }

    /**
     * Javalin Before Handler
     */
    public void handle(Context ctx) {
        if (!isAuthenticated(ctx)) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
        }
    }
}
```

### 4.5 配置扩展

在 `config.json` 中新增博客配置节：

```json
{
  "blog": {
    "title": "文枢博客",
    "description": "基于 RAG 的智能博客",
    "postsPerPage": 10,
    "adminPassword": "your-admin-password",
    "allowComments": false,
    "autoSummary": true
  }
}
```

### 4.6 API 端点设计

#### 公开接口（无需鉴权）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/blog/posts` | 获取已发布文章列表（分页） |
| GET | `/api/blog/posts/{slug}` | 获取文章详情 |
| GET | `/api/blog/categories` | 获取分类列表 |
| GET | `/api/blog/tags` | 获取标签列表 |
| GET | `/api/blog/search?q=keyword` | 搜索文章 |
| POST | `/api/blog/chat` | 基于博客内容的 RAG 问答 |
| POST | `/api/blog/chat/stream` | 基于博客内容的流式 RAG 问答 |
| GET | `/api/blog/stats` | 博客统计信息 |

#### 管理员接口（需鉴权）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/login` | 管理员登录验证 |
| GET | `/api/admin/posts` | 获取全部文章（含草稿） |
| POST | `/api/admin/posts` | 创建文章 |
| PUT | `/api/admin/posts/{id}` | 更新文章 |
| DELETE | `/api/admin/posts/{id}` | 删除文章 |
| POST | `/api/admin/posts/{id}/publish` | 发布文章（触发向量化） |
| POST | `/api/admin/posts/{id}/unpublish` | 取消发布（移除向量） |
| POST | `/api/admin/posts/{id}/top` | 置顶/取消置顶 |
| POST | `/api/admin/import` | 从文件导入文章（DOCX/PDF） |
| POST | `/api/admin/media/upload` | 上传媒体文件 |
| GET | `/api/admin/media` | 获取媒体列表 |
| DELETE | `/api/admin/media/{id}` | 删除媒体 |
| POST | `/api/admin/categories` | 创建分类 |
| PUT | `/api/admin/categories/{id}` | 更新分类 |
| DELETE | `/api/admin/categories/{id}` | 删除分类 |

---

## 5. 前端设计

### 5.1 新增页面

```
rag-knowledge-base-web/src/
├── components/
│   ├── blog/
│   │   ├── BlogList.vue          # 文章列表（卡片布局）
│   │   ├── BlogDetail.vue        # 文章详情页
│   │   ├── BlogSearch.vue        # 搜索组件
│   │   ├── BlogSidebar.vue       # 博客侧边栏（分类/标签/统计）
│   │   ├── BlogChat.vue          # 博客内嵌问答浮窗
│   │   └── BlogStats.vue         # 博客统计卡片
│   ├── admin/
│   │   ├── AdminDashboard.vue    # 管理面板首页
│   │   ├── PostEditor.vue        # Markdown 编辑器（含实时预览）
│   │   ├── PostList.vue          # 文章管理列表
│   │   ├── CategoryManager.vue   # 分类管理
│   │   └── MediaManager.vue      # 媒体库
│   └── ...existing components
├── composables/
│   ├── useBlog.ts                # 博客 API 调用
│   └── useAdmin.ts               # 管理员 API 调用
└── types/
    └── blog.ts                   # 博客类型定义
```

### 5.2 页面路由设计

扩展 `App.vue` 的状态路由：

```typescript
type Page = 'chat' | 'blog' | 'blog-detail' | 'knowledge' | 'a2a' | 'settings'
           | 'admin' | 'admin-editor' | 'admin-posts' | 'admin-categories' | 'admin-media'
```

| 页面状态 | 说明 | 需要鉴权 |
|----------|------|----------|
| `blog` | 博客首页，文章列表 | 否 |
| `blog-detail` | 文章详情 | 否 |
| `admin` | 管理面板首页 | 是 |
| `admin-editor` | 文章编辑器 | 是 |
| `admin-posts` | 文章管理 | 是 |
| `admin-categories` | 分类管理 | 是 |
| `admin-media` | 媒体库 | 是 |

### 5.3 关键组件设计

#### PostEditor（Markdown 编辑器）

```
┌──────────────────────────────────────────────┐
│  标题: [                                    ] │
│  分类: [下拉选择]  标签: [+ 添加标签]          │
├──────────────────────┬───────────────────────┤
│                      │                       │
│   Markdown 编辑区     │    实时预览区          │
│   (textarea)         │    (MarkdownRenderer)  │
│                      │                       │
│                      │                       │
├──────────────────────┴───────────────────────┤
│  [保存草稿]  [发布]  [从文件导入]              │
└──────────────────────────────────────────────┘
```

功能：
- 左右分栏：Markdown 输入 + 实时预览
- 工具栏：加粗、斜体、标题、列表、代码块、链接、图片上传
- 标签输入：支持自动补全已有标签
- 自动保存草稿（每 30 秒或失焦时）
- 从 DOCX/PDF 文件导入并转为 Markdown

#### BlogList（博客首页）

```
┌──────────────────────────────────────────────┐
│  文枢博客                                     │
│  [搜索文章...                              ]  │
├──────────────────────────┬───────────────────┤
│                          │                   │
│  ┌──────────────────┐    │   分类             │
│  │ 📄 文章标题       │    │   · Java          │
│  │ 摘要文本...       │    │   · AI/LLM        │
│  │ 2024-04-20 · 5min│    │   · 架构           │
│  └──────────────────┘    │                   │
│                          │   标签             │
│  ┌──────────────────┐    │   #RAG #LangChain4j│
│  │ 📄 文章标题       │    │   #向量数据库      │
│  │ 摘要文本...       │    │                   │
│  │ 2024-04-19 · 3min│    │   统计             │
│  └──────────────────┘    │   文章: 15 篇      │
│                          │   字数: 45,000     │
│  [1] [2] [3] 下一页 >    │                   │
└──────────────────────────┴───────────────────┘
```

#### BlogChat（博客问答浮窗）

```
                      ┌──────────────────────┐
                      │  🦐 问我关于本文的问题  │
                      ├──────────────────────┤
                      │  AI: 根据博客内容，... │
                      │                      │
                      │  [输入问题...       ] │
                      └──────────────────────┘
```

- 文章详情页右下角浮窗按钮
- 点击展开问答对话框
- 自动将当前文章的 slug 作为上下文传给后端
- 后端限定检索范围到博客文章向量空间

### 5.4 导航栏扩展

在现有侧边栏增加博客入口图标：

```
┌──────┐
│ Logo │
├──────┤
│  💬  │  ← 藏书阁（已有）
│  📰  │  ← 博客（新增）
│  📚  │  ← 知识库（已有）
│  📡  │  ← A2A（已有）
│  ⚙️  │  ← 设置（已有）
├──────┤
│  🔐  │  ← 管理后台（新增，需登录）
│  🟢  │  ← 状态指示灯（已有）
└──────┘
```

---

## 6. 关键技术决策

### 6.1 文章与知识库的关系

```
                 ┌─────────────────────────────┐
                 │         向量存储              │
                 │  ┌─────────┐  ┌──────────┐  │
                 │  │知识库片段 │  │博客文章片段│  │
                 │  │source:  │  │source:   │  │
                 │  │file.pdf │  │blog:slug │  │
                 │  └─────────┘  └──────────┘  │
                 └─────────────────────────────┘
```

- 博客文章和知识库文档**共享同一个向量存储**
- 通过 `metadata.source` 前缀区分来源：`blog:{slug}` vs `{filename}`
- 博客问答时可通过 source 前缀过滤，限定搜索范围
- 全局问答（藏书阁）同时搜索知识库和博客内容

### 6.2 Markdown 编辑器方案

采用**自研轻量编辑器**（非第三方富文本编辑器），理由：
- 项目已有 `MarkdownIt` 依赖，无需引入新库
- 左右分栏 + textarea 方案实现简单，维护成本低
- 与现有 `MarkdownRenderer.vue` 样式一致

如果后续需要更丰富的编辑体验，可切换为：
- **vditor**：国产 Markdown 编辑器，支持所见即所得
- **milkdown**：基于 ProseMirror 的插件化编辑器

### 6.3 管理员鉴权方案

采用**简单 Token 方案**（适合个人博客）：

1. 管理员在设置页输入密码
2. 后端验证后返回 JWT Token（或简单随机 Token）
3. 前端存储在 `localStorage`
4. 每次管理请求携带 `X-Admin-Token` 头
5. 后端 `AuthFilter` 校验

不引入 Spring Security 等重型框架。密码存储在 `config.json` 中（bcrypt 哈希）。

### 6.4 文件导入与格式转换

```
DOCX/PDF → AutoDocumentParser.load() → 纯文本
                                            │
                                            ▼
Markdown 正文 ←── 手动格式化或 AI 辅助润色 ←──┘
```

- DOCX/PDF 导入后生成纯文本，由管理员手动调整为 Markdown
- 未来可选：集成 LLM 自动将纯文本转为格式化的 Markdown

### 6.5 文章向量化策略

博客文章使用独立的分块策略：

```json
{
  "name": "BLOG_ARTICLE",
  "label": "博客文章",
  "chunkSize": 512,
  "chunkOverlap": 80
}
```

- 分块时保留文章元信息（标题、分类）作为 metadata
- 每个分块携带 `articleId`，方便溯源到原文章
- 文章更新时先删除旧索引再重建

---

## 7. 开发阶段规划

### Phase 1：基础博客功能（预计 3-4 天）

**目标**：管理员能创建、发布文章，访客能浏览阅读。

#### 后端任务

| 序号 | 任务 | 说明 |
|------|------|------|
| B1-1 | `BlogStore` 数据层 | 文章 CRUD、SQLite 表创建、分页查询 |
| B1-2 | `CategoryStore` 数据层 | 分类 CRUD |
| B1-3 | 博客配置扩展 | `AppConfiguration` 增加 `BlogConfig` |
| B1-4 | 公开 API 端点 | `/api/blog/posts`、`/api/blog/posts/{slug}`、`/api/blog/categories` |
| B1-5 | `AuthFilter` + 登录接口 | 密码验证、Token 生成 |
| B1-6 | 管理员 API 端点 | `/api/admin/posts` CRUD + 发布/取消发布 |
| B1-7 | 路由注册 | 在 `RagApplication` 中注册所有新端点 |

#### 前端任务

| 序号 | 任务 | 说明 |
|------|------|------|
| F1-1 | 博客类型定义 | `types/blog.ts`（Article、Category、PageResult） |
| F1-2 | `useBlog` composable | 公开博客 API 调用封装 |
| F1-3 | `useAdmin` composable | 管理员 API 调用封装 + Token 管理 |
| F1-4 | `BlogList.vue` | 文章列表页（卡片布局 + 分页 + 分类筛选） |
| F1-5 | `BlogDetail.vue` | 文章详情页（复用 MarkdownRenderer） |
| F1-6 | `BlogSidebar.vue` | 侧边栏（分类、标签云、统计） |
| F1-7 | `PostEditor.vue` | Markdown 编辑器（左右分栏 + 实时预览） |
| F1-8 | `PostList.vue` | 管理员文章列表（状态筛选、批量操作） |
| F1-9 | 管理员登录页 | 密码输入 → Token 存储 |
| F1-10 | 导航栏扩展 | 侧边栏增加博客和管理后台入口 |

### Phase 2：向量化集成（预计 2-3 天）

**目标**：博客文章发布时自动向量化，访客可基于博客内容问答。

#### 后端任务

| 序号 | 任务 | 说明 |
|------|------|------|
| B2-1 | `BlogIndexer` | 文章向量化索引管理（发布时索引、取消时移除） |
| B2-2 | 博客问答 API | `/api/blog/chat` 和 `/api/blog/chat/stream`，限定博客来源范围 |
| B2-3 | HybridSearcher 扩展 | 支持 source 前缀过滤（仅搜索 `blog:*` 来源） |
| B2-4 | 文章更新时索引重建 | 文章内容修改后自动删除旧索引、重新向量化 |

#### 前端任务

| 序号 | 任务 | 说明 |
|------|------|------|
| F2-1 | `BlogChat.vue` | 文章详情页的浮动问答组件 |
| F2-2 | 发布状态可视化 | 文章列表显示索引状态（已索引/未索引/索引中） |
| F2-3 | 博客统计 | 文章数、总字数、已索引片段数 |

### Phase 3：高级功能（预计 3-4 天）

**目标**：文档导入、媒体管理、搜索优化。

#### 后端任务

| 序号 | 任务 | 说明 |
|------|------|------|
| B3-1 | 文档导入 API | `/api/admin/import`，DOCX/PDF → 文章 |
| B3-2 | `MediaStore` + 上传 API | 图片/文件上传、存储、列表管理 |
| B3-3 | 静态文件服务 | Javalin 配置 `uploads/` 目录的静态文件访问 |
| B3-4 | 文章搜索 API | 全文搜索（标题 + 内容 + 标签） |
| B3-5 | 自动摘要生成 | 发布时调用 LLM 生成文章摘要 |

#### 前端任务

| 序号 | 任务 | 说明 |
|------|------|------|
| F3-1 | `MediaManager.vue` | 媒体库管理（上传、预览、删除、复制链接） |
| F3-2 | 编辑器图片上传 | 编辑器工具栏插入图片 → 调用媒体上传 API |
| F3-3 | 文档导入功能 | 管理员上传 DOCX/PDF → 预览 → 确认创建文章 |
| F3-4 | `BlogSearch.vue` | 全局搜索组件（搜索框 + 实时结果下拉） |
| F3-5 | `CategoryManager.vue` | 分类 CRUD 管理界面 |

### Phase 4：优化与上线（预计 2-3 天）

**目标**：性能优化、SEO、部署配置。

| 序号 | 任务 | 说明 |
|------|------|------|
| O4-1 | 文章列表缓存 | 已发布文章列表使用内存缓存，减少数据库查询 |
| O4-2 | 图片压缩 | 上传图片时自动生成缩略图 |
| O4-3 | RSS/Atom Feed | 生成博客订阅源 `/api/blog/feed.xml` |
| O4-4 | Sitemap | 生成 `sitemap.xml` 用于搜索引擎收录 |
| O4-5 | 前端构建优化 | 博客页面懒加载、图片懒加载 |
| O4-6 | Nginx 部署配置 | 更新 README 中的部署指南 |

---

## 8. 文件清单

### 8.1 后端新增文件

```
src/main/java/com/example/rag/blog/
├── BlogStore.java              ~300 行
├── CategoryStore.java          ~120 行
├── BlogIndexer.java            ~100 行
├── MediaStore.java             ~80 行
└── AuthFilter.java             ~40 行
```

### 8.2 后端修改文件

```
src/main/java/com/example/rag/
├── config/AppConfiguration.java    # 增加 BlogConfig 内部类
├── search/HybridSearcher.java      # 增加来源过滤参数
├── service/RagService.java         # 增加 indexSegments() 公开方法
└── RagApplication.java             # 注册新路由 + 静态文件服务
```

### 8.3 前端新增文件

```
rag-knowledge-base-web/src/
├── types/blog.ts                       ~60 行
├── composables/useBlog.ts              ~120 行
├── composables/useAdmin.ts             ~80 行
├── components/blog/BlogList.vue        ~200 行
├── components/blog/BlogDetail.vue      ~150 行
├── components/blog/BlogSearch.vue      ~80 行
├── components/blog/BlogSidebar.vue     ~120 行
├── components/blog/BlogChat.vue        ~150 行
├── components/blog/BlogStats.vue       ~60 行
├── components/admin/AdminDashboard.vue ~100 行
├── components/admin/PostEditor.vue     ~350 行
├── components/admin/PostList.vue       ~200 行
├── components/admin/CategoryManager.vue~120 行
└── components/admin/MediaManager.vue   ~180 行
```

### 8.4 前端修改文件

```
rag-knowledge-base-web/src/
├── App.vue                 # 增加博客/管理后台导航和页面路由
└── components/SettingsPage.vue  # 增加博客配置区域
```

---

## 9. 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 大量文章导致向量化耗时 | 发布操作响应慢 | 异步向量化，发布后立即返回，后台执行索引 |
| SQLite 并发写入瓶颈 | 多人同时编辑时可能锁等待 | WAL 模式已启用，busy_timeout=5000ms |
| 向量存储空间不足（InMemory） | 博客文章 + 知识库超出内存 | 推荐生产环境使用 Chroma/Milvus |
| Markdown XSS 风险 | 管理员注入恶意脚本 | MarkdownIt 已配置 `html: false`，禁止 HTML 标签 |
| 管理员密码泄露 | 未授权访问 | 密码 bcrypt 哈希存储，Token 有效期限制 |

---

## 10. 总结

本方案的核心优势：

1. **最小侵入**：复用现有 RAG 基础设施（RagService、HybridSearcher、MarkdownRenderer），新增代码集中在新模块
2. **数据统一**：博客文章和知识库文档共享向量空间，问答时可同时检索两类来源
3. **渐进式开发**：Phase 1 即可产出可用的博客系统，后续阶段按需迭代
4. **技术一致**：不引入新的框架或中间件，保持 Javalin + SQLite + LangChain4j 技术栈

预计总开发量：**约 10-12 天**，后端新增 ~640 行，前端新增 ~1850 行。
