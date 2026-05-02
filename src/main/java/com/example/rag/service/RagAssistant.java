package com.example.rag.service;

import dev.langchain4j.service.TokenStream;

/**
 * AI 助手接口，由 LangChain4j AiServices 动态实现。
 * 支持同步和流式两种调用模式，自动处理 Tool Calling 多轮循环。
 */
public interface RagAssistant {
    String chat(String message);
    TokenStream chatStream(String message);
}
