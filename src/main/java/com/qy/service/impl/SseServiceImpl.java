package com.qy.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.qy.agent.AgentRegistry;
import com.qy.agent.AgentType;
import com.qy.entity.AiChatMessage;
import com.qy.factory.ChatServiceFactory;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IAiChatSessionService;
import com.qy.service.IChatService;
import com.qy.service.ISseService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import com.qy.util.SSEUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseServiceImpl implements ISseService {

    private final ChatServiceFactory chatServiceFactory;

    private final IAiChatMessageService aiChatMessageService;

    private final IAiChatSessionService aiChatSessionService;

    private final AgentRegistry agentRegistry;

    @Override
    public SseEmitter sseChat(ChatRequest chatRequest) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        try {
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
    public Flux<ServerSentEvent<String>> streamChat(ChatRequest chatRequest) {
        IChatService chatService = chatServiceFactory.getChatService(chatRequest.getModel());

        aiChatSessionService.saveAiChatSession(chatRequest);

        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(chatRequest.getSessionId());
        aiChatMessage.setRole("user");
        aiChatMessage.setContent(chatRequest.getContent());
        aiChatMessage.setModel(chatRequest.getModel());

        aiChatMessageService.saveMessage(aiChatMessage);
        if (CollectionUtil.isEmpty(chatRequest.getFiles())) {
            return chatService.streamChat(chatRequest);
        } else {
            return chatService.streamMultiModalChat(chatRequest);
        }
    }

    @Override
    public Flux<ServerSentEvent<String>> streamAgentChat(ChatRequest chatRequest) {
        aiChatSessionService.saveAiChatSession(chatRequest);

        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(chatRequest.getSessionId());
        aiChatMessage.setRole("user");
        aiChatMessage.setContent(chatRequest.getContent());
        aiChatMessage.setModel(chatRequest.getModel());

        aiChatMessageService.saveMessage(aiChatMessage);

        ChatClient router = agentRegistry.getAgent(AgentType.ROUTER);
        // 将会话 ID 放入 ToolContext，供 AgentRouterTool 路由到子 Agent 时继续传递

        UserSession session = SessionContext.getSession();
        String conversationId = (session != null ? session.getUserId() : "anonymous") + "-" + chatRequest.getSessionId();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put("conversationId", conversationId);

        // 累积完整回复内容
        StringBuilder contentBuilder = new StringBuilder();

        return router.prompt()
                .user(chatRequest.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(toolContext)
                .stream()
                .content()
                .doOnNext(contentBuilder::append)
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.toString();
                    log.info("AI回复(完整): {}", fullContent);
                    ChatMessageRequest request = new ChatMessageRequest();
                    request.setSessionId(chatRequest.getSessionId());
                    request.setModel(chatRequest.getModel());
                    saveAssistantMessage(request, fullContent, session);
                })
                .onErrorResume(e -> {
                    log.error("流式消息处理出错: {}", e.getMessage());
                    return Flux.just("抱歉，AI服务暂时不可用，请稍后重试。");
                })
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build());
    }

    /**
     * 保存 AI 回复消息
     */
    private void saveAssistantMessage(ChatMessageRequest request, String content, UserSession session) {
        if (content == null || content.isEmpty()) {
            return;
        }
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
}
