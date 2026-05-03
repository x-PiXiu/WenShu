package com.example.rag.prompt;

import com.example.rag.config.AppConfiguration;

import java.util.*;

/**
 * 全局 Prompt 注册表 — 所有提示词的单一事实来源
 * 从 AppConfiguration.prompts 读取模板，提供静态访问和写入方法
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
     * 获取 prompt 分类（core / summary / tool / agent）
     */
    public static String getCategory(String key) {
        if (prompts == null) return "core";
        var entry = prompts.get(key);
        return entry != null && entry.category != null ? entry.category : "core";
    }

    /**
     * 添加或更新一个 prompt 条目
     */
    public static void putTemplate(String key, String template, String description, String category) {
        if (prompts == null) return;
        prompts.put(key, new AppConfiguration.PromptEntry(template, description, category));
    }

    /**
     * 移除一个 prompt 条目
     */
    public static void removeTemplate(String key) {
        if (prompts == null) return;
        prompts.remove(key);
    }

    /**
     * 获取指定分类下的所有 prompt key
     */
    public static List<String> getKeysByCategory(String category) {
        if (prompts == null) return List.of();
        List<String> keys = new ArrayList<>();
        for (var entry : prompts.entrySet()) {
            String cat = entry.getValue() != null && entry.getValue().category != null
                    ? entry.getValue().category : "core";
            if (category.equals(cat)) keys.add(entry.getKey());
        }
        return keys;
    }

    /**
     * 获取所有 prompt key
     */
    public static Set<String> getAllKeys() {
        if (prompts == null) return Set.of();
        return Collections.unmodifiableSet(prompts.keySet());
    }

    /**
     * 持久化当前 prompts 到 config.json 并重新加载内存注册表
     */
    public static void persist(AppConfiguration config) {
        config.setPrompts(prompts);
        config.save();
        init(config);
    }
}
