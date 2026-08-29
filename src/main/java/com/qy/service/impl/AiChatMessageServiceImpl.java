package com.qy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.qy.entity.AiChatMessage;
import com.qy.entity.AiChatSession;
import com.qy.mapper.AiChatMessageMapper;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IAiChatSessionService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage> implements IAiChatMessageService {
    @Resource
    private IAiChatSessionService aiChatSessionService;

    @Transactional
    @Override
    public Long saveMessage(AiChatMessage  chatMessage) {
        UserSession session = SessionContext.getSession();
        Long userId = session.getUserId();

        // 判断sessionId是否存在
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getId, chatMessage.getSessionId());
        wrapper.eq(AiChatSession::getUserId, userId);
        if (!aiChatSessionService.exists(wrapper)) {
            AiChatSession aiChatSession = new AiChatSession();
            aiChatSession.setId(chatMessage.getSessionId());
            aiChatSession.setUserId(userId);
            aiChatSession.setTitle(chatMessage.getContent().length() > 50 ? chatMessage.getContent().substring(0, 50) : chatMessage.getContent());
            aiChatSession.setCreateTime(LocalDateTime.now());
            aiChatSessionService.save(aiChatSession);
        }

        chatMessage.setUserId(userId);
        chatMessage.setCreateTime(LocalDateTime.now());
        this.save(chatMessage);

        return chatMessage.getSessionId();
    }
}
