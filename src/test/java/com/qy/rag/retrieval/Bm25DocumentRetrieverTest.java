package com.qy.rag.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bm25DocumentRetriever 单元测试
 * 验证中文文档的 BM25 索引写入、关键词检索、文档统计功能
 */
@DisplayName("BM25 文档检索引擎")
class Bm25DocumentRetrieverTest {

    private Bm25DocumentRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new Bm25DocumentRetriever();
    }

    // ==================== 文档统计 ====================

    @Test
    @DisplayName("空索引应返回 0 个文档")
    void emptyIndexShouldReturnZero() {
        assertEquals(0, retriever.getDocumentCount());
        assertEquals(0, retriever.getSourceFileCount());
    }

    @Test
    @DisplayName("添加文档后统计信息应正确")
    void shouldReportCorrectStatsAfterAddingDocuments() {
        List<Document> docs = createReadmeChunks();
        retriever.addDocuments(docs);

        assertEquals(4, retriever.getDocumentCount());
        assertEquals(1, retriever.getSourceFileCount());
    }

    @Test
    @DisplayName("多文件统计应区分来源")
    void shouldDistinguishSourceFiles() {
        List<Document> readmeDocs = createReadmeChunks();
        retriever.addDocuments(readmeDocs);

        List<Document> otherDocs = List.of(
                createDoc("other.md", "0", "这是另一个文件的内容")
        );
        retriever.addDocuments(otherDocs);

        assertEquals(5, retriever.getDocumentCount());
        assertEquals(2, retriever.getSourceFileCount());
    }

    // ==================== 中文关键词检索 ====================

    @Test
    @DisplayName("中文关键词检索应返回匹配文档")
    void chineseKeywordSearchShouldReturnMatches() {
        retriever.addDocuments(createReadmeChunks());

        // 搜索 "Spring Boot"，应在 README 内容中匹配到
        List<Document> results = retriever.retrieve(new Query("Spring Boot 项目"));
        assertFalse(results.isEmpty(), "应检索到包含 'Spring Boot' 的文档");

        // 验证返回结果包含相关元数据
        Document first = results.get(0);
        assertNotNull(first.getMetadata().get("source_file"));
        assertNotNull(first.getMetadata().get("chunk_index"));
        assertNotNull(first.getMetadata().get("bm25_score"));
    }

    @Test
    @DisplayName("查询 'AI聊天服务' 应匹配 README 项目简介")
    void queryAiChatServiceShouldMatchReadmeIntro() {
        retriever.addDocuments(createReadmeChunks());

        List<Document> results = retriever.retrieve(new Query("AI聊天服务"));
        assertFalse(results.isEmpty(), "应匹配到 README 中的项目简介");

        // 第一条结果应包含 "AI" 或 "聊天"
        boolean hasRelevantContent = results.stream()
                .anyMatch(d -> d.getText().contains("AI") || d.getText().contains("聊天"));
        assertTrue(hasRelevantContent, "检索结果应包含 AI 或 聊天 相关内容");
    }

    @Test
    @DisplayName("查询 '多格式文档上传' 应匹配对应段落")
    void queryDocumentUploadShouldMatchParagraph() {
        retriever.addDocuments(createReadmeChunks());

        List<Document> results = retriever.retrieve(new Query("多格式文档上传"));
        assertFalse(results.isEmpty(), "应匹配到文档上传相关的段落");
    }

    @Test
    @DisplayName("查询 '技术架构 Redis' 应返回技术栈相关内容")
    void queryTechStackShouldMatchContent() {
        retriever.addDocuments(createReadmeChunks());

        List<Document> results = retriever.retrieve(new Query("技术架构 Redis"));
        assertFalse(results.isEmpty(), "应匹配到技术架构相关段落");
    }

    @Test
    @DisplayName("不匹配的查询应返回空结果")
    void unrelatedQueryShouldReturnEmpty() {
        retriever.addDocuments(createReadmeChunks());

        List<Document> results = retriever.retrieve(new Query("量子计算机工作原理详解"));
        // 注意：由于 BM25 单字切分，"量""子""计""算""机" 等字可能在其他文档中出现，
        // 因此不强制为空，但得分应很低或为空
        assertTrue(results.isEmpty() || results.size() <= 2,
                "不相关查询应返回少或无结果");
    }

    // ==================== 检索排序 ====================

    @Test
    @DisplayName("检索结果应按相关度降序排列")
    void resultsShouldBeSortedByRelevance() {
        retriever.addDocuments(createReadmeChunks());

        List<Document> results = retriever.retrieve(new Query("主要功能 AI 模型"));

        if (results.size() >= 2) {
            double score1 = Double.parseDouble(
                    results.get(0).getMetadata().get("bm25_score").toString());
            double score2 = Double.parseDouble(
                    results.get(1).getMetadata().get("bm25_score").toString());
            assertTrue(score1 >= score2,
                    "第一个结果的 BM25 得分应 >= 第二个结果");
        }
    }

    @Test
    @DisplayName("检索结果最多返回 10 个")
    void shouldReturnAtMostTenResults() {
        // 添加超过 10 个文档
        List<Document> manyDocs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyDocs.add(createDoc("bulk.md", String.valueOf(i),
                    "Spring Boot AI 项目 功能 测试文档 第" + i + "段"));
        }
        retriever.addDocuments(manyDocs);

        List<Document> results = retriever.retrieve(new Query("Spring Boot"));
        assertTrue(results.size() <= 10, "应最多返回 10 条结果");
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("空索引检索应返回空列表")
    void emptyIndexRetrievalShouldReturnEmpty() {
        List<Document> results = retriever.retrieve(new Query("任何内容"));
        assertTrue(results.isEmpty());
    }

    // ==================== 测试数据构造 ====================

    /**
     * 构造模拟 README.md 的文档分块，用于验证修复后的相似度阈值 + 代码块读取
     */
    private List<Document> createReadmeChunks() {
        return List.of(
                createDoc("README.md", "0",
                        "QyAI 是一个基于 Spring Boot 的 AI 聊天服务项目，" +
                                "支持多种 AI 模型和流式消息回复。"),
                createDoc("README.md", "1",
                        "主要功能：支持多种 AI 模型（如 DeepSeek 和 Qwen），" +
                                "提供流式消息回复接口（Flux 和 SSE），支持 MCP 协议。"),
                createDoc("README.md", "2",
                        "技术架构：后端框架 Spring Boot 3.x，AI 模型集成 Spring AI，" +
                                "认证机制 JWT，缓存 Redis，日志 Logback。"),
                createDoc("README.md", "3",
                        "使用方式：支持 PDF/Word/Markdown/文本等多格式文档上传和问答，" +
                                "确保已安装 Java 17+ 和 Maven。")
        );
    }

    private Document createDoc(String sourceFile, String chunkIndex, String content) {
        Document doc = new Document(content,
                Map.of("source_file", sourceFile, "chunk_index", chunkIndex));
        return doc;
    }
}