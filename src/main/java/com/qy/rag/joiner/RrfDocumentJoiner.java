package com.qy.rag.joiner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion (RRF) 文档融合器
 * 将多路检索结果按 RRF 算法合并排序
 *
 * RRF 公式: score(d) = Σ 1/(k + rank_i)，k=60
 */
@Slf4j
public class RrfDocumentJoiner implements DocumentJoiner {

    private static final int K = 60; // RRF 常数

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        // RRF 评分表: doc_key -> score
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        // 保留第一个出现的 Document 对象
        Map<String, Document> docMap = new LinkedHashMap<>();

        for (Map.Entry<Query, List<List<Document>>> entry : documentsForQuery.entrySet()) {
            for (List<Document> rankedList : entry.getValue()) {
                for (int rank = 0; rank < rankedList.size(); rank++) {
                    Document doc = rankedList.get(rank);
                    String key = getDocKey(doc);

                    rrfScores.merge(key, 1.0 / (K + rank + 1), Double::sum);
                    docMap.putIfAbsent(key, doc);
                }
            }
        }

        // 按 RRF 分数降序排序
        List<Document> result = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> docMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("RRF 融合完成: {} 个查询 → {} 个去重结果",
                documentsForQuery.size(), result.size());
        return result;
    }

    /**
     * 生成文档的唯一标识
     * 优先使用 doc_id，否则用内容哈希
     */
    private String getDocKey(Document doc) {
        if (doc.getId() != null && !doc.getId().isEmpty()) {
            return doc.getId();
        }
        String source = doc.getMetadata().getOrDefault("source_file", "").toString();
        String chunk = doc.getMetadata().getOrDefault("chunk_index", "").toString();
        return source + ":" + chunk + ":" + doc.getText().hashCode();
    }
}
