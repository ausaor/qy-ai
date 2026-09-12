package com.qy.rag.postprocessor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 LLM 的文档重排序器
 * 使用 Qwen 对检索到的文档按相关性排序，截取 top-K
 */
@Slf4j
public class LlmRerankingPostProcessor implements DocumentPostProcessor {

    private final ChatClient.Builder chatClientBuilder;
    private final int topK;

    public LlmRerankingPostProcessor(ChatClient.Builder chatClientBuilder) {
        this(chatClientBuilder, 5);
    }

    public LlmRerankingPostProcessor(ChatClient.Builder chatClientBuilder, int topK) {
        this.chatClientBuilder = chatClientBuilder;
        this.topK = topK;
    }

    private static final String RERANK_PROMPT_TEMPLATE = """
            给定用户查询和以下文档片段，请按与查询的相关性从高到低排序。
            只返回文档编号列表，用逗号分隔，不要添加任何解释。

            用户查询: {query}

            文档片段:
            {documents}

            请返回排序后的编号（例如: 3,1,5,2,4）:
            """;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        // 如果文档数量不超过 topK，直接返回
        if (documents.size() <= topK) {
            return documents;
        }

        try {
            // 构建文档编号列表
            StringBuilder docList = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                String content = documents.get(i).getText();
                // 截取前 200 字符避免 prompt 过长
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                docList.append(String.format("[%d] %s\n", i + 1, content));
            }

            String prompt = new PromptTemplate(RERANK_PROMPT_TEMPLATE)
                    .create(Map.of("query", query.text(), "documents", docList.toString()))
                    .getContents();

            String response = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析排序结果
            List<Integer> rankedIndices = parseRanking(response, documents.size());

            List<Document> reranked = rankedIndices.stream()
                    .filter(i -> i >= 0 && i < documents.size())
                    .map(documents::get)
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("LLM 重排序完成: {} → {} 个文档", documents.size(), reranked.size());
            return reranked.isEmpty() ? documents.subList(0, Math.min(topK, documents.size())) : reranked;

        } catch (Exception e) {
            log.warn("LLM 重排序失败，回退到原始顺序: {}", e.getMessage());
            return documents.subList(0, Math.min(topK, documents.size()));
        }
    }

    /**
     * 解析 LLM 返回的编号列表
     */
    private List<Integer> parseRanking(String response, int maxIndex) {
        List<Integer> indices = new ArrayList<>();
        if (response == null) return indices;

        // 提取数字
        String cleaned = response.replaceAll("[^0-9,，\\s]", "").trim();
        for (String part : cleaned.split("[,，\\s]+")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1; // 转为 0-based
                if (idx >= 0 && idx < maxIndex && !indices.contains(idx)) {
                    indices.add(idx);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return indices;
    }
}
