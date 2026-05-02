package com.example.rag.tools;

import com.example.rag.config.AppConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 可配置的网页搜索工具
 * 支持 Tavily / SerpAPI / 自定义搜索 API
 */
public class WebSearcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final int maxResults;

    public WebSearcher(AppConfiguration.WebSearchConfig config) {
        this.provider = config != null ? config.provider : "none";
        this.apiKey = config != null ? config.apiKey : "";
        this.baseUrl = config != null ? config.baseUrl : "";
        this.maxResults = config != null ? config.maxResults : 5;
    }

    public boolean isConfigured() {
        if ("none".equals(provider)) return false;
        return apiKey != null && !apiKey.isBlank();
    }

    public String search(String query) {
        if (!isConfigured()) return null;
        try {
            return switch (provider) {
                case "tavily" -> searchTavily(query);
                case "serpapi" -> searchSerpApi(query);
                case "custom" -> searchCustom(query);
                default -> null;
            };
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    private String searchTavily(String query) throws Exception {
        String jsonBody = MAPPER.writeValueAsString(java.util.Map.of(
                "query", query,
                "max_results", maxResults,
                "api_key", apiKey
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return "未找到相关结果";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JsonNode item : results) {
            if (count >= maxResults) break;
            sb.append("[").append(count + 1).append("] ");
            if (item.has("title")) sb.append(item.get("title").asText());
            sb.append("\n");
            if (item.has("content")) sb.append(item.get("content").asText());
            sb.append("\n\n");
            count++;
        }
        return sb.toString().trim();
    }

    private String searchSerpApi(String query) throws Exception {
        String url = "https://serpapi.com/search.json?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                + "&api_key=" + apiKey + "&num=" + maxResults;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.get("organic_results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return "未找到相关结果";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JsonNode item : results) {
            if (count >= maxResults) break;
            sb.append("[").append(count + 1).append("] ");
            if (item.has("title")) sb.append(item.get("title").asText());
            sb.append("\n");
            if (item.has("snippet")) sb.append(item.get("snippet").asText());
            sb.append("\n\n");
            count++;
        }
        return sb.toString().trim();
    }

    private String searchCustom(String query) throws Exception {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "自定义搜索 API 地址未配置";
        }
        String url = baseUrl.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
                .replace("{apiKey}", apiKey != null ? apiKey : "")
                .replace("{maxResults}", String.valueOf(maxResults));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
