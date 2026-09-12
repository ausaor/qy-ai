package com.qy.service.impl;

import com.qy.rag.reader.MultiFormatDocumentReader;
import com.qy.rag.retrieval.Bm25DocumentRetriever;
import com.qy.rag.splitter.SemanticDocumentSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DocumentServiceImpl 单元测试
 * 验证文档处理流水线：读取 → 分块 → 向量库写入 → BM25 索引
 * 所有外部依赖均已 Mock，不依赖 Redis 或外部 API
 */
@DisplayName("文档处理服务")
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private MultiFormatDocumentReader documentReader;

    @Mock
    private SemanticDocumentSplitter documentSplitter;

    @Mock
    private Bm25DocumentRetriever bm25DocumentRetriever;

    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(
                vectorStore, documentReader, documentSplitter, bm25DocumentRetriever);
    }

    // ==================== 同步写入流程验证 ====================

    @Test
    @DisplayName("同步写入应完成 读取→分块→向量库→BM25 全流程")
    void syncWriteShouldCompleteFullPipeline() throws IOException {
        // 准备：创建临时 Markdown 文件
        Path tempFile = Files.createTempFile("test-readme-", ".md");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, """
                # QyAI 项目
                                
                这是一个基于 Spring Boot 的 AI 聊天服务。
                支持 DeepSeek 和 Qwen 模型。
                提供 Flux 和 SSE 流式接口。
                """);

        // Mock：读取器返回 1 个文档
        Document rawDoc = new Document("原始文档内容");
        when(documentReader.read(any(), anyString(), anyString()))
                .thenReturn(List.of(rawDoc));

        // Mock：分块器返回 3 个 chunk
        Document chunk1 = new Document("chunk1", Map.of("source_file", "test-readme.md", "chunk_index", "0"));
        Document chunk2 = new Document("chunk2", Map.of("source_file", "test-readme.md", "chunk_index", "1"));
        Document chunk3 = new Document("chunk3", Map.of("source_file", "test-readme.md", "chunk_index", "2"));
        when(documentSplitter.split(any()))
                .thenReturn(List.of(chunk1, chunk2, chunk3));

        // 执行同步写入
        int chunkCount = service.writeToVectorStore(
                new FileSystemResource(tempFile), "test-readme.md");

        // 验证返回值
        assertEquals(3, chunkCount, "应返回 3 个 chunk");

        // 验证调用顺序：1.读取 → 2.分块 → 3.向量库写入 → 4.BM25 索引
        verify(documentReader).read(any(), eq("test-readme.md"), eq("text/markdown"));
        verify(documentSplitter).split(List.of(rawDoc));
        verify(vectorStore, atLeastOnce()).add(any());
        verify(bm25DocumentRetriever).addDocuments(List.of(chunk1, chunk2, chunk3));
    }

    @Test
    @DisplayName("分批写入：超过 10 个 chunk 应分批次写入向量库")
    void shouldWriteInBatchesWhenChunksExceedBatchSize() {
        // Mock：15 个 chunk（超过 batchSize=10）
        List<Document> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            chunks.add(new Document("chunk" + i,
                    Map.of("source_file", "big.md", "chunk_index", String.valueOf(i))));
        }

        when(documentReader.read(any(), anyString(), anyString()))
                .thenReturn(List.of(new Document("big content")));
        when(documentSplitter.split(any())).thenReturn(chunks);

        service.writeToVectorStore(
                mock(FileSystemResource.class), "big.md");

        // 验证向量库被调用至少 2 次（15 个 chunk，每批 10 个 → 2 批）
        verify(vectorStore, atLeast(2)).add(any());
    }

    @Test
    @DisplayName("空文档应正确返回 0")
    void emptyDocumentShouldReturnZero() {
        when(documentReader.read(any(), anyString(), anyString()))
                .thenReturn(List.of());
        when(documentSplitter.split(any())).thenReturn(List.of());

        int count = service.writeToVectorStore(
                mock(FileSystemResource.class), "empty.md");

        assertEquals(0, count, "空文档应返回 0");
        verify(vectorStore, never()).add(any());
        verify(bm25DocumentRetriever).addDocuments(List.of());
    }

    @Test
    @DisplayName("MIME 类型识别：.md 文件应为 text/markdown")
    void shouldDetectMarkdownMimeType() throws Exception {
        service.writeToVectorStore(mock(FileSystemResource.class), "README.md");

        // 验证读取器收到了正确的 MIME 类型
        verify(documentReader).read(any(), anyString(), eq("text/markdown"));
    }

    @Test
    @DisplayName("MIME 类型识别：.txt 文件应为 text/plain")
    void shouldDetectTextMimeType() throws Exception {
        service.writeToVectorStore(mock(FileSystemResource.class), "notes.txt");

        verify(documentReader).read(any(), anyString(), eq("text/plain"));
    }

    @Test
    @DisplayName("MIME 类型识别：.pdf 文件应为 application/pdf")
    void shouldDetectPdfMimeType() throws Exception {
        service.writeToVectorStore(mock(FileSystemResource.class), "doc.pdf");

        verify(documentReader).read(any(), anyString(), eq("application/pdf"));
    }

    @Test
    @DisplayName("未知扩展名应返回 octet-stream")
    void unknownExtensionShouldReturnOctetStream() throws Exception {
        service.writeToVectorStore(mock(FileSystemResource.class), "data.xyz");

        verify(documentReader).read(any(), anyString(), eq("application/octet-stream"));
    }

    // ==================== 文档统计 ====================

    @Test
    @DisplayName("getDocumentStats 应返回 BM25 索引统计")
    void shouldReturnBm25Stats() {
        when(bm25DocumentRetriever.getDocumentCount()).thenReturn(42);
        when(bm25DocumentRetriever.getSourceFileCount()).thenReturn(3L);

        Map<String, Object> stats = service.getDocumentStats();

        assertEquals(42, stats.get("documentCount"));
        assertEquals(3L, stats.get("sourceFileCount"));
    }

    @Test
    @DisplayName("空索引统计应返回 0")
    void emptyIndexShouldReturnZeroStats() {
        when(bm25DocumentRetriever.getDocumentCount()).thenReturn(0);
        when(bm25DocumentRetriever.getSourceFileCount()).thenReturn(0L);

        Map<String, Object> stats = service.getDocumentStats();

        assertEquals(0, stats.get("documentCount"));
        assertEquals(0L, stats.get("sourceFileCount"));
    }

    // ==================== 异步写入验证 ====================

    @Test
    @DisplayName("异步写入内部应调用同步写入逻辑")
    void asyncWriteShouldDelegateToSyncWrite() throws IOException {
        Path tempFile = Files.createTempFile("async-test-", ".md");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "# Async Test");

        Document rawDoc = new Document("async content");
        when(documentReader.read(any(), anyString(), anyString()))
                .thenReturn(List.of(rawDoc));

        Document chunk = new Document("async-chunk");
        when(documentSplitter.split(any())).thenReturn(List.of(chunk));

        // 异步方法在测试中会同步执行（默认 SimpleAsyncTaskExecutor）
        service.writeToVectorStoreAsync(
                new FileSystemResource(tempFile), "async-test.md");

        // 验证内部调用了同步写入
        verify(documentSplitter).split(List.of(rawDoc));
        verify(bm25DocumentRetriever).addDocuments(List.of(chunk));

        // 验证临时文件被清理
        assertFalse(Files.exists(tempFile), "异步完成后临时文件应被清理");
    }

    // ==================== 数据流完整性 ====================

    @Test
    @DisplayName("整个处理流水线：数据从读取器到向量库和 BM25 的完整性")
    void fullPipelineDataIntegrity() {
        // 模拟完整的 README.md 内容
        Document rawDoc = new Document("""
                QyAI 是一个基于 Spring Boot 的 AI 聊天服务项目。
                支持多种 AI 模型和流式消息回复。
                技术架构包含 Spring Boot、Redis、JWT。""");

        when(documentReader.read(any(), anyString(), anyString()))
                .thenReturn(List.of(rawDoc));

        List<Document> chunks = List.of(
                new Document("chunk-0",
                        Map.of("source_file", "README.md", "chunk_index", "0")),
                new Document("chunk-1",
                        Map.of("source_file", "README.md", "chunk_index", "1"))
        );
        when(documentSplitter.split(List.of(rawDoc))).thenReturn(chunks);

        int result = service.writeToVectorStore(
                mock(FileSystemResource.class), "README.md");

        assertEquals(2, result);

        // 验证所有 chunk 都被写入向量库和 BM25
        ArgumentCaptor<List<Document>> vectorStoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(vectorStoreCaptor.capture());
        verify(bm25DocumentRetriever).addDocuments(chunks);

        // 验证向量库收到的数据包含所有 chunk
        long totalVectorChunks = vectorStoreCaptor.getAllValues().stream()
                .mapToLong(List::size)
                .sum();
        assertEquals(2, totalVectorChunks,
                "所有 2 个 chunk 都应写入向量库");
    }
}