package com.qy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisConfig extends CachingConfigurerSupport {

    @Resource
    private RedisConnectionFactory factory;

    @Primary
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // 设置值（value）的序列化采用 jacksonJsonRedisSerializer
        redisTemplate.setValueSerializer(genericJacksonJsonRedisSerializer());
        redisTemplate.setHashValueSerializer(genericJacksonJsonRedisSerializer());
        // 设置键（key）的序列化采用StringRedisSerializer。
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }



    @Bean
    public CacheManager cacheManager() {
        // 设置redis缓存管理器
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(600))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(genericJacksonJsonRedisSerializer()));
        return RedisCacheManager.builder(factory).cacheDefaults(cacheConfiguration).build();
    }


    /**
     * Jackson 3 的通用 JSON 序列化器（Spring Boot 4 默认 Jackson 3）
     * - 开启默认类型信息，反序列化时可以还原对象类型
     * - Jackson 3 内置 JSR-310 日期时间支持，无需再注册 JavaTimeModule
     */
    @Bean
    public GenericJacksonJsonRedisSerializer genericJacksonJsonRedisSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                // 类型信息写入，允许还原 Object 及其子类
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build())
                .customize(builder -> builder
                        // 忽略无效字段
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        // 忽略空值
                        .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL)))
                .build();
    }
}
