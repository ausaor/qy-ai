package com.qy.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.qy.entity.AiChatSession;
import com.qy.mapper.AiChatSessionMapper;
import com.qy.service.IAiChatSessionService;
import org.springframework.stereotype.Service;

@Service
public class AiChatSessionServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession> implements IAiChatSessionService {
}
