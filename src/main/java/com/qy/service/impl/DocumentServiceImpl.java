package com.qy.service.impl;

import com.qy.rag.reader.MultiFormatDocumentReader;
import com.qy.rag.retrieval.Bm25DocumentRetriever;
import com.qy.rag.splitter.SemanticDocumentSplitter;
import com.qy.service.IDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements IDocumentService {

    private final VectorStore vectorStore;

    private final MultiFormatDocumentReader documentReader;

    private final SemanticDocumentSplitter documentSplitter;

    private final Bm25DocumentRetriever bm25DocumentRetriever;

    @Async
    @Override
    public void writeToVectorStoreAsync(Resource resource, String originalFilename) {
        try {
            log.info("异步文档处理开始: {}", originalFilename);
            int chunkCount = writeToVectorStore(resource, originalFilename);
            log.info("异步文档处理完成: {}, 共 {} 个 chunk 已写入向量库和 BM25 索引",
                    originalFilename, chunkCount);
        } catch (Exception e) {
            // 异步任务异常必须记录，否则上传接口返回成功但文档从未写入，用户无法感知
            log.error("异步文档处理失败: {}, 原因: {}", originalFilename, e.getMessage(), e);
        } finally {
            cleanupTempFile(resource);
        }
    }

    @Override
    public int writeToVectorStore(Resource resource, String originalFilename) {
        String contentType = detectContentType(originalFilename);
        log.info("开始处理文档: {}, MIME: {}", originalFilename, contentType);

        // 1.使用多格式读取器解析文档
        List<Document> documents = documentReader.read(resource, originalFilename, contentType);
        log.info("读取到 {} 个文档片段", documents.size());

        // 2.语义分块
        List<Document> chunks = documentSplitter.split(documents);
        log.info("语义分块完成: {} 个 chunk", chunks.size());

        // 3.分批写入向量库（DashScope embedding API 限制每批最多 10 个文档）
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<Document> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            vectorStore.add(batch);
            log.info("向量库写入批次 {}/{}，{} 个 chunk",
                    (i / batchSize + 1), (chunks.size() + batchSize - 1) / batchSize, batch.size());
        }

        // 4.同步写入 BM25 索引
        bm25DocumentRetriever.addDocuments(chunks);
        log.info("文档处理完成: {} → {} 个 chunk 已写入向量库和 BM25 索引",
                originalFilename, chunks.size());
        return chunks.size();
    }

    @Override
    public Map<String, Object> getDocumentStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("documentCount", bm25DocumentRetriever.getDocumentCount());
        stats.put("sourceFileCount", bm25DocumentRetriever.getSourceFileCount());
        return stats;
    }

    /**
     * 异步处理完成后删除上传转存的本地临时文件
     */
    private void cleanupTempFile(Resource resource) {
        if (resource instanceof FileSystemResource fileResource) {
            try {
                Files.deleteIfExists(fileResource.getFile().toPath());
                log.info("成功删除上传临时文件: {}", fileResource.getPath());
            } catch (IOException e) {
                log.warn("删除上传临时文件失败: {}", fileResource.getPath(), e);
            }
        }
    }

    private String detectContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".rtf")) return "application/rtf";
        return "application/octet-stream";
    }
}
