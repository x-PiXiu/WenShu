# 文枢 · 藏书阁 — AI 生成封面图片提示词

> 以下提示词适用于 Midjourney、DALL-E、Stable Diffusion、通义万相、智谱清言等 AI 绘图工具。根据不同用途给出了多组提示词。

---

## 一、项目品牌主视觉（Logo / 头图）

**用途**：GitHub 仓库 Social Preview、公众号头像、项目首页 Banner

### Prompt

```
A minimalist logo design for an AI-powered knowledge base application called "文枢 WenShu". The central element is a stylized ancient Chinese book (线装书) with glowing golden threads of light flowing out from its pages, symbolizing AI bringing knowledge to life. The book rests on a subtle digital grid pattern. Color palette: warm amber (#D97B2B), dark brown (#3D3028), cream white (#FDF9F3). Clean vector style, flat design with soft gradients, white background. No text. Aspect ratio 1:1.
```

**中文辅助描述**：
- 主体是一本古风线装书，书页中流出金色光芒线条，象征 AI 激活知识
- 书下方有淡淡的数字网格纹理，代表技术底座
- 暖橙色 + 深棕色 + 米白色的品牌配色
- 极简扁平矢量风格，白底

---

## 二、公众号推文封面（16:9 横版）

**用途**：微信公众号文章封面图（900×383 或 2.35:1）

### 方案 A：概念型 — 「文档活了」

```
A wide cinematic illustration showing an ancient Chinese library study room (藏书阁) with tall wooden bookshelves filled with scrolls and books. In the center, a modern holographic AI interface floats above an open ancient book — showing chat bubbles, vector search nodes, and knowledge graph connections in warm amber light (#D97B2B). The left side is traditional (ink, wood, paper texture), the right side transitions into a modern digital aesthetic (clean lines, soft glow). The contrast between ancient knowledge storage and AI intelligence. Warm lighting, depth of field, no text. Aspect ratio 16:9.
```

**中文辅助描述**：
- 左侧是传统藏书阁场景：木质书架、竹简、古籍
- 右侧过渡到数字风格：悬浮的全息 AI 界面、对话气泡、知识图谱节点
- 中间一本打开的古书上浮现橙色光芒的 AI 交互界面
- 暖色调光影，有景深效果，不要出现文字

### 方案 B：功能展示型 — 产品截图拼贴

```
A modern tech product showcase banner for an AI knowledge base application. The layout shows 4 floating UI cards arranged in a grid: (1) a chat interface with streaming AI response, (2) a flashcard study mode with flip animation, (3) a document library with file icons, (4) a blog article with a side chat panel. Each card has soft rounded corners, warm cream background (#FDF9F3), and orange accent color (#D97B2B). The cards float above a subtle gradient background from dark brown (#3D3028) to cream white. Clean, minimal, Apple-style product photography aesthetic. No text on the cards, use placeholder UI elements. Aspect ratio 16:9.
```

**中文辅助描述**：
- 4 张悬浮的产品截图卡片：对话、闪卡、文档库、博客问答
- 卡片圆角、米白底色、橙色强调色
- 底部深棕到米白的渐变背景
- Apple 风格的产品展示美学，卡片上不放具体文字

---

## 三、功能插图（文章内配图）

### 1. 「和文档对话」配图

```
An isometric illustration of a person asking a question to a stack of documents (PDF, Word, Markdown files). The documents glow with amber light (#D97B2B) and transform into a chat bubble containing a clear, structured answer. Vector connections flow from the documents to the answer bubble, representing RAG retrieval. Warm cream background (#F8F5EF), clean flat illustration style with subtle shadows. No text. Aspect ratio 3:2.
```

**场景**：一个人向文档堆提问，文档发出橙色光芒，内容汇聚成一个回答气泡

### 2. 「闪卡学习」配图

```
An isometric illustration of a study desk with floating flashcards arranged in a circular pattern around a glowing brain icon. The flashcards show a warm amber front side and a soft green back side, some flipped mid-air. A small radar chart visualization floats nearby. Warm lighting, clean flat illustration style, cream background (#F8F5EF). No text. Aspect ratio 3:2.
```

**场景**：书桌上悬浮的闪卡围成一圈，中间是发光的大脑图标，旁边有雷达图

### 3. 「混合检索」配图

```
A technical diagram illustration showing two search paths merging into one result. Left path: a neural network icon with semantic vector arrows (labeled conceptually, no actual text). Right path: a keyword matching grid with highlighted cells. Both paths converge at a central fusion node (RRF), which outputs a ranked list of glowing document cards. Warm amber (#D97B2B) for the fusion node, soft blue for vector path, soft green for keyword path. Dark background (#3D3028), clean infographic style. No text. Aspect ratio 16:9.
```

**场景**：左侧向量检索路径、右侧关键词检索路径，汇合到中间 RRF 融合节点，输出排序后的文档卡片

### 4. 「一键部署」配图

```
A clean illustration showing a simple 3-step process: (1) a zip file being unzipped, (2) a cursor double-clicking a start button, (3) a browser window opening with a warm-toned AI chat interface. The three steps are connected by subtle dotted lines with arrows. Minimalist style, warm cream background (#F8F5EF), amber accent color (#D97B2B). No text, use icons only. Aspect ratio 16:9.
```

**场景**：解压 zip → 双击启动 → 浏览器打开，三步流程图

---

## 四、品牌配色参考

生成时如需指定颜色，项目官方配色：

| 用途 | 色值 | 说明 |
|------|------|------|
| 主强调色 | `#D97B2B` | 暖橙色（按钮、标签、高亮） |
| 主文字色 | `#3D3028` | 深棕色 |
| 背景色 | `#F8F5EF` | 暖米白 |
| 卡片背景 | `#FDF9F3` | 纯净米白 |
| 次要文字 | `#B8A898` | 灰棕色 |
| 边框线 | `#E8DDD0` | 浅驼色 |
| 成功/答案 | `#16a34a` | 绿色 |
| 危险/删除 | `#E85D5D` | 红色 |

---

## 五、使用建议

1. **公众号封面**推荐使用「方案 A 概念型」，文艺+科技的反差感适合吸引点击
2. **文章配图**逐个使用第三部分的插图，放在对应段落位置
3. **AI 绘图工具推荐**：
   - Midjourney v6：风格质感最好，适合品牌主视觉
   - DALL-E 3：理解中文描述更好，适合功能插图
   - 通义万相 / 智谱清言：免费，适合快速出草图
4. **通用技巧**：所有提示词末尾都加了 `no text`，避免 AI 生成乱码文字。文字部分后期用 PS/Figma 叠加更专业
5. 如果生成的风格偏卡通，可以在提示词末尾加 `professional, sophisticated, editorial illustration style` 加强质感
