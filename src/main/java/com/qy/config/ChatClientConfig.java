package com.qy.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    /**
     * 配置 DeepSeek ChatClient，注册系统指令和工具函数
     */
    @Bean
    public ChatClient deepseekChatClient(
            @Qualifier("deepseekChatModel") OpenAiChatModel deepseekChatModel,
            ToolCallbackProvider metricsAnalysisToolCallbackProvider,
            ChatMemory chatMemory,
            @Value("${spring.ai.assistant.system-prompt}") String systemPrompt) {
        return ChatClient.builder(deepseekChatModel)
                .defaultSystem(systemPrompt)
                // 注册工具方法
                .defaultToolCallbacks(metricsAnalysisToolCallbackProvider)
                .defaultAdvisors(new SimpleLoggerAdvisor(), // 日志增强器
                        MessageChatMemoryAdvisor.builder(chatMemory).build()) // 对话记忆增强器
                .build();
    }

    /**
     * 配置通义千问 ChatClient，注册系统指令和工具函数
     */
    @Bean
    public ChatClient qianwenChatClient(
            @Qualifier("qianwenChatModel") OpenAiChatModel qianwenChatModel,
            ToolCallbackProvider metricsAnalysisToolCallbackProvider,
            ChatMemory chatMemory,
            @Value("${spring.ai.assistant.system-prompt}") String systemPrompt) {
        return ChatClient.builder(qianwenChatModel)
                .defaultSystem(systemPrompt)
                // 注册工具方法
                .defaultToolCallbacks(metricsAnalysisToolCallbackProvider)
                .defaultAdvisors(new SimpleLoggerAdvisor(), // 日志增强器
                        MessageChatMemoryAdvisor.builder(chatMemory).build()) // 对话记忆增强器
                .build();
    }
}
