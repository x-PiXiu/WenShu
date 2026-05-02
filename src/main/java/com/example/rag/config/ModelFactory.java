package com.example.rag.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.List;

/**
 * Model Factory: dynamically creates ChatModel / EmbeddingModel from config
 * All providers use OpenAI-compatible API (Ollama, MiniMax, ZhiPU, etc.)
 */
public class ModelFactory {

    public static ChatModel createChatModel(AppConfiguration.LlmConfig config) {
        return createChatModel(config, List.of());
    }

    public static ChatModel createChatModel(AppConfiguration.LlmConfig config, List<ChatModelListener> listeners) {
        var builder = OpenAiChatModel.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.baseUrl)
                .modelName(config.modelName)
                .timeout(Duration.ofSeconds(90));

        if (config.temperature != null) {
            builder.temperature(config.temperature);
        }
        if (config.maxTokens != null) {
            builder.maxTokens(config.maxTokens);
        }
        if (listeners != null && !listeners.isEmpty()) {
            builder.listeners(listeners);
        }

        return builder.build();
    }

    public static StreamingChatModel createStreamingChatModel(AppConfiguration.LlmConfig config) {
        return createStreamingChatModel(config, List.of());
    }

    public static StreamingChatModel createStreamingChatModel(AppConfiguration.LlmConfig config, List<ChatModelListener> listeners) {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.baseUrl)
                .modelName(config.modelName)
                .timeout(Duration.ofMinutes(5));

        if (config.temperature != null) {
            builder.temperature(config.temperature);
        }
        if (config.maxTokens != null) {
            builder.maxTokens(config.maxTokens);
        }
        if (listeners != null && !listeners.isEmpty()) {
            builder.listeners(listeners);
        }

        return builder.build();
    }

    public static EmbeddingModel createEmbeddingModel(AppConfiguration.EmbeddingConfig config) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.baseUrl)
                .modelName(config.modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
