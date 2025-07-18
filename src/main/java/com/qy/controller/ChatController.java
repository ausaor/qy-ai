package com.qy.controller;

import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.ISseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Slf4j
@RequestMapping("/chat")
@RestController
public class ChatController {
    @Resource
    private ISseService sseService;

    /**
     * 流式返回聊天回复
     * 该端点接收聊天消息，发送到DeepSeek AI模型，然后将回复作为服务器发送事件（SSE）流式返回给客户端
     *
     * @param sessionId   会话ID
     * @param content     消息内容
     * @param role        消息角色（默认：user）
     * @param maxTokens   生成回复的最大令牌数
     * @param temperature 控制输出的随机性
     * @return 流式响应
     */
    @GetMapping(value = "/stream/msg/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式获取消息回复", description = "发送消息并流式返回回复")
    public Flux<ChatResponse> streamMessage(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam(defaultValue = "user") String role,
            @RequestParam String model,
            @RequestParam(required = false) Integer maxTokens,
            @RequestParam(required = false) Float temperature) {

        log.info("流式发送消息到会话: sessionId = {}, content = {}", sessionId, content);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        request.setRole(role);
        request.setMaxTokens(maxTokens);
        request.setTemperature(temperature);
        request.setModel(model);

        return sseService.streamMessage(sessionId, request);
    }

    /**
     * 流式返回聊天回复
     * 该端点接收聊天消息，发送到DeepSeek AI模型，然后将回复作为服务器发送事件（SSE）流式返回给客户端
     *
     * @param sessionId   会话ID
     * @param content     消息内容
     * @param role        消息角色（默认：user）
     * @return 流式响应
     */
    @GetMapping(value = "/flux/msg/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式获取消息回复", description = "发送消息并流式返回回复")
    public Flux<String> streamChat(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam(defaultValue = "user") String role,
            @RequestParam String model) {

        log.info("流式发送消息到会话: sessionId = {}, content = {}", sessionId, content);

        ChatRequest request = new ChatRequest();
        request.setContent(content);
        request.setModel(model);
        request.setRole(role);

        return sseService.streamChat(sessionId, request);
    }

    @GetMapping(value = "/sse/msg/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE获取消息回复", description = "发送消息并SSE返回回复")
    public SseEmitter sseChat(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam(defaultValue = "user") String role,
            @RequestParam String model) {

        log.info("SSE发送消息到会话: sessionId = {}, content = {}", sessionId, content);

        ChatRequest request = new ChatRequest();
        request.setContent(content);
        request.setModel(model);
        request.setRole(role);

        return sseService.sseChat(request);
    }

    @GetMapping(value = "/mcp/msg/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "MCP流式获取消息回复", description = "发送消息并流式返回回复")
    public Flux<ServerSentEvent<String>> mcpChat(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam(defaultValue = "user") String role,
            @RequestParam String model) {
        log.info("收到消息请求内容: {}", content);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setModel(model);
        request.setContent(content);
        request.setRole(role);

        return sseService.mcpChat(sessionId, request);
    }
}
