package com.qy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux 配置
 * 
 * 该配置类定义了 WebFlux 相关的配置，包括流式响应处理等
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 增加缓冲区大小，提高流式响应性能
        configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
    }
} 