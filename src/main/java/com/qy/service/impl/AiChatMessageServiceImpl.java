package com.qy.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.qy.entity.AiChatMessage;
import com.qy.mapper.AiChatMessageMapper;
import com.qy.service.IAiChatMessageService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage> implements IAiChatMessageService {

    @Transactional
    @Override
    public Long saveMessage(AiChatMessage  chatMessage) {
        UserSession session = SessionContext.getSession();
        Long userId = session.getUserId();

        chatMessage.setUserId(userId);
        chatMessage.setCreateTime(LocalDateTime.now());
        this.save(chatMessage);

        return chatMessage.getSessionId();
    }
}
