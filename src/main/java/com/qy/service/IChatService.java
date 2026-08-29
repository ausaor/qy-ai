package com.qy.service;

import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

public interface IChatService {
    /**
     * 流式发送消息到会话并获取AI回复
     *
     * @param request 消息请求
     * @return 流式聊天响应
     */
    Flux<ChatResponse> streamMessage(ChatMessageRequest request);


    /**
     * 流式发送消息到会话并获取AI回复
     *
     * @param chatRequest 消息请求
     * @return 流式聊天响应（SSE格式）
     */
    Flux<ServerSentEvent<String>> streamChat(ChatRequest chatRequest);

    /**
     * 客户端发送消息到服务端
     * @param chatRequest 请求对象
     */
    SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter);


    /**
     * MCP对话服务
     *
     * @param request 请求对象
     * @return 响应
     */
    Flux<ServerSentEvent<String>> mcpChat(ChatMessageRequest request);

    /**
     * 获取此服务支持的模型类别
     */
    String getCategory();
}
