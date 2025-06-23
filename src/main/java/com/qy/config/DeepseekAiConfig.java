package com.qy.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * DeepSeek AI 配置类
 * 
 * 该配置类负责设置与 DeepSeek AI 模型交互所需的组件和参数
 * 由于 DeepSeek API 与 OpenAI API 结构相似，我们使用 Spring AI 的 OpenAI 客户端作为基础
 * 在实际生产环境中，可能需要创建专门的 DeepSeek 客户端
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
     * 创建 RestTemplate 用于 HTTP 请求
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 创建 DeepSeek API 客户端
     * 注意：这里使用 OpenAiApi 作为基础，因为 DeepSeek 通常遵循类似的 API 模式
     */
    @Bean
    public OpenAiApi deepseekApi() {
        // 在实际实现中，可能需要为 DeepSeek 创建自定义客户端
        return OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(apiUrl)
                .build();
    }

    /**
     * 配置 DeepSeek 聊天选项
     * 设置模型名称、温度和最大令牌数等参数
     */
    @Bean
    public OpenAiChatOptions chatOptions() {
        return OpenAiChatOptions.builder()
                .model(model)           // 使用从配置文件读取的模型名称
                .temperature(temperature) // 使用从配置文件读取的温度参数
                .maxTokens(maxTokens)     // 使用从配置文件读取的最大标记数
                .build();
    }
    
    /**
     * 创建 ChatModel 实例
     */
    @Bean
    public ChatModel chatModel(OpenAiApi deepseekApi, OpenAiChatOptions chatOptions) {
        return OpenAiChatModel.builder()
                .openAiApi(deepseekApi)
                .defaultOptions(chatOptions)
                .build();
    }
}
