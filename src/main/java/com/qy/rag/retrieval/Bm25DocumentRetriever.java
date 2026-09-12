package com.qy.rag.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 基于纯 Java 的 BM25 关键词检索器
 * 无外部依赖，使用简单的分词 + TF-IDF 评分
 */
@Slf4j
@Component
public class Bm25DocumentRetriever implements DocumentRetriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    // 文档存储
    private final List<StoredDocument> documents = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private double avgDocLength = 0;

    /**
     * 向 BM25 索引添加文档
     */
    public void addDocuments(List<Document> docs) {
        lock.writeLock().lock();
        try {
            for (Document doc : docs) {
                StoredDocument stored = new StoredDocument(
                        doc.getId(),
                        doc.getText(),
                        doc.getMetadata().getOrDefault("source_file", "").toString(),
                        doc.getMetadata().getOrDefault("chunk_index", "").toString()
                );
                documents.add(stored);
            }
            // 重新计算平均文档长度
            avgDocLength = documents.stream()
                    .mapToInt(d -> d.tokens.length)
                    .average()
                    .orElse(1.0);
            log.info("BM25 索引写入 {} 个文档，当前共 {} 个文档", docs.size(), documents.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取 BM25 索引中的文档总数
     */
    public int getDocumentCount() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取不同来源文件的数量
     */
    public long getSourceFileCount() {
        lock.readLock().lock();
        try {
            return documents.stream()
                    .map(d -> d.sourceFile)
                    .distinct()
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        try {
            String[] queryTokens = tokenize(query.text());
            if (queryTokens.length == 0) {
                return Collections.emptyList();
            }

            // 计算每个文档的 BM25 分数
            List<ScoredDocument> scored = new ArrayList<>();
            for (StoredDocument doc : documents) {
                double score = bm25Score(queryTokens, doc);
                if (score > 0) {
                    scored.add(new ScoredDocument(doc, score));
                }
            }

            // 按分数降序排列，取 top 10
            List<Document> results = scored.stream()
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(10)
                    .map(sd -> toSpringDocument(sd))
                    .collect(Collectors.toList());

            log.debug("BM25 检索返回 {} 个结果，查询: {}", results.size(), query.text());
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * BM25 评分公式
     */
    private double bm25Score(String[] queryTokens, StoredDocument doc) {
        double score = 0;
        int docLen = doc.tokens.length;

        for (String token : queryTokens) {
            int tf = doc.termFreq.getOrDefault(token, 0);
            if (tf == 0) continue;

            // 文档频率：包含该词的文档数
            int df = 0;
            for (StoredDocument d : documents) {
                if (d.termFreq.containsKey(token)) df++;
            }

            // IDF
            double idf = Math.log((documents.size() - df + 0.5) / (df + 0.5) + 1.0);

            // TF 归一化
            double tfNorm = (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLen / avgDocLength));

            score += idf * tfNorm;
        }
        return score;
    }

    /**
     * 简单分词：按空白和标点切分，转小写
     * 对中文按单字切分（bigram 效果更好但实现更复杂）
     */
    private String[] tokenize(String text) {
        if (text == null || text.isEmpty()) return new String[0];

        // 英文按空白和标点切分，中文按字符切分
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isWhitespace(c) || ",.!?;:，。！？；：、\"'()[]{}【】「」".indexOf(c) >= 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                // 中文字符单独作为一个 token
                if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    tokens.add(String.valueOf(c));
                }
            } else if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                // 中文字符：先保存之前的英文 token
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }

    private Document toSpringDocument(ScoredDocument sd) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_file", sd.doc.sourceFile);
        metadata.put("chunk_index", sd.doc.chunkIndex);
        metadata.put("bm25_score", String.format("%.4f", sd.score));

        if (sd.doc.docId != null && !sd.doc.docId.isEmpty()) {
            return new Document(sd.doc.docId, sd.doc.content, metadata);
        }
        return new Document(sd.doc.content, metadata);
    }

    /**
     * 内部文档存储
     */
    private static class StoredDocument {
        final String docId;
        final String content;
        final String sourceFile;
        final String chunkIndex;
        final String[] tokens;
        final Map<String, Integer> termFreq;

        StoredDocument(String docId, String content, String sourceFile, String chunkIndex) {
            this.docId = docId;
            this.content = content;
            this.sourceFile = sourceFile;
            this.chunkIndex = chunkIndex;
            this.tokens = tokenizeStatic(content);
            this.termFreq = new HashMap<>();
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
            }
        }

        private static String[] tokenizeStatic(String text) {
            if (text == null || text.isEmpty()) return new String[0];
            List<String> tokens = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (char c : text.toLowerCase().toCharArray()) {
                if (Character.isWhitespace(c) || ",.!?;:，。！？；：、\"'()[]{}【】「」".indexOf(c) >= 0) {
                    if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
                } else if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
                    tokens.add(String.valueOf(c));
                } else {
                    current.append(c);
                }
            }
            if (current.length() > 0) tokens.add(current.toString());
            return tokens.toArray(new String[0]);
        }
    }

    private record ScoredDocument(StoredDocument doc, double score) {}
}
