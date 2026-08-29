package com.qy.service.impl;

import com.qy.entity.AiChatMessage;
import com.qy.factory.ChatServiceFactory;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IChatService;
import com.qy.service.ISseService;
import com.qy.util.SSEUtil;
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

    private final IAiChatMessageService aiChatMessageService;

    @Override
    public SseEmitter sseChat(ChatRequest chatRequest) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        try {
            // 构建消息列表
            buildChatMessageList(chatRequest);
            // 设置对话角色
            chatRequest.setRole("user");

            IChatService chatService = chatServiceFactory.getChatService(chatRequest.getModel());

            AiChatMessage aiChatMessage = new AiChatMessage();
            aiChatMessage.setSessionId(chatRequest.getSessionId());
            aiChatMessage.setRole("user");
            aiChatMessage.setContent(chatRequest.getContent());
            aiChatMessage.setModel(chatRequest.getModel());

            aiChatMessageService.saveMessage(aiChatMessage);
            chatService.chat(chatRequest, sseEmitter);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            SSEUtil.sendErrorEvent(sseEmitter, e.getMessage());
        }
        return sseEmitter;
    }

    @Override
    public Flux<String> streamChat(ChatRequest chatRequest) {
        // 构建消息列表
        buildChatMessageList(chatRequest);
        IChatService chatService = chatServiceFactory.getChatService(chatRequest.getModel());

        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(chatRequest.getSessionId());
        aiChatMessage.setRole("user");
        aiChatMessage.setContent(chatRequest.getContent());
        aiChatMessage.setModel(chatRequest.getModel());

        aiChatMessageService.saveMessage(aiChatMessage);
        return chatService.streamChat(chatRequest);
    }

    @Override
    public Flux<ChatResponse> streamMessage(ChatMessageRequest request) {
        IChatService chatService = chatServiceFactory.getChatService(request.getModel());

        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(request.getSessionId());
        aiChatMessage.setRole("user");
        aiChatMessage.setContent(request.getContent());
        aiChatMessage.setModel(request.getModel());

        aiChatMessageService.saveMessage(aiChatMessage);
        return chatService.streamMessage(request);
    }

    @Override
    public Flux<ServerSentEvent<String>> mcpChat(ChatMessageRequest request) {
        IChatService chatService = chatServiceFactory.getChatService(request.getModel());
        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(request.getSessionId());
        aiChatMessage.setRole("user");
        aiChatMessage.setContent(request.getContent());
        aiChatMessage.setModel(request.getModel());

        aiChatMessageService.saveMessage(aiChatMessage);
        return chatService.mcpChat(request);
    }

    /**
     * 构建消息列表
     */
    private void buildChatMessageList(ChatRequest chatRequest) {

    }
}
