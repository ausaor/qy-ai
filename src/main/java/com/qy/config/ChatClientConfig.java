package com.qy.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    /**
     * 配置ChatClient，注册系统指令和工具函数
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider metricsAnalysisToolCallbackProvider) {
        return builder
                .defaultSystem("你是一个智能助手，能够像真实对话一样理解用户的需求。" +
                        "在对话中，你会：\n" +
                        "1. 理解上下文，当用户提到新的查询对象时，自动关联之前的查询条件。\n" +
                        "2. 灵活理解各种日期表达方式，如'2025年7月'、'上个月'、'第一季度'等，并将其转换为yyyy-MM-dd格式的开始日期和结束日期\n" +
                        "3. 根据对话历史，智能推测用户的查询意图。\n" +
                        "4. 用简洁专业的语言回复，将分析结果整理为易读的格式。\n" +
                        "5. 在需要时主动询问用户以获取更准确的信息\n" +
                        "6. 工具调用规则：\n" +
                        "    - 当用户查询xx城市xx日期的天气情况时（例如'查询深圳市2025年7月6号天气情况'），必须调用getCityWeather工具\n")
                // 注册工具方法
                .defaultTools(metricsAnalysisToolCallbackProvider)
                .build();
    }
} 