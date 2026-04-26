package com.example.rag.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档元数据持久化：记录每个文档的类型，用于重索引时还原分块策略
 * 元数据文件: knowledge/.doc-meta.json
 */
public class DocumentMetaStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, DocMeta>> META_TYPE = new TypeReference<>() {};

    private final Path metaFile;
    private Map<String, DocMeta> meta;

    public DocumentMetaStore(String knowledgeDir) {
        this.metaFile = Path.of(knowledgeDir).resolve(".doc-meta.json");
        this.meta = load();
    }

    /**
     * 记录一个文档的类型名和分块参数
     */
    public void put(String filename, String typeName, int chunkSize, int chunkOverlap) {
        meta.put(filename, new DocMeta(typeName, chunkSize, chunkOverlap));
        save();
    }

    /**
     * 获取文档类型名，未记录则返回 "GENERAL"
     */
    public String getTypeName(String filename) {
        DocMeta m = meta.get(filename);
        return m != null ? m.type : "GENERAL";
    }

    /**
     * 获取所有元数据
     */
    public Map<String, DocMeta> getAll() {
        return Collections.unmodifiableMap(meta);
    }

    /**
     * 删除文档元数据
     */
    public void remove(String filename) {
        meta.remove(filename);
        save();
    }

    private Map<String, DocMeta> load() {
        if (!Files.exists(metaFile)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(metaFile.toFile(), META_TYPE);
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private void save() {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), meta);
        } catch (IOException e) {
            System.err.println("[WARN] Failed to save document metadata: " + e.getMessage());
        }
    }

    public record DocMeta(String type, int chunkSize, int chunkOverlap) {}
}
