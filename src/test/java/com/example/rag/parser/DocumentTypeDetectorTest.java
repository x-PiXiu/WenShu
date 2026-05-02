package com.example.rag.parser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentTypeDetector 结构分析测试
 * 验证重写后的检测逻辑对不同文档类型的判断准确性
 */
class DocumentTypeDetectorTest {

    @Test
    void emptyContent_shouldReturnGeneral() {
        var result = DocumentTypeDetector.detect("test.txt", "");
        assertEquals("GENERAL", result.type());
        assertTrue(result.confidence() < 0.5);
    }

    @Test
    void blankContent_shouldReturnGeneral() {
        var result = DocumentTypeDetector.detect("test.txt", "   \n  \n  ");
        assertEquals("GENERAL", result.type());
    }

    @Test
    void technicalDocWithCodeBlocks_shouldReturnTechnical() {
        String content = """
            # Spring Boot 快速入门

            ## 1. 环境搭建

            首先创建一个 Spring Boot 项目：

            ```java
            @SpringBootApplication
            public class MyApp {
                public static void main(String[] args) {
                    SpringApplication.run(MyApp.class, args);
                }
            }
            ```

            ## 2. 配置文件

            在 application.yml 中配置：

            ```yaml
            server:
              port: 8080
            spring:
              datasource:
                url: jdbc:mysql://localhost:3306/mydb
            ```

            ## 3. REST 控制器

            ```java
            @RestController
            @RequestMapping("/api")
            public class UserController {
                @GetMapping("/users")
                public List<User> list() {
                    return userService.findAll();
                }
            }
            ```
            """;

        var result = DocumentTypeDetector.detect("spring-boot-guide.md", content);
        assertEquals("TECHNICAL", result.type());
        assertTrue(result.confidence() > 0.5, "Confidence should be > 0.5, got " + result.confidence());
    }

    @Test
    void docxParsedTechnicalContent_shouldReturnTechnical() {
        // 模拟 Tika 解析 DOCX 后的纯文本 —— 没有代码块标记，但保留代码关键字
        String content = """
            第2篇 开发环境搭建 5分钟跑通第一个AI对话

            1. 环境要求
            Java 17+
            Maven 3.8+
            OpenAI API Key

            2. 创建项目

            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;

            @SpringBootApplication
            public class RagApplication {
                public static void main(String[] args) {
                    SpringApplication.run(RagApplication.class, args);
                }
            }

            3. 配置文件

            在 pom.xml 中添加依赖：
            import dev.langchain4j;
            using dev.langchain4j.openai;

            4. 向量存储配置

            public class VectorStoreConfig {
                private String collectionName;
                private int dimension;
            }

            5. 运行测试

            在终端执行：
            $ mvn clean package
            $ java -jar target/app.jar

            如果看到以下输出说明启动成功：
            > Server started on port 7080

            6. 测试对话

            console.log("Hello AI!")
            System.out.println("Response: " + response);
            """;

        var result = DocumentTypeDetector.detect("开发环境搭建.docx", content);
        assertEquals("TECHNICAL", result.type(),
            "DOCX parsed technical content should be detected as TECHNICAL, got " + result.type());
    }

    @Test
    void faqDocument_shouldReturnFaq() {
        String content = """
            常见问题解答

            Q: 如何安装 JDK 17？
            A: 从 Oracle 官网下载安装包，或者使用 sdkman 安装：
            sdk install java 17.0.8-tem

            Q: Maven 构建失败怎么办？
            A: 检查网络连接和 settings.xml 配置，确保能访问 Maven 中央仓库。

            Q: 如何配置 OpenAI API Key？
            A: 在 application.yml 中设置 openai.api.key，或通过环境变量 OPENAI_API_KEY 传入。

            Q: 向量数据库选哪个好？
            A: 推荐开发环境用 Chroma，生产环境用 Milvus。

            Q: 为什么检索结果不准确？
            A: 检查分块大小和 overlap 设置，确保 embedding 模型与查询语言匹配。

            Q: 支持哪些文档格式？
            A: 目前支持 PDF、DOCX、MD、TXT 格式。
            """;

        var result = DocumentTypeDetector.detect("faq.md", content);
        assertEquals("FAQ", result.type());
        assertTrue(result.confidence() > 0.5);
    }

    @Test
    void logDocument_shouldReturnLog() {
        String content = """
            2024-12-15 10:23:45 INFO  [main] Application started on port 7080
            2024-12-15 10:23:46 INFO  [main] Connecting to vector store...
            2024-12-15 10:23:47 DEBUG [main] Vector store connection established
            2024-12-15 10:24:01 INFO  [http] POST /api/documents/upload
            2024-12-15 10:24:02 WARN  [http] File size exceeds recommended limit: 15MB
            2024-12-15 10:24:05 ERROR [http] Failed to parse document: corrupted PDF
            2024-12-15 10:24:05 ERROR [http] Stacktrace: com.example.ParseException
            2024-12-15 10:24:10 INFO  [http] POST /api/chat
            2024-12-15 10:24:11 DEBUG [chat] Query: "如何配置向量数据库"
            2024-12-15 10:24:12 INFO  [chat] Retrieved 5 segments
            """;

        var result = DocumentTypeDetector.detect("app.log", content);
        assertEquals("LOG", result.type());
        assertTrue(result.confidence() > 0.7);
    }

    @Test
    void articleWithHeadings_shouldReturnArticle() {
        String content = """
            深入理解 RAG 检索增强生成技术

            RAG（Retrieval-Augmented Generation）是一种结合信息检索与大语言模型生成能力的技术架构。
            它通过在生成回答前先检索相关文档，有效缓解了大模型的幻觉问题，提升了回答的准确性和可靠性。

            一、RAG 的核心原理

            RAG 系统的核心流程包括三个阶段：索引、检索和生成。在索引阶段，文档被分块并向量化存储；
            在检索阶段，系统根据用户查询检索最相关的文档片段；在生成阶段，检索结果作为上下文输入 LLM。

            二、向量检索的关键技术

            向量检索是 RAG 系统的基础。文本通过 Embedding 模型转换为高维向量，然后使用近似最近邻
            算法（如 HNSW、IVF）在向量数据库中快速找到语义相似的文档片段。主流向量数据库包括
            Chroma、Milvus、Pinecone 等。

            三、混合检索策略

            单纯的向量检索在某些场景下存在局限性。混合检索结合了向量检索和关键词检索（如 BM25），
            通过倒数秩融合（RRF）算法合并两种检索结果，显著提升了检索的准确率和召回率。

            四、总结

            RAG 技术为企业级知识问答系统提供了可靠的解决方案。通过合理的分块策略、混合检索和
            生成优化，可以构建高质量的智能问答系统。
            """;

        var result = DocumentTypeDetector.detect("rag-intro.txt", content);
        assertEquals("ARTICLE", result.type());
    }

    @Test
    void generalShortText_shouldReturnGeneral() {
        String content = """
            会议记录 2024-12-15

            参会人员：张三、李四、王五
            会议主题：下季度工作计划

            讨论要点：
            1. 项目进度回顾
            2. 新需求评审
            3. 团队资源分配

            下一步：
            - 完成需求文档
            - 安排技术评审
            """;

        var result = DocumentTypeDetector.detect("meeting.txt", content);
        assertEquals("GENERAL", result.type());
    }

    @Test
    void pythonCodeDoc_shouldReturnTechnical() {
        String content = """
            # Python 数据处理教程

            ## 使用 Pandas 处理 CSV

            import pandas as pd
            from pathlib import Path

            def load_data(filepath):
                df = pd.read_csv(filepath)
                return df

            class DataProcessor:
                def __init__(self, config):
                    self.config = config

                def process(self):
                    for item in self.data:
                        print(item)

            ## 使用 requests 获取 API 数据

            import requests

            response = requests.get("https://api.example.com/data")
            data = response.json()
            print(f"Got {len(data)} records")

            ## 虚拟环境管理

            $ python -m venv .venv
            $ source .venv/bin/activate
            $ pip install pandas requests
            """;

        var result = DocumentTypeDetector.detect("python-tutorial.md", content);
        assertEquals("TECHNICAL", result.type());
    }

    @Test
    void goCodeDoc_shouldReturnTechnical() {
        String content = """
            # Go 并发编程指南

            ## Goroutine 基础

            package main

            import (
                "fmt"
                "sync"
            )

            func main() {
                var wg sync.WaitGroup
                for i := 0; i < 10; i++ {
                    wg.Add(1)
                    go func(id int) {
                        defer wg.Done()
                        fmt.Println("Worker", id)
                    }(i)
                }
                wg.Wait()
            }

            ## Channel 通信

            func producer(ch chan<- int) {
                for i := 0; i < 100; i++ {
                    ch <- i
                }
                close(ch)
            }
            """;

        var result = DocumentTypeDetector.detect("go-concurrency.md", content);
        assertEquals("TECHNICAL", result.type());
    }
}
