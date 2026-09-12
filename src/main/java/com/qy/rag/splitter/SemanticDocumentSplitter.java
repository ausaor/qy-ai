package com.qy.rag.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义文档分块器
 * 将文档按语义边界分块，保留元数据信息用于引用溯源
 */
@Slf4j
@Component
public class SemanticDocumentSplitter {

    private final TokenTextSplitter tokenTextSplitter;

    public SemanticDocumentSplitter() {
        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(300)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .build();
    }

    /**
     * 对文档列表进行语义分块
     *
     * @param documents 原始文档列表
     * @return 分块后的文档列表，每个 chunk 带有 chunk_index 和来源元数据
     */
    public List<Document> split(List<Document> documents) {
        List<Document> allChunks = new ArrayList<>();

        for (Document doc : documents) {
            String sourceFile = doc.getMetadata().getOrDefault("source_file", "unknown").toString();
            String fileType = doc.getMetadata().getOrDefault("file_type", "unknown").toString();

            // 使用 TokenTextSplitter 进行分块
            List<Document> chunks = tokenTextSplitter.apply(List.of(doc));

            // 为每个 chunk 添加分块元数据
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                chunk.getMetadata().put("chunk_index", String.valueOf(i));
                chunk.getMetadata().put("source_file", sourceFile);
                chunk.getMetadata().put("file_type", fileType);
                chunk.getMetadata().put("total_chunks", String.valueOf(chunks.size()));
            }

            allChunks.addAll(chunks);
            log.debug("文档 {} 分块完成: {} 个 chunk", sourceFile, chunks.size());
        }

        log.info("语义分块完成: {} 个文档 → {} 个 chunk", documents.size(), allChunks.size());
        return allChunks;
    }
}
