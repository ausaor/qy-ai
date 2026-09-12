package com.qy.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisChatMemoryRepository 单元测试：
 * 验证消息列表 JSON 序列化往返、Redis 读写与 TTL 续期逻辑（Redis 模板为 mock，无需真实连接）
 */
class RedisChatMemoryRepositoryTest {

    private static final String KEY_PREFIX = "ai:chat:memory:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findByConversationIdShouldRestoreMessagesFromRedisJson() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String json = "[{\"type\":\"USER\",\"content\":\"你好\"},{\"type\":\"ASSISTANT\",\"content\":\"你好，有什么可以帮你？\"}]";
        when(valueOperations.get(KEY_PREFIX + "1-100")).thenReturn(json);

        RedisChatMemoryRepository repository =
                new RedisChatMemoryRepository(redisTemplate, objectMapper, KEY_PREFIX, Duration.ofDays(7));

        List<Message> messages = repository.findByConversationId("1-100");

        assertEquals(2, messages.size());
        assertEquals(MessageType.USER, messages.get(0).getMessageType());
        assertEquals("你好", messages.get(0).getText());
        assertEquals(MessageType.ASSISTANT, messages.get(1).getMessageType());
        assertEquals("你好，有什么可以帮你？", messages.get(1).getText());
    }

    @Test
    void findByConversationIdShouldReturnEmptyWhenKeyAbsent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY_PREFIX + "absent")).thenReturn(null);

        RedisChatMemoryRepository repository =
                new RedisChatMemoryRepository(redisTemplate, objectMapper, KEY_PREFIX, Duration.ofDays(7));

        assertEquals(List.of(), repository.findByConversationId("absent"));
    }

    @Test
    void saveAllShouldSerializeMessagesAndRefreshTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisChatMemoryRepository repository =
                new RedisChatMemoryRepository(redisTemplate, objectMapper, KEY_PREFIX, Duration.ofDays(7));

        repository.saveAll("1-100", List.of(new UserMessage("问题"), new AssistantMessage("回答")));

        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY_PREFIX + "1-100"), jsonCaptor.capture(), any(Duration.class));

        // 用捕获到的 JSON 回放读取链路，验证序列化格式可被完整还原
        ValueOperations<String, String> readBackOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(readBackOperations);
        when(readBackOperations.get(KEY_PREFIX + "1-100")).thenReturn(jsonCaptor.getValue());

        List<Message> restored = repository.findByConversationId("1-100");
        assertEquals(2, restored.size());
        assertEquals(MessageType.USER, restored.get(0).getMessageType());
        assertEquals("问题", restored.get(0).getText());
        assertEquals(MessageType.ASSISTANT, restored.get(1).getMessageType());
        assertEquals("回答", restored.get(1).getText());
    }

    @Test
    void saveAllShouldDeleteKeyWhenMessagesEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        RedisChatMemoryRepository repository =
                new RedisChatMemoryRepository(redisTemplate, objectMapper, KEY_PREFIX, Duration.ofDays(7));

        repository.saveAll("1-100", List.of());

        verify(redisTemplate).delete(KEY_PREFIX + "1-100");
    }

    @Test
    void saveAllShouldSkipToolRelatedMessages() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RedisChatMemoryRepository repository =
                new RedisChatMemoryRepository(redisTemplate, objectMapper, KEY_PREFIX, Duration.ofDays(7));

        // 工具调用消息：无正文、携带 toolCalls
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("call-1", "function", "getCityWeather", "{\"cityName\":\"北京\"}")))
                .build();
        // 工具返回消息
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getCityWeather", "{\"weather\":\"晴\"}")))
                .build();

        repository.saveAll("1-100", List.of(
                new UserMessage("今天北京天气怎么样"),
                toolCallMessage,
                toolResponseMessage,
                new AssistantMessage("北京今天晴，气温25℃")));

        // 工具相关消息被过滤，仅持久化 USER 与最终 ASSISTANT 回复
        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY_PREFIX + "1-100"), jsonCaptor.capture(), any(Duration.class));

        ValueOperations<String, String> readBackOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(readBackOperations);
        when(readBackOperations.get(KEY_PREFIX + "1-100")).thenReturn(jsonCaptor.getValue());

        List<Message> restored = repository.findByConversationId("1-100");
        assertEquals(2, restored.size());
        assertEquals(MessageType.USER, restored.get(0).getMessageType());
        assertEquals("今天北京天气怎么样", restored.get(0).getText());
        assertEquals(MessageType.ASSISTANT, restored.get(1).getMessageType());
        assertEquals("北京今天晴，气温25℃", restored.get(1).getText());
    }
}
