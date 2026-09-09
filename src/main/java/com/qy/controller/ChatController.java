package com.qy.controller;

import cn.hutool.core.util.StrUtil;
import com.qy.enums.ChatRole;
import com.qy.enums.ChatType;
import com.qy.exception.GlobalException;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;

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
     * @return 流式响应
     */
    @GetMapping(value = "/stream/msg/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式获取消息回复", description = "发送消息并流式返回回复")
    public Flux<ChatResponse> streamMessage(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam String model) {

        log.info("流式发送消息到会话: sessionId = {}, content = {}", sessionId, content);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        request.setRole(ChatRole.USER.getRole());
        request.setModel(model);
        request.setSessionId(sessionId);

        return sseService.streamMessage(request);
    }

    /**
     * 流式返回聊天回复（支持多模态文件上传）
     * 该端点接收聊天消息和可选的附件（图片/音频/视频），发送到AI模型，
     * 然后将回复作为服务器发送事件（SSE）流式返回给客户端
     *
     * @param sessionId 会话ID
     * @param content   消息内容
     * @param model     模型类别
     * @param files     附件文件（可选，支持图片/音频/视频）
     * @return 流式响应
     */
    @PostMapping(value = "/flux/msg/{sessionId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式获取消息回复（支持多模态）", description = "发送消息并流式返回回复，支持上传图片/音频/视频文件")
    public Flux<ServerSentEvent<String>> streamChat(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam String model,
            @RequestParam String chatType,
            @RequestPart(required = false) List<MultipartFile> files) {

        log.info("流式发送消息到会话: sessionId = {}, content = {}, files = {}",
                sessionId, content, files != null ? files.size() : 0);
        if (!StrUtil.equals(chatType, ChatType.CHAT.getCode())) {
            throw new GlobalException("Invalid chat type: " + chatType);
        }

        ChatRequest request = new ChatRequest();
        request.setContent(content);
        request.setModel(model);
        request.setRole(ChatRole.USER.getRole());
        request.setSessionId(sessionId);
        request.setFiles(files);
        request.setChatType(chatType);

        return sseService.streamChat(request);
    }

    @GetMapping(value = "/sse/msg/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE获取消息回复", description = "发送消息并SSE返回回复")
    public SseEmitter sseChat(
            @PathVariable Long sessionId,
            @RequestParam String content,
            @RequestParam String model) {

        log.info("SSE发送消息到会话: sessionId = {}, content = {}", sessionId, content);

        ChatRequest request = new ChatRequest();
        request.setSessionId(sessionId);
        request.setContent(content);
        request.setModel(model);
        request.setRole(ChatRole.USER.getRole());

        return sseService.sseChat(request);
    }
}
