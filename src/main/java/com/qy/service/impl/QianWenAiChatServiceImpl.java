package com.qy.service.impl;

import com.qy.entity.AiChatMessage;
import com.qy.enums.ChatModeType;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IChatService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 通义千问对话服务实现
 * 通过 DashScope 的 OpenAI 兼容接口，基于 Spring AI 2.0 的 ChatClient 进行流式对话
 */
@Slf4j
@Service("qianWenAiChatServiceImpl")
public class QianWenAiChatServiceImpl implements IChatService {

    /** 全局系统提示词 */
    @Value("${spring.ai.assistant.system-prompt}")
    private String systemPrompt;

    /** 千问默认聊天选项（模型/温度/最大token数） */
    private final OpenAiChatOptions chatOptions;

    /** 千问 ChatClient */
    private final ChatClient chatClient;

    /** Jackson 3 ObjectMapper */
    private final ObjectMapper objectMapper;

    private final IAiChatMessageService aiChatMessageService;

    /**
     * 构造器注入：通过 @Qualifier 明确指定千问专属的 Bean，
     * 避免与 DeepSeek 的同类型 Bean 产生歧义
     */
    public QianWenAiChatServiceImpl(
            @Qualifier("qianwenChatOptions") OpenAiChatOptions chatOptions,
            @Qualifier("qianwenChatClient") ChatClient chatClient,
            ObjectMapper objectMapper,
            IAiChatMessageService aiChatMessageService) {
        this.chatOptions = chatOptions;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.aiChatMessageService = aiChatMessageService;
    }

    /**
     * 构建自定义聊天选项，请求参数优先，缺省使用默认配置
     */
    private OpenAiChatOptions.Builder buildOptions(ChatMessageRequest request) {
        return OpenAiChatOptions.builder()
                .model(chatOptions.getModel())
                .temperature(request.getTemperature() != null ? request.getTemperature().doubleValue() : 0.7)
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 2000);
    }

    /**
     * 保存 AI 回复消息
     */
    private void saveAssistantMessage(ChatMessageRequest request, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        UserSession session = SessionContext.getSession();
        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(request.getSessionId());
        aiChatMessage.setRole("assistant");
        aiChatMessage.setContent(content);
        aiChatMessage.setModel(request.getModel());
        aiChatMessage.setUserId(session != null ? session.getUserId() : null);
        aiChatMessage.setCreateTime(LocalDateTime.now());
        aiChatMessageService.save(aiChatMessage);
    }

    @Override
    public Flux<ChatResponse> streamMessage(ChatMessageRequest request) {
        log.info("千问流式发送消息到会话: sessionId = {}, content = {}", request.getSessionId(), request.getContent());

        UserSession session = SessionContext.getSession();

        // 累积完整回复内容
        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(request.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getUserId() + "-" + request.getSessionId()))
                .options(buildOptions(request))
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    try {
                        String content = response.getResult() != null && response.getResult().getOutput() != null
                                ? response.getResult().getOutput().getText() : null;
                        if (content != null) {
                            contentBuilder.append(content);
                            log.info("AI回复(流式): {}", content);
                        }
                    } catch (Exception e) {
                        log.warn("从响应中获取内容时出错: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.toString();
                    log.info("AI回复(完整): {}", fullContent);
                    saveAssistantMessage(request, fullContent);
                })
                .onErrorResume(e -> {
                    log.error("流式消息处理出错: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Flux<String> streamChat(ChatRequest chatRequest) {
        log.info("千问流式发送消息到会话: sessionId = {}, content = {}", chatRequest.getSessionId(), chatRequest.getContent());

        UserSession session = SessionContext.getSession();

        // 累积完整回复内容
        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(chatRequest.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getUserId() + "-" + chatRequest.getSessionId()))
                .stream()
                .content()
                .doOnNext(contentBuilder::append)
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.toString();
                    log.info("AI回复(完整): {}", fullContent);
                    ChatMessageRequest request = new ChatMessageRequest();
                    request.setSessionId(chatRequest.getSessionId());
                    request.setModel(chatRequest.getModel());
                    saveAssistantMessage(request, fullContent);
                })
                .onErrorResume(e -> {
                    log.error("流式消息处理出错: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        log.info("千问SSE发送消息到会话: sessionId = {}, content = {}", chatRequest.getSessionId(), chatRequest.getContent());

        UserSession session = SessionContext.getSession();

        StringBuilder contentBuilder = new StringBuilder();

        chatClient.prompt()
                .system(systemPrompt)
                .user(chatRequest.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getUserId() + "-" + chatRequest.getSessionId()))
                .stream()
                .content()
                .doOnNext(content -> {
                    try {
                        contentBuilder.append(content);
                        emitter.send(SseEmitter.event().name("message").data(content, MediaType.TEXT_PLAIN));
                    } catch (Exception e) {
                        log.warn("SSE发送内容时出错: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    try {
                        ChatMessageRequest request = new ChatMessageRequest();
                        request.setSessionId(chatRequest.getSessionId());
                        request.setModel(chatRequest.getModel());
                        saveAssistantMessage(request, contentBuilder.toString());
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("SSE完成事件处理出错: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    log.error("SSE对话出错: {}", e.getMessage());
                    emitter.completeWithError(e);
                })
                .subscribe();

        return emitter;
    }

    @Override
    public Flux<ServerSentEvent<String>> mcpChat(ChatMessageRequest request) {
        UserSession session = SessionContext.getSession();
        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(request.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getUserId() + "-" + request.getSessionId()))
                .options(buildOptions(request))
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    try {
                        String content = response.getResult() != null && response.getResult().getOutput() != null
                                ? response.getResult().getOutput().getText() : null;
                        if (content != null) {
                            contentBuilder.append(content);
                        }
                    } catch (Exception e) {
                        log.warn("Error getting content from response: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // 当流完成时，保存完整的对话结果
                    String fullContent = contentBuilder.toString();
                    if (!fullContent.isEmpty()) {
                        log.info("Complete mcp chat result: {}", fullContent);
                        saveAssistantMessage(request, fullContent);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error in mcp chat: {}", e.getMessage());
                    return Flux.empty();
                })
                .map(chatResponse -> ServerSentEvent.<String>builder()
                        .data(toJson(chatResponse))
                        .event("message")
                        .build());
    }

    /**
     * 将流式回答结果转json字符串
     *
     * @param chatResponse 流式回答结果
     * @return String json字符串
     */
    @SneakyThrows
    public String toJson(ChatResponse chatResponse) {
        return objectMapper.writeValueAsString(chatResponse);
    }

    @Override
    public String getCategory() {
        return ChatModeType.QIANWEN.getCode();
    }
}
