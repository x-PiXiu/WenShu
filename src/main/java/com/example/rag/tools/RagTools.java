package com.example.rag.tools;

import com.example.rag.chat.ChatStore;
import com.example.rag.config.AppConfiguration;
import com.example.rag.service.MemoryStore;
import com.example.rag.service.RagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
            String prompt = "请将以下文本翻译为" + targetLanguage
                    + "。只输出翻译结果，不要添加任何解释或前缀：\n\n" + text;
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
            sb.append("请基于以上两份文档内容，分析它们的异同点。");
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
            sb.append("请基于以上内容生成一份结构化的摘要。");
            return sb.toString().trim();
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
}
