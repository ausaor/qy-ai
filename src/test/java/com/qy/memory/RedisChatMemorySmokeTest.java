package com.qy.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 真实 Redis 冒烟验证：saveAll / findByConversationId / TTL / 清理（验证后即删除，不残留数据） */
@SpringBootTest
class RedisChatMemorySmokeTest {

    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final String CONVERSATION_ID = "smoke-test-conversation";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void smokeTestRedisRoundTrip() {
        RedisChatMemoryRepository repository =
                new RedisChatMemoryRepository(stringRedisTemplate, objectMapper, KEY_PREFIX, Duration.ofDays(7));

        repository.saveAll(CONVERSATION_ID,
                List.of(new UserMessage("你好"), new AssistantMessage("你好，我是AI助手")));

        // 验证 TTL 已设置（约 7 天）
        Long ttl = stringRedisTemplate.getExpire(KEY_PREFIX + CONVERSATION_ID);
        assertNotEquals(null, ttl);
        assertNotEquals(-1L, ttl);

        // 验证读取还原
        List<Message> messages = repository.findByConversationId(CONVERSATION_ID);
        assertEquals(2, messages.size());
        assertEquals("你好", messages.get(0).getText());
        assertEquals("你好，我是AI助手", messages.get(1).getText());

        // 清理验证数据
        repository.deleteByConversationId(CONVERSATION_ID);
        assertEquals(List.of(), repository.findByConversationId(CONVERSATION_ID));
    }
}
