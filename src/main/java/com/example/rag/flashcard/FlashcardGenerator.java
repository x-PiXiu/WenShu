package com.example.rag.flashcard;

import com.example.rag.prompt.PromptRegistry;
import com.example.rag.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 驱动的闪卡生成器
 * 调用 ChatModel 生成结构化 JSON 闪卡数据
 */
public class FlashcardGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RagService ragService;

    public record GenerateRequest(String text, String sourceFile,
                                   int cardCount, String difficulty) {}

    public record GeneratedCard(String front, String back,
                                 List<String> tags, int difficulty) {}

    public FlashcardGenerator(RagService ragService) {
        this.ragService = ragService;
    }

    public List<GeneratedCard> generate(GenerateRequest request) {
        String diffLabel;
        String diffNum;
        switch (request.difficulty()) {
            case "basic" -> { diffLabel = "基础(basic)：概念定义、关键术语、基本事实"; diffNum = "1"; }
            case "advanced" -> { diffLabel = "高级(advanced)：综合应用、深度推理、边缘情况"; diffNum = "3"; }
            default -> { diffLabel = "中级(intermediate)：原理机制、对比分析、因果关系"; diffNum = "2"; }
        }

        String template = PromptRegistry.getTemplate("flashcard_generate");
        if (template == null || template.isBlank()) {
            template = """
                你是「文枢·闪卡」的智能出题助手。你的任务是根据用户提供的文档内容，生成高质量、有深度的学习闪卡。

                ## 任务
                - 阅读下方【学习材料】，从中提取核心知识点，生成 ${cardCount} 张闪卡。
                - 难度级别：${difficultyLabel}。

                ## 闪卡要求
                - 每张卡的 front（正面）是一个清晰、具体的问题。问题要聚焦，避免过于宽泛。
                - 每张卡的 back（背面）必须包含两个层次的内容：
                  1. **直接回答**：用 1-2 句话直接回答问题，给出明确答案。
                  2. **深度解释**：用 2-4 句话解释「为什么」—— 阐述原理、因果关系、或与相关概念的对比，帮助理解而非死记硬背。
                - 将直接回答和深度解释写在同一个 back 字段中，用换行分隔。
                - 问题应覆盖不同类型的知识（概念定义、原理机制、对比分析、流程步骤、术语辨析等），避免重复。
                - 每张卡标注 1-3 个标签（tags），用于分类归纳。
                - difficulty 用数字表示：1=基础，2=中级，3=高级。本次统一为 ${difficultyNumber}。

                ## 输出格式
                严格输出 JSON 数组，不要包含任何其他文字或 markdown 标记。格式如下：

                [
                  {
                    "front": "问题文本",
                    "back": "直接回答\\n\\n深度解释（为什么）",
                    "tags": ["标签1", "标签2"],
                    "difficulty": ${difficultyNumber}
                  }
                ]

                ## 注意事项
                - 严格遵守 JSON 格式，不要输出 ```json 等代码围栏标记。
                - back 字段中使用 \\n\\n（两个换行）分隔直接回答和深度解释。
                - 确保问题是明确的、可验证的，避免过于主观的开放性问题。
                - 答案必须完全基于学习材料，不得编造或引入外部知识。
                - 深度解释要真正帮助理解，不是简单重复答案。
                """;
        }
        String systemPrompt = template
                .replace("${cardCount}", String.valueOf(request.cardCount()))
                .replace("${difficultyLabel}", diffLabel)
                .replace("${difficultyNumber}", diffNum);

        String userMessage = "【学习材料】\n" + request.text();

        var chatModel = ragService.getChatModel();
        if (chatModel == null) throw new RuntimeException("LLM 模型不可用");

        String raw = chatModel.chat(systemPrompt + "\n\n" + userMessage);
        String cleaned = RagService.stripThinkTags(raw);

        return parseCards(cleaned);
    }

    private List<GeneratedCard> parseCards(String response) {
        String json = response.trim();
        // 兼容 ```json ... ``` 代码围栏
        if (json.startsWith("```")) {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        // 提取第一个完整 JSON 数组
        int arrStart = json.indexOf('[');
        int arrEnd = json.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            json = json.substring(arrStart, arrEnd + 1);
        }

        try {
            return MAPPER.readValue(json, new TypeReference<List<GeneratedCard>>() {});
        } catch (Exception e) {
            // 尝试宽松解析：逐条解析
            try {
                var list = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
                List<GeneratedCard> cards = new ArrayList<>();
                for (Map<String, Object> item : list) {
                    String front = (String) item.getOrDefault("front", "");
                    String back = (String) item.getOrDefault("back", "");
                    @SuppressWarnings("unchecked")
                    List<String> tags = item.get("tags") instanceof List
                            ? (List<String>) item.get("tags")
                            : List.of();
                    int diff = item.get("difficulty") instanceof Number
                            ? ((Number) item.get("difficulty")).intValue()
                            : 2;
                    cards.add(new GeneratedCard(front, back, tags, diff));
                }
                return cards;
            } catch (Exception e2) {
                throw new RuntimeException("解析闪卡 JSON 失败: " + e2.getMessage());
            }
        }
    }
}
