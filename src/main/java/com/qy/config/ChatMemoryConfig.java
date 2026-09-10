package com.qy.config;

import com.qy.contant.RedisKey;
import com.qy.memory.RedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
public class ChatMemoryConfig {

    /**
     * 对话记忆：基于内存存储 + 滑动窗口（最多保留最近 20 条消息）
     * Spring AI 2.0 移除了 InMemoryChatMemory，推荐使用 MessageWindowChatMemory
     */
//    @Bean
//    public ChatMemory chatMemory() {
//        return MessageWindowChatMemory.builder()
//                .chatMemoryRepository(new InMemoryChatMemoryRepository())
//                .maxMessages(20)
//                .build();
//    }

    /**
     * 对话记忆：基于 Redis 持久化 + 滑动窗口（最多保留最近 maxMessages 条消息）
     * Spring AI 2.0 移除了 InMemoryChatMemory，推荐使用 MessageWindowChatMemory；
     * 底层仓库替换为自定义 RedisChatMemoryRepository，使对话上下文跨服务重启保留、多实例共享，
     * 并借助 TTL 自动回收长期不活跃的会话记忆
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository redisChatMemoryRepository,
            @Value("${spring.ai.chat.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * Redis 对话记忆仓库：每个会话（conversationId）一个 key，
     * 消息列表 JSON 序列化存储，带 TTL 自动过期（每次保存自动续期）
     */
    @Bean
    public ChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.ai.chat.memory.redis.time-to-live:7d}") Duration timeToLive) {
        return new RedisChatMemoryRepository(stringRedisTemplate, objectMapper, RedisKey.CHAT_MEMORY, timeToLive);
    }
}
