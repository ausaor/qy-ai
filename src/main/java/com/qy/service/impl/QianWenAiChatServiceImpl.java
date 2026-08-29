package com.qy.service.impl;

import com.qy.enums.ChatModeType;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IChatService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
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
        return null;
    }

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        return null;
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
