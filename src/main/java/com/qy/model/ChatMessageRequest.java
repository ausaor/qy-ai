package com.qy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息请求模型类
 * 
 * 该类表示用户发送的聊天消息请求，包含以下字段：
 * - content: 消息内容
 * - role: 消息角色，通常为"user"
 * - maxTokens: 生成回复的最大令牌数
 * - temperature: 控制输出的随机性，值越高随机性越大
 * - sessionId: 会话ID，用于关联消息和会话
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    private String content;       // 消息内容
    private String role = "user"; // 消息角色，默认为"user"
    private Integer maxTokens;    // 生成回复的最大令牌数
    private Float temperature;    // 控制输出的随机性
    private String sessionId;     // 会话ID
    private String model;         // 模型名称
} 