package com.example.rag.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * LLM 调用监听器：自动拦截所有 ChatModel / StreamingChatModel 调用
 * 记录 Token 消耗、延迟、模型、完成原因等
 */
public class LlmCallListener implements ChatModelListener {

    private final LlmCallStore store;
    private final String callType;

    public LlmCallListener(LlmCallStore store, String callType) {
        this.store = store;
        this.callType = callType;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put("startTime", System.currentTimeMillis());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        Long startTime = (Long) context.attributes().get("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

        ChatResponse response = context.chatResponse();
        String model = response.modelName();
        if (model == null || model.isEmpty()) {
            model = context.chatRequest().modelName();
        }

        int inputTokens = 0, outputTokens = 0;
        if (response.tokenUsage() != null) {
            inputTokens = response.tokenUsage().inputTokenCount();
            outputTokens = response.tokenUsage().outputTokenCount();
        }

        String finishReason = response.finishReason() != null ? response.finishReason().name() : null;

        store.logCall(callType, model, inputTokens, outputTokens, duration, finishReason, null);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        Long startTime = (Long) context.attributes().get("startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

        String model = context.chatRequest() != null ? context.chatRequest().modelName() : null;
        String error = context.error() != null ? context.error().getMessage() : "unknown";

        store.logCall(callType, model, 0, 0, duration, null, truncate(error, 200));
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
