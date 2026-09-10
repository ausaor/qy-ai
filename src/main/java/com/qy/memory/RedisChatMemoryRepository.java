package com.qy.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的对话记忆仓库（Spring AI 2.0 {@link ChatMemoryRepository} 实现）
 * <p>
 * 每个会话（conversationId）对应一个 Redis String 键：
 * <pre>ai:chat:memory:{conversationId} -> [{"type":"USER","content":"..."},{"type":"ASSISTANT","content":"..."}]</pre>
 * 消息仅存类型与文本内容，避免 media / toolCalls / metadata 等复杂字段的序列化问题；
 * 整体带 TTL 自动过期，每次保存自动续期，长期不活跃的会话记忆会被 Redis 自动回收。
 * <p>
 * Redis 不可用时降级处理：读取返回空历史、写入仅记日志，保证核心对话流程不受记忆存储故障影响。
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    /** Redis 字符串操作模板（键与值均按字符串处理，值即 JSON 序列化的消息列表） */
    private final StringRedisTemplate redisTemplate;

    /** Jackson 3 序列化器 */
    private final ObjectMapper objectMapper;

    /** 对话记忆键前缀 */
    private final String keyPrefix;

    /** 记忆过期时间（null 表示永不过期） */
    private final Duration timeToLive;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            String keyPrefix, Duration timeToLive) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.timeToLive = timeToLive;
    }

    @Override
    public List<String> findConversationIds() {
        List<String> conversationIds = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(keyPrefix + "*").count(100).build())) {
            cursor.forEachRemaining(key -> conversationIds.add(key.substring(keyPrefix.length())));
        } catch (Exception e) {
            log.warn("扫描对话记忆会话ID失败: {}", e.getMessage());
        }
        return conversationIds;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(keyPrefix + conversationId);
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            List<ChatMemoryMessageDto> messageDtos =
                    objectMapper.readValue(json, new TypeReference<List<ChatMemoryMessageDto>>() { });
            return messageDtos.stream()
                    .map(this::restoreMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("读取对话记忆失败, conversationId = {}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = keyPrefix + conversationId;
        try {
            List<ChatMemoryMessageDto> messageDtos = messages == null
                    ? List.of()
                    : messages.stream().map(ChatMemoryMessageDto::from).toList();

            // 无消息时直接清理，避免残留空键
            if (messageDtos.isEmpty()) {
                redisTemplate.delete(key);
                return;
            }

            String json = objectMapper.writeValueAsString(messageDtos);
            if (timeToLive != null && !timeToLive.isZero() && !timeToLive.isNegative()) {
                redisTemplate.opsForValue().set(key, json, timeToLive);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
        } catch (Exception e) {
            log.warn("保存对话记忆失败, conversationId = {}: {}", conversationId, e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            redisTemplate.delete(keyPrefix + conversationId);
        } catch (Exception e) {
            log.warn("删除对话记忆失败, conversationId = {}: {}", conversationId, e.getMessage());
        }
    }

    /** 将持久化 DTO 还原为 Spring AI 消息对象 */
    private Message restoreMessage(ChatMemoryMessageDto messageDto) {
        String content = messageDto.content() == null ? "" : messageDto.content();
        return switch (messageDto.type()) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> null;
        };
    }

    /** 对话记忆的持久化 DTO：仅保留消息类型与文本内容 */
    record ChatMemoryMessageDto(String type, String content) {

        static ChatMemoryMessageDto from(Message message) {
            MessageType messageType = message.getMessageType() != null ? message.getMessageType() : MessageType.USER;
            String text = message.getText() != null ? message.getText() : "";
            return new ChatMemoryMessageDto(messageType.name(), text);
        }
    }
}
