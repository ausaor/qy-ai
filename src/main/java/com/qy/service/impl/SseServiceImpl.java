package com.qy.service.impl;

import com.qy.factory.ChatServiceFactory;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IChatService;
import com.qy.service.ISseService;
import com.qy.util.SSEUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseServiceImpl implements ISseService {

    private final ChatServiceFactory chatServiceFactory;

    @Override
    public SseEmitter sseChat(ChatRequest chatRequest) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        try {
            // 构建消息列表
            buildChatMessageList(chatRequest);
            // 设置对话角色
            chatRequest.setRole("user");

            IChatService chatService = chatServiceFactory.getChatService(chatRequest.getModel());
            chatService.chat(chatRequest, sseEmitter);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            SSEUtil.sendErrorEvent(sseEmitter, e.getMessage());
        }
        return sseEmitter;
    }

    @Override
    public Flux<String> streamChat(Long sessionId, ChatRequest chatRequest) {
        // 构建消息列表
        buildChatMessageList(chatRequest);
        IChatService chatService = chatServiceFactory.getChatService(chatRequest.getModel());
        return chatService.streamChat(sessionId, chatRequest);
    }

    @Override
    public Flux<ChatResponse> streamMessage(Long sessionId, ChatMessageRequest request) {
        IChatService chatService = chatServiceFactory.getChatService(request.getModel());
        return chatService.streamMessage(sessionId, request);
    }

    @Override
    public Flux<ServerSentEvent<String>> mcpChat(Long sessionId, ChatMessageRequest request) {
        IChatService chatService = chatServiceFactory.getChatService(request.getModel());
        return chatService.mcpChat(sessionId, request.getContent());
    }

    /**
     * 构建消息列表
     */
    private void buildChatMessageList(ChatRequest chatRequest) {
        List<ChatMessage> messages = new ArrayList<>();

        messages.add(new SystemMessage("你是一个乐于助人的AI助手，能够以对话的方式回应用户。请提供详细且准确的信息。"));

        messages.add(new UserMessage(chatRequest.getContent()));
        chatRequest.setMessages(messages);
    }
}
