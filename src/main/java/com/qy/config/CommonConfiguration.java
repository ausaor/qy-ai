package com.qy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.JedisPooled;

@Configuration
public class CommonConfiguration {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        config.setDatabase(redisDatabase);
        return new JedisConnectionFactory(config);
    }

    @Bean
    public JedisPooled jedisPooled(JedisConnectionFactory connectionFactory) {
        return new JedisPooled(connectionFactory.getStandaloneConfiguration().getHostName(),
                connectionFactory.getStandaloneConfiguration().getPort());
    }

//    @Bean
//    public VectorStore vectorStore(JedisPooled jedisPooled, OpenAiEmbeddingModel embeddingModel) {
//        return RedisVectorStore.builder(jedisPooled, embeddingModel)
//                .indexName("spring-ai-index")
//                .initializeSchema(true)
//                .prefix("doc:")
//                .metadataFields(
//                        MetadataField.tag("chat_id")
//                )
//                .build();
//    }
//
//    @Bean
//    public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory){
//        return ChatClient
//                .builder(model)
//                .defaultOptions(ChatOptions.builder().model("qwen3.6-plus"))
//                .defaultSystem("你是人生情感大师，为因为情感问题困扰的人提供帮助")
//                .defaultAdvisors(
//                        // MessageChatMemoryAdvisor: 自动管理消息记忆
//                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                        new SimpleLoggerAdvisor()
//                )
//                .build();
//    }


//    @Bean
//    public ChatClient pdfChatClient(
//            OpenAiChatModel model,
//            VectorStore vectorStore) {
//        return ChatClient.builder(model)
//                .defaultSystem("你是PDF文档问答助手。请根据提供的上下文信息回答用户问题。如果上下文中没有相关信息，请明确告知用户无法回答。")
//                .defaultAdvisors(
//                        SimpleLoggerAdvisor.builder().build(),
//                        // PDF问答不需要聊天记忆，每个问题独立处理
//                        QuestionAnswerAdvisor
//                                .builder(vectorStore)
//                                .searchRequest(
//                                        SearchRequest.builder()
//                                                .similarityThreshold(0.5d)
//                                                .topK(2)
//                                                .build()
//                                ).build()
//                )
//                .build();
//    }
}
