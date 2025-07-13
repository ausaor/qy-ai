package com.qy.service;

import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

public interface ISseService {
    /**
     * 客户端发送消息到服务端
     * @param chatRequest 请求对象
     */
    SseEmitter sseChat(ChatRequest chatRequest);

    Flux<String> streamChat(Long sessionId, ChatRequest chatRequest);

    Flux<ChatResponse> streamMessage(Long sessionId, ChatMessageRequest request);

    Flux<ServerSentEvent<String>> mcpChat(Long sessionId, ChatMessageRequest request);
}
