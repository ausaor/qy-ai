package com.qy.service.impl;

import com.qy.entity.AiChatMessage;
import com.qy.enums.ChatModeType;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IChatService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Service("qianWenAiChatServiceImpl")
@RequiredArgsConstructor
public class QianWenAiChatServiceImpl implements IChatService {
    @Value("${spring.ai.dash-scope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dash-scope.chat.options.model}")
    private String modelName;

    private final IAiChatMessageService aiChatMessageService;

    @Override
    public Flux<ChatResponse> streamMessage(ChatMessageRequest request) {
        return null;
    }

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        UserSession session = SessionContext.getSession();
        StreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(2000)
                .temperature(0.7F)
                .build();
        StringBuilder builder = new StringBuilder();

        return Flux.create(sink -> {
            try {
                model.chat(request.getMessages(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        // 使用正确的构建方式
                        sink.next(partialResponse);
                        log.info("收到消息片段: {}", partialResponse);
                        builder.append(partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                        log.info("消息结束，完整消息ID: {}", completeResponse.id());
                        sink.complete();
                        String fullContent = builder.toString();
                        if (!fullContent.isEmpty()) {
                            log.info("Complete Flux<String> chat result: {}", fullContent);
                            AiChatMessage aiChatMessage = new AiChatMessage();
                            aiChatMessage.setSessionId(request.getSessionId());
                            aiChatMessage.setRole("assistant");
                            aiChatMessage.setContent(fullContent);
                            aiChatMessage.setModel(request.getModel());
                            aiChatMessage.setUserId(session.getUserId());
                            aiChatMessage.setCreateTime(LocalDateTime.now());
                            aiChatMessageService.save(aiChatMessage);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式请求失败:", error);
                        sink.error(error);
                    }
                });
            } catch (Exception e) {
                log.error("千问请求失败：{}", e.getMessage());
                sink.error(e);
            }
        });
    }

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        UserSession session = SessionContext.getSession();
        StreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(2000)
                .temperature(0.7F)
                .build();
        StringBuilder builder = new StringBuilder();
        // 发送流式消息
        try {
            model.chat(chatRequest.getMessages(), new StreamingChatResponseHandler() {
                @SneakyThrows
                @Override
                public void onPartialResponse(String partialResponse) {
                    emitter.send(partialResponse);
                    log.info("收到消息片段: {}", partialResponse);
                    builder.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    try {
                        emitter.send(SseEmitter.event().name("end").data("DONE"));
                    } catch (IOException e) {
                        log.error("SSE发送失败: {}", e.getMessage());
                    }
                    emitter.complete();
                    log.info("消息结束，完整消息ID: {}", completeResponse);
                    String fullContent = builder.toString();
                    if (!fullContent.isEmpty()) {
                        log.info("Complete SseEmitter chat result: {}", fullContent);
                        AiChatMessage aiChatMessage = new AiChatMessage();
                        aiChatMessage.setSessionId(chatRequest.getSessionId());
                        aiChatMessage.setRole("assistant");
                        aiChatMessage.setContent(fullContent);
                        aiChatMessage.setModel(chatRequest.getModel());
                        aiChatMessage.setUserId(session.getUserId());
                        aiChatMessage.setCreateTime(LocalDateTime.now());
                        aiChatMessageService.save(aiChatMessage);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    error.printStackTrace();
                }
            });
        } catch (Exception e) {
            log.error("千问请求失败：{}", e.getMessage());
        }

        return emitter;
    }

    @Override
    public Flux<ServerSentEvent<String>> mcpChat(ChatMessageRequest request) {
        return null;
    }

    @Override
    public String getCategory() {
        return ChatModeType.QIANWEN.getCode();
    }
}
