package com.qy.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.qy.entity.AiChatSession;
import com.qy.exception.GlobalException;
import com.qy.mapper.AiChatSessionMapper;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatSessionService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AiChatSessionServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession> implements IAiChatSessionService {
    @Override
    public void saveAiChatSession(ChatRequest chatRequest) {
        UserSession session = SessionContext.getSession();
        Long userId = session.getUserId();

        // 判断sessionId是否存在
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getId, chatRequest.getSessionId());
        wrapper.eq(AiChatSession::getUserId, userId);

        AiChatSession chatSession = this.getOne(wrapper);
        /*if (ObjectUtil.isNotNull(chatSession) && !chatSession.getType().equals(chatRequest.getChatType())) {
            throw new GlobalException("对话模式已改变，请重新创建一个会话");
        }*/

        if (ObjectUtil.isNull(chatSession)) {
            AiChatSession aiChatSession = new AiChatSession();
            aiChatSession.setId(chatRequest.getSessionId());
            aiChatSession.setUserId(userId);
            aiChatSession.setTitle(chatRequest.getContent().length() > 50 ? chatRequest.getContent().substring(0, 50) : chatRequest.getContent());
            aiChatSession.setCreateTime(LocalDateTime.now());
            aiChatSession.setType(chatRequest.getChatType());
            save(aiChatSession);
        }
    }
}
