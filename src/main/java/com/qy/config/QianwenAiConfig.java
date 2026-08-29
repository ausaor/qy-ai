package com.qy.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通义千问 AI 配置类
 *
 * 千问通过阿里云百炼（DashScope）的 OpenAI 兼容接口（compatible-mode）接入，
 * 因此同样基于 Spring AI 2.0 的 OpenAiChatModel + openai-java SDK 实现。
 */
@Configuration
public class QianwenAiConfig {

    @Value("${spring.ai.qianwen.api-key}")
    private String apiKey;  // DashScope API 密钥

    @Value("${spring.ai.qianwen.base-url}")
    private String apiUrl;  // DashScope OpenAI 兼容接口地址

    @Value("${spring.ai.qianwen.chat.options.model}")
    private String model;  // 模型名称，如 qwen-plus

    @Value("${spring.ai.qianwen.chat.options.temperature}")
    private double temperature;  // 温度参数

    @Value("${spring.ai.qianwen.chat.options.max-tokens}")
    private int maxTokens;  // 最大标记数

    /**
     * 创建千问的 OpenAI 兼容客户端（指向 DashScope compatible-mode 接口）
     */
    @Bean
    public OpenAIClient qianwenOpenAiClient() {
        return OpenAIOkHttpClient.builder()
                .baseUrl(apiUrl)
                .apiKey(apiKey)
                .build();
    }

    /**
     * 配置千问聊天选项
     */
    @Bean
    public OpenAiChatOptions qianwenChatOptions() {
        return OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * 创建千问 ChatModel 实例
     * 注意：必须同时设置同步客户端和异步客户端（openAiClientAsync），
     * 否则 build() 会回退到 OpenAiSetup 按标准配置属性创建客户端，
     * 读不到自定义的千问配置而报“At least one credential source must be specified”
     */
    @Bean
    public OpenAiChatModel qianwenChatModel(OpenAIClient qianwenOpenAiClient, OpenAiChatOptions qianwenChatOptions) {
        return OpenAiChatModel.builder()
                .openAiClient(qianwenOpenAiClient)
                .openAiClientAsync(qianwenOpenAiClient.async())
                .options(qianwenChatOptions)
                .build();
    }
}
