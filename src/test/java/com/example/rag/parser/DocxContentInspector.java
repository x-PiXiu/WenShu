package com.example.rag.parser;

import dev.langchain4j.data.document.Document;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * 调试工具：输出 DOCX 实际解析内容 + DocumentTypeDetector 的检测结果
 */
public class DocxContentInspector {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("knowledge/第2篇-开发环境搭建：5分钟跑通第一个AI对话.docx");
        Document doc = AutoDocumentParser.load(file);
        String text = doc.text();

        // Write parsed content
        Files.writeString(Path.of("target/docx-content.txt"), text, StandardCharsets.UTF_8);

        // Detection result using the REAL detector
        var result = DocumentTypeDetector.detect(file.getFileName().toString(), text);

        String report = String.format("""
            === DOCX DETECTION REPORT ===
            File: %s
            Parsed chars: %d
            Detection: %s
            Confidence: %.2f
            Method: %s
            """,
            file.getFileName(), text.length(),
            result.type(), result.confidence(), result.method());

        Files.writeString(Path.of("target/docx-detection.txt"), report, StandardCharsets.UTF_8);
        System.out.println("Written to target/docx-detection.txt");
    }
}
