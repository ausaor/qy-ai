package com.qy.rag.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 混合检索器
 * 并行执行向量检索和 BM25 关键词检索，合并结果
 * 通过 AdvancedRagConfiguration 创建 bean
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private final VectorStoreDocumentRetriever vectorRetriever;
    private final Bm25DocumentRetriever bm25Retriever;

    public HybridDocumentRetriever(VectorStoreDocumentRetriever vectorRetriever,
                                   Bm25DocumentRetriever bm25Retriever) {
        this.vectorRetriever = vectorRetriever;
        this.bm25Retriever = bm25Retriever;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 并行执行两种检索
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return vectorRetriever.retrieve(query);
            } catch (Exception e) {
                log.warn("向量检索失败: {}", e.getMessage());
                return Collections.<Document>emptyList();
            }
        });

        CompletableFuture<List<Document>> bm25Future = CompletableFuture.supplyAsync(() -> {
            try {
                return bm25Retriever.retrieve(query);
            } catch (Exception e) {
                log.warn("BM25 检索失败: {}", e.getMessage());
                return Collections.<Document>emptyList();
            }
        });

        // 等待两个检索完成
        CompletableFuture.allOf(vectorFuture, bm25Future).join();

        List<Document> vectorResults = vectorFuture.getNow(Collections.emptyList());
        List<Document> bm25Results = bm25Future.getNow(Collections.emptyList());

        log.info("混合检索完成: 向量 {} 个, BM25 {} 个, 查询: {}",
                vectorResults.size(), bm25Results.size(), query.text());

        // 返回两组结果的包装，由 DocumentJoiner 做融合
        List<Document> combined = new ArrayList<>();
        combined.addAll(vectorResults);
        combined.addAll(bm25Results);
        return combined;
    }
}
