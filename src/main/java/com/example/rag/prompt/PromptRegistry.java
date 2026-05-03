package com.example.rag.prompt;

import com.example.rag.config.AppConfiguration;

import java.util.Map;

/**
 * 全局 Prompt 注册表
 * 从 AppConfiguration.prompts 读取模板，提供静态访问方法
 */
public class PromptRegistry {

    private static Map<String, AppConfiguration.PromptEntry> prompts;

    public static void init(AppConfiguration config) {
        prompts = config.getPrompts();
    }

    /**
     * 获取 prompt 模板文本
     * @return 模板字符串，如果 key 不存在返回 null
     */
    public static String getTemplate(String key) {
        if (prompts == null) return null;
        var entry = prompts.get(key);
        return entry != null ? entry.template : null;
    }

    /**
     * 获取 prompt 描述（人类可读名称）
     */
    public static String getDescription(String key) {
        if (prompts == null) return key;
        var entry = prompts.get(key);
        return entry != null && entry.description != null ? entry.description : key;
    }

    /**
     * 获取 prompt 分类（core / summary / tool）
     */
    public static String getCategory(String key) {
        if (prompts == null) return "core";
        var entry = prompts.get(key);
        return entry != null && entry.category != null ? entry.category : "core";
    }
}
