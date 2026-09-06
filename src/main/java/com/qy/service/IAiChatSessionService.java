package com.qy.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.qy.entity.AiChatSession;
import com.qy.model.ChatRequest;

public interface IAiChatSessionService extends IService<AiChatSession> {
    /**
     * 保存会话
     *
     * @param chatRequest 会话请求
     */
    void saveAiChatSession(ChatRequest chatRequest);
}
