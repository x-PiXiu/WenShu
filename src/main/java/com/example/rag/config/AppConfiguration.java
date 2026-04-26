package com.example.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用配置：LLM / Embedding / VectorStore / RAG 参数
 * 持久化到 config.json，支持运行时动态修改
 */
public class AppConfiguration {

    private static final String CONFIG_FILE = "config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private LlmConfig llm;
    private EmbeddingConfig embedding;
    private VectorStoreConfig vectorStore;
    private RagConfig rag;
    private A2AConfig a2a;
    private List<DocumentTypeConfig> documentTypes;

    public static class LlmConfig {
        public String provider = "ollama";
        public String baseUrl = "http://localhost:11434/v1";
        public String apiKey = "ollama";
        public String modelName = "qwen2.5";
        public Double temperature = 0.7;
        public Integer maxTokens = 2048;
        public boolean streaming = true;
    }

    public static class EmbeddingConfig {
        public String provider = "ollama";
        public String baseUrl = "http://localhost:11434/v1";
        public String apiKey = "ollama";
        public String modelName = "nomic-embed-text";
    }

    public static class VectorStoreConfig {
        public String type = "memory";
        // Chroma
        public String chromaBaseUrl = "http://localhost:8000";
        public String collectionName = "rag_knowledge_base";
        // Milvus
        public String milvusHost = "localhost";
        public int milvusPort = 19530;
        public int embeddingDimension = 768;
    }

    public static class RagConfig {
        public int chunkSize = 300;
        public int chunkOverlap = 30;
        public int vectorTopK = 5;
        public int keywordTopK = 10;
        public double rrfK = 60;
        public double minScore = 0.5;
    }

    public static class A2AConfig {
        public boolean enabled = true;
        public String agentName = "WenShu Agent";
        public String agentDescription = "基于 RAG 的智能知识库问答 Agent，支持混合检索（向量+关键词）、动态 LLM/Embedding 切换、多种向量存储后端，兼容 A2A 协议实现跨 Agent 协作";
    }

    public static class DocumentTypeConfig {
        public String name;
        public String label;
        public int chunkSize;
        public int chunkOverlap;

        public DocumentTypeConfig() {}

        public DocumentTypeConfig(String name, String label, int chunkSize, int chunkOverlap) {
            this.name = name;
            this.label = label;
            this.chunkSize = chunkSize;
            this.chunkOverlap = chunkOverlap;
        }
    }

    public static AppConfiguration load() {
        Path path = Path.of(CONFIG_FILE);
        if (Files.exists(path)) {
            try {
                return MAPPER.readValue(Files.readString(path), AppConfiguration.class);
            } catch (IOException e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        }
        AppConfiguration config = createDefault();
        config.save();
        return config;
    }

    public void save() {
        try {
            MAPPER.writeValue(Path.of(CONFIG_FILE).toFile(), this);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public static AppConfiguration createDefault() {
        AppConfiguration config = new AppConfiguration();
        config.llm = new LlmConfig();
        config.embedding = new EmbeddingConfig();
        config.vectorStore = new VectorStoreConfig();
        config.rag = new RagConfig();
        config.a2a = new A2AConfig();
        config.documentTypes = createDefaultDocumentTypes();
        return config;
    }


public LlmConfig getLlm() { return llm; }
    public EmbeddingConfig getEmbedding() { return embedding; }
    public VectorStoreConfig getVectorStore() { return vectorStore; }
    public RagConfig getRag() { return rag; }
    public A2AConfig getA2a() { return a2a; }

    public void setLlm(LlmConfig llm) { this.llm = llm; }
    public void setEmbedding(EmbeddingConfig embedding) { this.embedding = embedding; }
    public void setVectorStore(VectorStoreConfig vectorStore) { this.vectorStore = vectorStore; }
    public void setRag(RagConfig rag) { this.rag = rag; }
    public void setA2a(A2AConfig a2a) { this.a2a = a2a; }

    public List<DocumentTypeConfig> getDocumentTypes() {
        return documentTypes != null ? documentTypes : createDefaultDocumentTypes();
    }

    public void setDocumentTypes(List<DocumentTypeConfig> documentTypes) { this.documentTypes = documentTypes; }

    /**
     * 根据 DocumentType 名称查找配置的分块参数
     */
    public DocumentTypeConfig findDocTypeConfig(String typeName) {
        if (documentTypes != null) {
            for (DocumentTypeConfig dtc : documentTypes) {
                if (dtc.name.equalsIgnoreCase(typeName)) return dtc;
            }
        }
        // Fallback to GENERAL defaults
        return new DocumentTypeConfig("GENERAL", "通用文档", 512, 50);
    }

    public static List<DocumentTypeConfig> createDefaultDocumentTypes() {
        List<DocumentTypeConfig> list = new ArrayList<>();
        list.add(new DocumentTypeConfig("GENERAL", "通用文档", 512, 50));
        list.add(new DocumentTypeConfig("TECHNICAL", "技术文档", 800, 100));
        list.add(new DocumentTypeConfig("FAQ", "FAQ/问答对", 256, 20));
        list.add(new DocumentTypeConfig("LOG", "日志/结构化数据", 512, 50));
        list.add(new DocumentTypeConfig("ARTICLE", "长文/手册", 1024, 150));
        return list;
    }
}
