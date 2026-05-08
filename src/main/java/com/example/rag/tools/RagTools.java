package com.example.rag.tools;

import com.example.rag.blog.BlogStore;
import com.example.rag.chat.ChatStore;
import com.example.rag.config.AppConfiguration;
import com.example.rag.prompt.PromptRegistry;
import com.example.rag.service.MemoryStore;
import com.example.rag.service.RagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RAG Agent 可调用的工具集
 * 使用 @Tool 注解定义，由 ToolEngine 提取规格并执行
 */
public class RagTools {

    private final RagService ragService;
    private final WebSearcher webSearcher;
    private final AppConfiguration config;
    private MemoryStore memoryStore;
    private ChatStore chatStore;
    private BlogStore blogStore;

    public RagTools(RagService ragService, WebSearcher webSearcher, AppConfiguration config) {
        this.ragService = ragService;
        this.webSearcher = webSearcher;
        this.config = config;
    }

    public void setMemoryStore(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    public void setChatStore(ChatStore chatStore) {
        this.chatStore = chatStore;
    }

    public void setBlogStore(BlogStore blogStore) {
        this.blogStore = blogStore;
    }

    // ===== Original Tools =====

    @Tool("在知识库中搜索与问题相关的文档片段，返回最相关的内容")
    public String searchKnowledge(@P("搜索关键词或问题") String query) {
        try {
            var results = ragService.getSearcher().search(query);
            if (results.isEmpty()) return "未找到相关文档";
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(results.size(), 3);
            for (int i = 0; i < limit; i++) {
                var r = results.get(i);
                sb.append("[").append(i + 1).append("] ");
                if (r.source() != null && !r.source().isBlank()) {
                    sb.append("(来源: ").append(r.source()).append(") ");
                }
                sb.append(r.text()).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "知识库搜索失败: " + e.getMessage();
        }
    }

    @Tool("获取当前日期和时间")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)"));
    }

    @Tool("获取知识库的统计信息，包括文档数量、分段数量、向量库类型等")
    public String getKnowledgeStats() {
        var stats = ragService.getStats();
        StringBuilder sb = new StringBuilder();
        sb.append("文档数量: ").append(stats.documentCount()).append("\n");
        sb.append("分段数量: ").append(stats.segmentCount()).append("\n");
        sb.append("LLM 提供商: ").append(stats.llmProvider()).append("\n");
        sb.append("LLM 模型: ").append(stats.llmModel()).append("\n");
        sb.append("Embedding 提供商: ").append(stats.embeddingProvider()).append("\n");
        sb.append("Embedding 模型: ").append(stats.embeddingModel()).append("\n");
        sb.append("向量库类型: ").append(stats.vectorStoreType());
        return sb.toString();
    }

    @Tool("在互联网上搜索信息。当知识库中没有相关内容时使用此工具获取外部信息")
    public String webSearch(@P("搜索关键词") String query) {
        if (webSearcher == null || !webSearcher.isConfigured()) {
            return "互联网搜索未配置。请在管理面板的设置页面配置搜索服务（Tavily/SerpAPI/自定义）。";
        }
        try {
            return webSearcher.search(query);
        } catch (Exception e) {
            return "互联网搜索失败: " + e.getMessage();
        }
    }

    // ===== Memory Management =====

    @Tool("保存一条记忆到长期记忆库，用于在未来的对话中回忆关键信息")
    public String saveMemory(@P("记忆内容摘要") String summary,
                             @P("重要性评分，0到1之间，默认0.5") double importance) {
        if (memoryStore == null) return "记忆系统未启用";
        try {
            importance = Math.max(0, Math.min(1, importance));
            var entry = memoryStore.storeMemory("tool-call", summary, importance);
            return "记忆已保存，ID: " + entry.id() + "，重要性: " + String.format("%.2f", importance);
        } catch (Exception e) {
            return "保存记忆失败: " + e.getMessage();
        }
    }

    @Tool("从长期记忆库中搜索与查询相关的历史记忆")
    public String recallMemory(@P("搜索关键词或问题") String query) {
        if (memoryStore == null || memoryStore.getMemoryCount() == 0) return "记忆库为空或未启用";
        try {
            var memories = ragService.recallMemories(query, 5);
            if (memories.isEmpty()) return "未找到相关记忆";
            StringBuilder sb = new StringBuilder("找到以下相关记忆：\n");
            for (int i = 0; i < memories.size(); i++) {
                String mem = memories.get(i);
                if (mem.startsWith("[记忆摘要] ")) mem = mem.substring("[记忆摘要] ".length());
                sb.append(i + 1).append(". ").append(mem).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "搜索记忆失败: " + e.getMessage();
        }
    }

    // ===== Document Management =====

    @Tool("列出知识库中所有文档及其元数据信息")
    public String listDocuments() {
        try {
            var allMeta = ragService.getDocumentMeta();
            if (allMeta.isEmpty()) return "知识库中没有文档";
            StringBuilder sb = new StringBuilder("知识库文档列表：\n");
            int i = 1;
            for (var entry : allMeta.entrySet()) {
                sb.append(i++).append(". ").append(entry.getKey());
                var meta = entry.getValue();
                sb.append(" (类型: ").append(meta.type())
                        .append(", 分块大小: ").append(meta.chunkSize())
                        .append(")\n");
            }
            var stats = ragService.getStats();
            sb.append("\n共 ").append(stats.documentCount()).append(" 文档, ")
                    .append(stats.segmentCount()).append(" 分段");
            return sb.toString().trim();
        } catch (Exception e) {
            return "获取文档列表失败: " + e.getMessage();
        }
    }

    @Tool("按文档来源名称搜索相关文档片段")
    public String searchBySource(@P("文档来源名称或关键词") String sourceKeyword) {
        try {
            var results = ragService.getSearcher().search(sourceKeyword).stream()
                    .filter(r -> r.source() != null
                            && r.source().toLowerCase().contains(sourceKeyword.toLowerCase()))
                    .limit(5)
                    .toList();
            if (results.isEmpty()) return "未找到来源包含 \"" + sourceKeyword + "\" 的文档片段";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                var r = results.get(i);
                sb.append("[").append(i + 1).append("] (来源: ").append(r.source()).append(") ");
                String text = r.text().length() > 300 ? r.text().substring(0, 300) + "..." : r.text();
                sb.append(text).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "搜索文档失败: " + e.getMessage();
        }
    }

    // ===== Conversation Export =====

    @Tool("导出指定对话的完整聊天记录，包含对话标题、时间和所有消息")
    public String exportConversation(@P("对话ID") String conversationId) {
        if (chatStore == null) return "聊天存储未启用";
        try {
            var conv = chatStore.getConversation(conversationId);
            if (conv == null) return "未找到对话: " + conversationId;
            var messages = chatStore.listMessages(conversationId);
            if (messages.isEmpty()) return "对话为空";
            StringBuilder sb = new StringBuilder();
            sb.append("对话标题: ").append(conv.title()).append("\n");
            sb.append("创建时间: ").append(formatTimestamp(conv.createdAt())).append("\n");
            sb.append("消息数: ").append(messages.size()).append("\n\n");
            for (var msg : messages) {
                sb.append("[").append(msg.role()).append("] ");
                String content = msg.content();
                if (content.length() > 500) content = content.substring(0, 500) + "...";
                sb.append(content).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "导出对话失败: " + e.getMessage();
        }
    }

    // ===== Calculator =====

    @Tool("计算数学表达式，支持加减乘除、幂运算、括号和基本数学函数（sqrt, sin, cos, tan, abs, log）")
    public String calculate(@P("数学表达式，如 (2+3)*4 或 sqrt(16) 或 2^10") String expression) {
        try {
            double result = evaluateExpression(expression.trim());
            if (result == (long) result) {
                return expression + " = " + (long) result;
            }
            return expression + " = " + result;
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    // ===== Translation =====

    @Tool("将文本翻译为指定语言，使用 LLM 进行高质量翻译")
    public String translate(@P("要翻译的文本") String text,
                            @P("目标语言，如 English、日语、法语、韩语") String targetLanguage) {
        try {
            var chatModel = ragService.getChatModel();
            if (chatModel == null) return "LLM 模型不可用";
            String tmpl = PromptRegistry.getTemplate("translate");
            String prompt;
            if (tmpl != null && !tmpl.isBlank() && tmpl.contains("${targetLanguage}")) {
                prompt = tmpl.replace("${targetLanguage}", targetLanguage).replace("${text}", text);
            } else {
                prompt = "请将以下文本翻译为" + targetLanguage
                        + "。只输出翻译结果，不要添加任何解释或前缀：\n\n" + text;
            }
            String result = chatModel.chat(prompt);
            return RagService.stripThinkTags(result);
        } catch (Exception e) {
            return "翻译失败: " + e.getMessage();
        }
    }

    // ===== Document Comparison =====

    @Tool("对比两个文档或知识来源的内容，分别检索后返回内容供分析")
    public String compareDocuments(@P("第一个文档的来源名称") String source1,
                                   @P("第二个文档的来源名称") String source2) {
        try {
            var results1 = ragService.getSearcher().search(source1).stream()
                    .filter(r -> r.source() != null
                            && r.source().toLowerCase().contains(source1.toLowerCase()))
                    .limit(3).toList();
            var results2 = ragService.getSearcher().search(source2).stream()
                    .filter(r -> r.source() != null
                            && r.source().toLowerCase().contains(source2.toLowerCase()))
                    .limit(3).toList();
            if (results1.isEmpty()) return "未找到文档: " + source1;
            if (results2.isEmpty()) return "未找到文档: " + source2;

            StringBuilder sb = new StringBuilder();
            sb.append("【文档A: ").append(source1).append("】\n");
            for (var r : results1) {
                String text = r.text().length() > 300 ? r.text().substring(0, 300) + "..." : r.text();
                sb.append(text).append("\n\n");
            }
            sb.append("【文档B: ").append(source2).append("】\n");
            for (var r : results2) {
                String text = r.text().length() > 300 ? r.text().substring(0, 300) + "..." : r.text();
                sb.append(text).append("\n\n");
            }
            String compareSuffix = PromptRegistry.getTemplate("document_compare");
            sb.append(compareSuffix != null && !compareSuffix.isBlank()
                    ? compareSuffix : "请基于以上两份文档内容，分析它们的异同点。");
            return sb.toString().trim();
        } catch (Exception e) {
            return "文档对比失败: " + e.getMessage();
        }
    }

    // ===== Summary Generation =====

    @Tool("根据知识库中的文档内容生成摘要，检索相关片段后供分析总结")
    public String generateSummary(@P("要生成摘要的文档来源名称或主题关键词") String sourceOrTopic) {
        try {
            var results = ragService.getSearcher().search(sourceOrTopic);
            if (results.isEmpty()) return "未找到与 \"" + sourceOrTopic + "\" 相关的文档";
            StringBuilder sb = new StringBuilder();
            sb.append("以下是与 \"").append(sourceOrTopic).append("\" 相关的文档片段：\n\n");
            int limit = Math.min(results.size(), 5);
            for (int i = 0; i < limit; i++) {
                var r = results.get(i);
                String text = r.text().length() > 400 ? r.text().substring(0, 400) + "..." : r.text();
                sb.append("[").append(i + 1).append("] ").append(text).append("\n\n");
            }
            String summarySuffix = PromptRegistry.getTemplate("search_summary");
            sb.append(summarySuffix != null && !summarySuffix.isBlank()
                    ? summarySuffix : "请基于以上内容生成一份结构化的摘要。");
            return sb.toString().trim();
        } catch (Exception e) {
            return "摘要生成失败: " + e.getMessage();
        }
    }

    // ===== Document CRUD Tools =====

    @Tool("读取知识库中指定文档的完整文本内容")
    public String readDocument(@P("文档文件名") String filename) {
        try {
            String content = ragService.readDocumentFile(filename);
            if (content == null) return "文档不存在: " + filename;
            if (content.length() > 8000) {
                return content.substring(0, 8000) + "\n\n... (文档过长，已截断，总长度: " + content.length() + " 字符)";
            }
            return content;
        } catch (Exception e) {
            return "读取文档失败: " + e.getMessage();
        }
    }

    @Tool("创建新文档并自动索引到知识库。支持 Markdown 纯文本内容")
    public String createDocument(@P("文档标题") String title,
                                 @P("文档内容，支持 Markdown 格式") String content,
                                 @P("文档类型：GENERAL/TECHNICAL/FAQ/ARTICLE，留空自动检测") String type) {
        try {
            if (title == null || title.isBlank()) return "文档标题不能为空";
            if (content == null || content.isBlank()) return "文档内容不能为空";
            String safeName = ragService.createDocumentFile(title, content, type);
            return "文档已创建并索引: " + safeName;
        } catch (Exception e) {
            return "创建文档失败: " + e.getMessage();
        }
    }

    @Tool("更新知识库中已有文档的内容并重新索引。会先展示差异供用户确认后再执行更新。")
    public String updateDocument(@P("要更新的文档文件名") String filename,
                                 @P("新的文档内容") String content) {
        try {
            if (filename == null || filename.isBlank()) return "文件名不能为空";
            if (content == null || content.isBlank()) return "内容不能为空";

            // 读取旧内容，暂存变更
            String oldContent = ragService.readDocumentFile(filename);
            if (oldContent == null) return "文档不存在: " + filename;

            java.nio.file.Path file = java.nio.file.Path.of("knowledge").resolve(filename).normalize();
            var change = PendingFileChanges.stage(file.toAbsolutePath().toString(), oldContent, content, "document");
            int oldLines = oldContent.split("\n").length;
            int newLines = content.split("\n").length;
            return "文档修改已暂存，等待用户确认。changeId=" + change.id()
                    + " file=" + filename
                    + " 旧=" + oldLines + "行 新=" + newLines + "行"
                    + " PENDING_CONFIRM=true";
        } catch (Exception e) {
            return "更新文档失败: " + e.getMessage();
        }
    }

    @Tool("获取知识库中指定文档的详细信息，包括文件大小、类型、分块参数等")
    public String getDocumentInfo(@P("文档文件名") String filename) {
        try {
            var allMeta = ragService.getDocumentMeta();
            var meta = allMeta.get(filename);
            if (meta == null) return "未找到文档元数据: " + filename;

            java.nio.file.Path file = java.nio.file.Path.of("knowledge").resolve(filename);
            StringBuilder sb = new StringBuilder();
            sb.append("文档: ").append(filename).append("\n");
            if (java.nio.file.Files.exists(file)) {
                sb.append("文件大小: ").append(java.nio.file.Files.size(file)).append(" 字节\n");
                sb.append("最后修改: ").append(formatTimestamp(java.nio.file.Files.getLastModifiedTime(file).toMillis())).append("\n");
            }
            sb.append("文档类型: ").append(meta.type()).append("\n");
            sb.append("分块大小: ").append(meta.chunkSize()).append("\n");
            sb.append("分块重叠: ").append(meta.chunkOverlap()).append("\n");
            sb.append("检测方法: ").append(meta.detectionMethod()).append("\n");

            var typeConfig = config.findDocTypeConfig(meta.type());
            sb.append("句子重叠数: ").append(typeConfig.overlapSentences);
            return sb.toString().trim();
        } catch (Exception e) {
            return "获取文档信息失败: " + e.getMessage();
        }
    }

    // ===== Blog Tools =====

    @Tool("搜索博客中已发布的文章，返回匹配的文章列表")
    public String blogSearch(@P("搜索关键词") String query) {
        if (blogStore == null) return "博客系统未启用";
        try {
            var articles = blogStore.searchArticles(query);
            if (articles.isEmpty()) return "未找到匹配的博客文章";
            StringBuilder sb = new StringBuilder("找到 ").append(articles.size()).append(" 篇相关文章：\n");
            for (var a : articles) {
                sb.append("- ").append(a.title());
                if (a.category() != null && !a.category().isBlank()) sb.append(" [").append(a.category()).append("]");
                if (a.summary() != null && !a.summary().isBlank()) sb.append("\n  摘要: ").append(a.summary());
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "博客搜索失败: " + e.getMessage();
        }
    }

    @Tool("创建一篇新的博客文章（默认为草稿状态）")
    public String blogWrite(@P("文章标题") String title,
                            @P("文章内容，支持 Markdown 格式") String content,
                            @P("文章分类，留空则不分类") String category) {
        if (blogStore == null) return "博客系统未启用";
        try {
            if (title == null || title.isBlank()) return "文章标题不能为空";
            if (content == null || content.isBlank()) return "文章内容不能为空";
            var article = blogStore.createArticle(title, content, "md", category, List.of(), null, null);
            return "博客文章已创建（草稿状态）\nID: " + article.id() + "\n标题: " + article.title();
        } catch (Exception e) {
            return "创建博客文章失败: " + e.getMessage();
        }
    }

    @Tool("使用 LLM 自动生成文章或文本的摘要")
    public String blogSummarize(@P("需要生成摘要的文本内容") String content) {
        try {
            var chatModel = ragService.getChatModel();
            if (chatModel == null) return "LLM 模型不可用";
            String tmpl = PromptRegistry.getTemplate("article_summary");
            String prompt = (tmpl != null && !tmpl.isBlank() ? tmpl
                    : "请用1-2句话总结以下文章的核心内容，不超过100字，直接输出摘要文本，不要加引号或前缀：\n\n")
                    + (content.length() > 2000 ? content.substring(0, 2000) : content);
            return RagService.stripThinkTags(chatModel.chat(prompt));
        } catch (Exception e) {
            return "摘要生成失败: " + e.getMessage();
        }
    }

    // ===== Helpers =====

    private static String formatTimestamp(long ts) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Recursive descent parser for arithmetic expressions.
     * Supports: +, -, *, /, ^, parentheses, numbers, and math functions
     * (sqrt, sin, cos, tan, abs, log).
     */
    private static double evaluateExpression(String expr) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("意外字符: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                while (true) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                while (true) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = pos;

                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, pos));
                } else if (ch >= 'a' && ch <= 'z') {
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = expr.substring(startPos, pos);
                    x = parseFactor();
                    x = switch (func) {
                        case "sqrt" -> Math.sqrt(x);
                        case "abs" -> Math.abs(x);
                        case "log" -> Math.log(x);
                        case "sin" -> Math.sin(x);
                        case "cos" -> Math.cos(x);
                        case "tan" -> Math.tan(x);
                        default -> throw new RuntimeException("未知函数: " + func);
                    };
                } else {
                    throw new RuntimeException("意外字符: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }

    // ===== File System Tools =====

    private java.nio.file.Path resolvePath(String path) {
        java.nio.file.Path resolved = java.nio.file.Path.of(path);
        if (!resolved.isAbsolute()) {
            resolved = java.nio.file.Path.of(config.getWorkDir()).resolve(resolved);
        }
        return resolved.normalize();
    }

    @Tool("在指定路径写入文件。如果路径不存在会自动创建目录。支持绝对路径和相对路径（相对于工作目录）。会先展示内容差异供用户确认后再执行写入。")
    public String writeFile(@P("文件路径，如 /tmp/test.txt 或 output/report.md") String path,
                            @P("要写入的文件内容") String content) {
        try {
            if (path == null || path.isBlank()) return "文件路径不能为空";
            if (content == null) content = "";
            java.nio.file.Path file = resolvePath(path);
            String oldContent = java.nio.file.Files.exists(file) ? java.nio.file.Files.readString(file) : "";

            // 所有文件变更（新建和修改）都暂存，等待用户确认
            var change = PendingFileChanges.stage(file.toAbsolutePath().toString(), oldContent, content, "write");
            int oldLines = oldContent.isEmpty() ? 0 : oldContent.split("\n").length;
            int newLines = content.isEmpty() ? 0 : content.split("\n").length;
            return "文件变更已暂存，等待用户确认。changeId=" + change.id()
                    + " path=" + file.toAbsolutePath()
                    + " 旧=" + oldLines + "行 新=" + newLines + "行"
                    + " PENDING_CONFIRM=true";
        } catch (Exception e) {
            return "写入文件失败: " + e.getMessage();
        }
    }

    @Tool("读取指定路径的文件内容。支持绝对路径和相对路径")
    public String readFile(@P("文件路径") String path) {
        try {
            if (path == null || path.isBlank()) return "文件路径不能为空";
            java.nio.file.Path file = resolvePath(path);
            if (!java.nio.file.Files.exists(file)) return "文件不存在: " + file.toAbsolutePath();
            String content = java.nio.file.Files.readString(file);
            if (content.length() > 8000) {
                return content.substring(0, 8000) + "\n\n... (文件过长，已截断，总长度: " + content.length() + " 字符)";
            }
            return content;
        } catch (Exception e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool("列出指定目录下的文件和子目录。不传路径则列出当前工作目录")
    public String listFiles(@P("目录路径，留空则使用默认工作目录") String path) {
        try {
            java.nio.file.Path dir = (path != null && !path.isBlank()) ? resolvePath(path) : java.nio.file.Path.of(config.getWorkDir());
            if (!java.nio.file.Files.isDirectory(dir)) return "目录不存在: " + dir.toAbsolutePath();
            StringBuilder sb = new StringBuilder("目录: ").append(dir.toAbsolutePath()).append("\n");
            try (var stream = java.nio.file.Files.list(dir)) {
                stream.limit(100).forEach(p -> {
                    try {
                        String type = java.nio.file.Files.isDirectory(p) ? "[DIR]" : "[FILE]";
                        long size = java.nio.file.Files.isRegularFile(p) ? java.nio.file.Files.size(p) : 0;
                        sb.append(type).append(" ").append(p.getFileName());
                        if (size > 0) sb.append(" (").append(size).append(" bytes)");
                        sb.append("\n");
                    } catch (Exception ignored) {}
                });
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "列出文件失败: " + e.getMessage();
        }
    }

    @Tool("创建目录，支持多级创建。路径不存在时自动创建所有父目录")
    public String createDirectory(@P("要创建的目录路径") String path) {
        try {
            if (path == null || path.isBlank()) return "目录路径不能为空";
            java.nio.file.Path dir = resolvePath(path);
            java.nio.file.Files.createDirectories(dir);
            return "目录已创建: " + dir.toAbsolutePath();
        } catch (Exception e) {
            return "创建目录失败: " + e.getMessage();
        }
    }
}
