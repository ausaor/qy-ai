package com.qy.controller;


import cn.hutool.core.util.StrUtil;
import com.qy.enums.ChatType;
import com.qy.exception.GlobalException;
import com.qy.model.ChatRequest;
import com.qy.service.ISseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 多 Agent 系统统一入口
 * 所有请求通过 Router Agent 自动分发到专业 Agent
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ISseService sseService;

    /**
     * 智能助手对话接口
     * Router Agent 自动识别意图并路由到对应专业 Agent
     */
    @RequestMapping(value = "/chat/{sessionId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agentChat(@PathVariable Long sessionId,
                                                   @RequestParam String content,
                                                   @RequestParam(defaultValue = "user") String role,
                                                   @RequestParam String model,
                                                   @RequestParam String chatType,
                                                   @RequestPart(required = false) List<MultipartFile> files) {

        log.info("流式发送消息到会话: sessionId = {}, content = {}, files = {}",
                sessionId, content, files != null ? files.size() : 0);
        if (!StrUtil.equals(chatType, ChatType.AGENT.getCode())) {
            throw new GlobalException("Invalid chat type: " + chatType);
        }

        ChatRequest request = new ChatRequest();
        request.setContent(content);
        request.setModel(model);
        request.setRole(role);
        request.setSessionId(sessionId);
        request.setFiles(files);
        request.setChatType(chatType);

        return sseService.streamAgentChat(request);
    }
}
