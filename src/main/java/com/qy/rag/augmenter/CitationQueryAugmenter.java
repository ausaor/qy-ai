package com.qy.rag.augmenter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 引用增强查询增强器
 * 在 prompt 中注入带编号的文档引用，并指示 LLM 使用 [N] 标注来源
 */
@Slf4j
public class CitationQueryAugmenter implements QueryAugmenter {

    private final ContextualQueryAugmenter delegate;

    public CitationQueryAugmenter() {
        this.delegate = ContextualQueryAugmenter.builder()
                .documentFormatter(this::formatDocumentsWithCitations)
                .build();
    }

    @Override
    public Query augment(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return delegate.augment(query, documents);
        }

        // 使用委托增强器，但用自定义的文档格式化方法
        Query augmented = delegate.augment(query, documents);
        log.debug("引用增强完成: {} 个文档片段", documents.size());
        return augmented;
    }

    /**
     * 将文档格式化为带编号引用的文本
     * [1] (来源: xxx.pdf, 第2块) 内容...
     */
    private String formatDocumentsWithCitations(List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(i -> {
                    Document doc = documents.get(i);
                    String source = doc.getMetadata().getOrDefault("source_file", "未知来源").toString();
                    String chunkIndex = doc.getMetadata().getOrDefault("chunk_index", "").toString();
                    String fileType = doc.getMetadata().getOrDefault("file_type", "").toString();

                    StringBuilder ref = new StringBuilder();
                    ref.append(String.format("[%d] (来源: %s", i + 1, source));
                    if (!chunkIndex.isEmpty()) {
                        ref.append(", 第").append(chunkIndex).append("块");
                    }
                    ref.append(") ");
                    ref.append(doc.getText());
                    return ref.toString();
                })
                .collect(Collectors.joining("\n\n"));
    }
}
