package com.example.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 根据文件后缀自动选择解析器
 */
public class AutoDocumentParser {

    public static DocumentParser forFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return new ApachePdfBoxDocumentParser();
        } else if (filename.endsWith(".docx")) {
            return new ApacheTikaDocumentParser();
        } else if (filename.endsWith(".md")) {
            return new TextDocumentParser();
        } else {
            return new TextDocumentParser();
        }
    }

    /**
     * 加载单个文件
     */
    public static Document load(Path path) {
        DocumentParser parser = forFile(path);
        try (InputStream inputStream = Files.newInputStream(path)) {
            Document document = parser.parse(inputStream);
            // 将文件名写入 metadata，供下游溯源使用
            document.metadata().put("source", path.getFileName().toString());
            return document;
        } catch (IOException e) {
            throw new RuntimeException("无法加载文档: " + path, e);
        }
    }

    /**
     * 批量加载目录（只加载知识库目录下的文档）
     */
    public static List<Document> loadDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在：" + dir);
        }

        List<Document> allDocs = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> Files.isRegularFile(p))
                 .filter(p -> !p.getFileName().toString().startsWith("."))
                 .filter(p -> {
                     String name = p.getFileName().toString().toLowerCase();
                     return name.endsWith(".pdf") || name.endsWith(".docx")
                         || name.endsWith(".md") || name.endsWith(".txt");
                 })
                 .forEach(p -> {
                     try {
                         Document doc = load(p);
                         System.out.println("  加载: " + p.getFileName()
                             + " (" + doc.text().length() + " 字)");
                         allDocs.add(doc);
                     } catch (Exception e) {
                         System.out.println("  跳过: " + p.getFileName()
                             + "（解析失败: " + e.getMessage() + "）");
                     }
                 });
        } catch (IOException e) {
            throw new RuntimeException("无法遍历目录: " + dir, e);
        }

        return allDocs;
    }
}
