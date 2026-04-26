package com.example.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;

/**
 * Vector Store Factory: dynamically creates EmbeddingStore from config
 * Supports: InMemory, Chroma, Milvus
 */
public class VectorStoreFactory {

    public static EmbeddingStore<TextSegment> create(AppConfiguration.VectorStoreConfig config) {
        return switch (config.type) {
            case "chroma" -> createChromaStore(config);
            case "milvus" -> createMilvusStore(config);
            default -> createInMemoryStore();
        };
    }

    private static EmbeddingStore<TextSegment> createInMemoryStore() {
        System.out.println("[VectorStore] Using InMemory");
        return new InMemoryEmbeddingStore<>();
    }

    private static EmbeddingStore<TextSegment> createChromaStore(AppConfiguration.VectorStoreConfig config) {
        System.out.println("[VectorStore] Using Chroma: " + config.chromaBaseUrl
                + " collection=" + config.collectionName);
        return ChromaEmbeddingStore.builder()
                .baseUrl(config.chromaBaseUrl)
                .collectionName(config.collectionName)
                .build();
    }

    private static EmbeddingStore<TextSegment> createMilvusStore(AppConfiguration.VectorStoreConfig config) {
        System.out.println("[VectorStore] Using Milvus: " + config.milvusHost
                + ":" + config.milvusPort + " collection=" + config.collectionName
                + " dimension=" + config.embeddingDimension);
        return MilvusEmbeddingStore.builder()
                .host(config.milvusHost)
                .port(config.milvusPort)
                .collectionName(config.collectionName)
                .dimension(config.embeddingDimension)
                .build();
    }
}
