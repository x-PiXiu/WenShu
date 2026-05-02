package com.example.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 评估结果历史存储（JSON 文件）
 */
public class EvalResultStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<EvalResult>> LIST_TYPE = new TypeReference<>() {};

    private final Path storeFile;
    private List<EvalResult> history;

    public EvalResultStore(String evalDir) {
        this.storeFile = Path.of(evalDir).resolve("results.json");
        this.history = load();
    }

    public void save(EvalResult result) {
        history.add(result);
        write();
    }

    public List<EvalResult> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public EvalResult getLatest() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public void clear() {
        history.clear();
        write();
    }

    private List<EvalResult> load() {
        if (!Files.exists(storeFile)) return new ArrayList<>();
        try {
            return new ArrayList<>(MAPPER.readValue(storeFile.toFile(), LIST_TYPE));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void write() {
        try {
            Files.createDirectories(storeFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(storeFile.toFile(), history);
        } catch (IOException e) {
            System.err.println("[WARN] Failed to save eval results: " + e.getMessage());
        }
    }

    /**
     * 一次评估的完整结果快照
     */
    public record EvalResult(
            String timestamp,
            int totalCases,
            int topK,
            double hitRate,
            double avgRecallAtK,
            double avgPrecisionAtK,
            double mrr,
            List<CaseSummary> caseResults
    ) {}

    public record CaseSummary(
            String id,
            String question,
            String category,
            boolean hit,
            double topScore
    ) {}
}
