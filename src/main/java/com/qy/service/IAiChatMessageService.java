package com.qy.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.qy.entity.AiChatMessage;

public interface IAiChatMessageService extends IService<AiChatMessage> {

    Long saveMessage(AiChatMessage  chatMessage);
}
