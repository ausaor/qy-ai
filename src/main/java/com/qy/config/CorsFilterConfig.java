package com.qy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS过滤器配置
 * 提供过滤器级别的跨域资源共享支持
 */
@Configuration
public class CorsFilterConfig {

    /**
     * 创建CORS过滤器
     * 
     * @return CORS过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许cookie等凭证
        config.setAllowCredentials(true);
        
        // 允许所有来源
        config.addAllowedOriginPattern("*");
        
        // 允许所有头信息
        config.addAllowedHeader("*");
        
        // 允许所有方法
        config.addAllowedMethod("*");
        
        // 暴露响应头
        config.addExposedHeader("Authorization");
        
        // 预检请求的有效期
        config.setMaxAge(3600L);
        
        // 对所有路径应用配置
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
} 