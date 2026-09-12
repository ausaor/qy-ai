package com.qy.config;

import com.qy.rag.augmenter.CitationQueryAugmenter;
import com.qy.rag.joiner.RrfDocumentJoiner;
import com.qy.rag.postprocessor.LlmRerankingPostProcessor;
import com.qy.rag.retrieval.Bm25DocumentRetriever;
import com.qy.rag.retrieval.HybridDocumentRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 高级 RAG 管线配置
 * 定义混合检索、查询改写、LLM 重排序、引用增强等组件
 */
@Configuration
public class AdvancedRagConfiguration {

    /**
     * 向量检索器
     */
    @Bean
    public VectorStoreDocumentRetriever vectorStoreDocumentRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.4)
                .topK(10)
                .build();
    }

    /**
     * 混合检索器（向量 + BM25）
     */
    @Bean
    public HybridDocumentRetriever hybridDocumentRetriever(
            VectorStoreDocumentRetriever vectorRetriever,
            Bm25DocumentRetriever bm25Retriever) {
        return new HybridDocumentRetriever(vectorRetriever, bm25Retriever);
    }

    /**
     * RAG 管线 Advisor
     * 串联: 查询改写 → 查询扩展 → 混合检索 → RRF 融合 → LLM 重排序 → 引用增强
     */
    @Bean
    public RetrievalAugmentationAdvisor advancedRagAdvisor(
            HybridDocumentRetriever hybridRetriever,
            @Qualifier("qianwenChatModel") OpenAiChatModel model) {

        // 用于查询改写和扩展的 ChatClient Builder
        ChatClient.Builder ragChatClientBuilder = ChatClient.builder(model)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen3.6-plus"));

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(
                        RewriteQueryTransformer.builder()
                                .chatClientBuilder(ragChatClientBuilder)
                                .build()
                )
                .queryExpander(
                        MultiQueryExpander.builder()
                                .chatClientBuilder(ragChatClientBuilder)
                                .numberOfQueries(3)
                                .includeOriginal(true)
                                .build()
                )
                .documentRetriever(hybridRetriever)
                .documentJoiner(new RrfDocumentJoiner())
                .documentPostProcessors(
                        new LlmRerankingPostProcessor(ragChatClientBuilder, 5)
                )
                .queryAugmenter(new CitationQueryAugmenter())
                .build();
    }

    /**
     * 高级文档问答 ChatClient
     * 使用 RetrievalAugmentationAdvisor 替代 QuestionAnswerAdvisor
     */
    @Bean
    public ChatClient advancedDocChatClient(
            @Qualifier("qianwenChatModel") OpenAiChatModel model,
            RetrievalAugmentationAdvisor advancedRagAdvisor,
            ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem("""
                        你是文档问答助手。请根据提供的上下文信息回答用户问题。
                        回答时必须使用 [N] 标注引用来源，例如 [1]、[2]。
                        如果上下文中没有相关信息，请明确告知用户无法回答。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        advancedRagAdvisor,
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
