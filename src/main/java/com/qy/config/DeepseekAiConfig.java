package com.qy.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek AI 配置类
 *
 * 该配置类负责设置与 DeepSeek AI 模型交互所需的组件和参数。
 * Spring AI 2.0 移除了自研的 OpenAiApi，改为直接使用 openai-java SDK 的 OpenAIClient，
 * 因此这里基于 OpenAIOkHttpClient 构建指向 DeepSeek 的客户端。
 */
@Configuration
public class DeepseekAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;  // DeepSeek API 密钥

    @Value("${spring.ai.openai.base-url}")
    private String apiUrl;  // DeepSeek API 地址

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;  // 模型名称，从配置文件中读取

    @Value("${spring.ai.openai.chat.options.temperature}")
    private double temperature;  // 温度参数，从配置文件中读取

    @Value("${spring.ai.openai.chat.options.max-tokens}")
    private int maxTokens;  // 最大标记数，从配置文件中读取

    /**
     * 创建 DeepSeek 的 OpenAI 兼容客户端（openai-java SDK）
     */
    @Bean
    public OpenAIClient deepseekOpenAiClient() {
        return OpenAIOkHttpClient.builder()
                .baseUrl(apiUrl)
                .apiKey(apiKey)
                .build();
    }

    /**
     * 配置 DeepSeek 聊天选项
     * 设置模型名称、温度和最大令牌数等参数
     */
    @Bean
    public OpenAiChatOptions deepseekChatOptions() {
        return OpenAiChatOptions.builder()
                .model(model)             // 使用从配置文件读取的模型名称
                .temperature(temperature) // 使用从配置文件读取的温度参数
                .maxTokens(maxTokens)     // 使用从配置文件读取的最大标记数
                .build();
    }

    /**
     * 创建 DeepSeek ChatModel 实例（Spring AI 2.0 的 Builder API）
     * 注意：必须同时设置同步客户端和异步客户端（openAiClientAsync），
     * 否则 build() 会回退到 OpenAiSetup 按标准配置属性创建客户端，
     * 读不到自定义的 DeepSeek 配置而报“At least one credential source must be specified”
     */
    @Bean
    public OpenAiChatModel deepseekChatModel(OpenAIClient deepseekOpenAiClient, OpenAiChatOptions deepseekChatOptions) {
        return OpenAiChatModel.builder()
                .openAiClient(deepseekOpenAiClient)
                .openAiClientAsync(deepseekOpenAiClient.async())
                .options(deepseekChatOptions)
                .build();
    }
}
