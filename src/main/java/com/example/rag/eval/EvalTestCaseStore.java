package com.example.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 评估测试用例存储（JSON 文件）
 */
public class EvalTestCaseStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<TestCase>> LIST_TYPE = new TypeReference<>() {};

    private final Path storeFile;
    private List<TestCase> cases;

    public EvalTestCaseStore(String evalDir) {
        this.storeFile = Path.of(evalDir).resolve("test-cases.json");
        this.cases = load();
    }

    public List<TestCase> getAll() {
        return Collections.unmodifiableList(cases);
    }

    public void add(TestCase tc) {
        cases.removeIf(t -> t.id().equals(tc.id()));
        cases.add(tc);
        save();
    }

    public boolean remove(String id) {
        boolean removed = cases.removeIf(t -> t.id().equals(id));
        if (removed) save();
        return removed;
    }

    private List<TestCase> load() {
        if (!Files.exists(storeFile)) return new ArrayList<>();
        try {
            return new ArrayList<>(MAPPER.readValue(storeFile.toFile(), LIST_TYPE));
        } catch (IOException e) {
            System.err.println("[WARN] Failed to load eval test cases: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void save() {
        try {
            Files.createDirectories(storeFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(storeFile.toFile(), cases);
        } catch (IOException e) {
            System.err.println("[WARN] Failed to save eval test cases: " + e.getMessage());
        }
    }

    /**
     * 评估测试用例：问题 + 期望命中的文档来源
     */
    public record TestCase(
            String id,
            String question,
            Set<String> relevantSources,
            String category
    ) {}
}
