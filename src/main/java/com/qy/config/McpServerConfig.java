package com.qy.config;

import com.qy.service.IMcpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@Slf4j
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider metricsAnalysisToolCallbackProvider(IMcpToolService mcpToolService) {
        log.info("IMcpToolService 实例: {}", mcpToolService.getClass().getName());

        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(mcpToolService)
                .build();
        // 通过反射获取工具名称
        log.info("=== 已注册的工具列表 ===");
        Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .forEach(name -> log.info("注册的工具Registered Tool: {}", name));
        return provider;
    }
} 