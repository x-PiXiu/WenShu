package com.example.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/**
 * Model Factory: dynamically creates ChatLanguageModel / EmbeddingModel from config
 * All providers use OpenAI-compatible API (Ollama, MiniMax, ZhiPu, etc.)
 */
public class ModelFactory {

    public static ChatLanguageModel createChatModel(AppConfiguration.LlmConfig config) {
        var builder = OpenAiChatModel.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.baseUrl)
                .modelName(config.modelName);

        if (config.temperature != null) {
            builder.temperature(config.temperature);
        }
        if (config.maxTokens != null) {
            builder.maxTokens(config.maxTokens);
        }

        return builder.build();
    }

    public static StreamingChatLanguageModel createStreamingChatModel(AppConfiguration.LlmConfig config) {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.baseUrl)
                .modelName(config.modelName);

        if (config.temperature != null) {
            builder.temperature(config.temperature);
        }
        if (config.maxTokens != null) {
            builder.maxTokens(config.maxTokens);
        }

        return builder.build();
    }

    public static EmbeddingModel createEmbeddingModel(AppConfiguration.EmbeddingConfig config) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.baseUrl)
                .modelName(config.modelName)
                .build();
    }
}
