package com.example.rag;

import com.example.rag.parser.AutoDocumentParser;
import com.example.rag.prompt.RagPromptTemplate;
import com.example.rag.prompt.RagPromptTemplate.Reference;
import com.example.rag.search.HybridSearcher;
import com.example.rag.search.HybridSearcher.SearchResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Path;
import java.util.List;

/**
 * RAG 知识库问答系统（LangChain4j 0.36.x 版）
 */
public class RagKnowledgeBaseApp {

    public static void main(String[] args) {
        // ========== 1. 初始化 ==========
        String miniMaxApiKey = "sk-cp-k2j9OqSVUJn4OVryHW-LlKdTmHIKsZcp6Vj_ridGL8BscdcEfwo2VQuWshFBcyt0qGPI7AwP-OsVd3fEF_FljaCY3RIj3RCSnI8wVmVZofr4wY0iLxSSJk0";//System.getenv("MINIMAX_API_KEY");
        if (miniMaxApiKey == null || miniMaxApiKey.isBlank()) {
            System.err.println("请设置环境变量 MINIMAX_API_KEY");
            System.exit(1);
        }
        String zhiPuApiKey = "55fdf7fb876a4712b6ff435dba7cb2e6.8E2FfxqHMUTux6Th";//System.getenv("MINIMAX_API_KEY");
        if (zhiPuApiKey == null || zhiPuApiKey.isBlank()) {
            System.err.println("请设置环境变量 MINIMAX_API_KEY");
            System.exit(1);
        }
        String minimaxBaseUrl = "https://api.minimax.chat/v1";
        String zhipuBaseUrl = "https://open.bigmodel.cn/api/paas/v4";

        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(miniMaxApiKey).baseUrl(minimaxBaseUrl).modelName("MiniMax-M2.5").build();

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(zhiPuApiKey).baseUrl(zhipuBaseUrl).modelName("embedding-3").build();

        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // ========== 2. Indexing：加载知识库 -> 分割 -> 向量化 -> 存储 -> 注册BM25 ==========
        System.out.println("正在索引知识库...");
        List<Document> docs = AutoDocumentParser.loadDirectory(Path.of("knowledge/"));
        if (docs.isEmpty()) {
            System.err.println("未找到任何知识库文档，请检查 knowledge/ 目录");
            System.exit(1);
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
        HybridSearcher searcher = new HybridSearcher(store, embeddingModel, 5, 10, 60, 0.5);

        int segmentCount = 0;
        for (Document doc : docs) {
            List<TextSegment> segments = splitter.split(doc);
            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment.text()).content();
                String id = store.add(embedding, segment);
                String source = segment.metadata().getString("source");
                searcher.indexSegment(id, segment.text(), source != null ? source : "unknown");
                segmentCount++;
            }
        }
        System.out.println("索引完成，共 " + docs.size() + " 个文档，" + segmentCount + " 个片段\n");

        // ========== 3. 问答循环 ==========
        System.out.println("知识库问答系统已启动（输入 exit 退出）");
        System.out.println("=".repeat(50));

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            System.out.print("\n你的问题：");
            String question = scanner.nextLine().trim();

            if (question.isEmpty()) continue;
            if (question.equalsIgnoreCase("exit")) break;

            try {
                // Step A: 混合检索（向量 + 关键词 BM25 -> RRF 融合）
                List<SearchResult> results = searcher.search(question);

                if (results.isEmpty()) {
                    System.out.println("未找到相关信息，请尝试其他表述");
                    continue;
                }

                // Step B: 转换为 Reference，构建 Prompt
                List<Reference> refs = results.stream()
                        .map(r -> new Reference(r.text(), r.source(), r.vectorScore()))
                        .toList();
                String prompt = RagPromptTemplate.build(question, refs);

                // Step C: 生成回答
                String answer = chatModel.generate(prompt);

                // Step D: 输出结果
                System.out.println("\n回答：");
                System.out.println("  " + answer);
                System.out.println("\n参考来源：");
                for (int i = 0; i < results.size(); i++) {
                    SearchResult r = results.get(i);
                    String preview = r.text().length() > 80
                            ? r.text().substring(0, 80) + "..."
                            : r.text();
                    System.out.printf("  [%d] RRF=%.4f 向量=%.4f | %s%n",
                            i + 1, r.rrfScore(), r.vectorScore(), preview);
                }
            } catch (Exception e) {
                System.err.println("处理问题时出错: " + e.getMessage());
            }
        }

        System.out.println("\n再见！");
    }
}
