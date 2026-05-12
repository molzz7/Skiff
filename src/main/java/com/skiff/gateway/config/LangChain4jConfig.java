package com.skiff.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skiff.gateway.service.memory.CompressingChatMemoryProvider;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * LangChain4j 核心配置
 * <p>
 * - ChatMemoryProvider → CompressingChatMemoryProvider（自动摘要压缩）
 * - CompressorModel → 独立轻量模型，专门用于对话摘要
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${skiff.model.base-url}")
    private String baseUrl;
    @Value("${skiff.model.api-key}")
    private String apiKey;
    @Value("${skiff.memory.max-tokens:8000}")
    private int maxTokens;
    @Value("${skiff.memory.compress-threshold:0.8}")
    private double compressThreshold;
    @Value("${skiff.memory.ttl-hours:72}")
    private long ttlHours;

    /** Token 估算器 */
    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new OpenAiTokenCountEstimator("gpt-4o");
    }

    /** 摘要压缩专用轻量模型 */
    @Bean
    public ChatModel compressorModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.3)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .build();
    }

    /** ChatMemoryProvider — Redis 持久化 + TokenWindow + 自动摘要压缩 */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            @Autowired(required = false) StringRedisTemplate redis,
            ObjectMapper mapper,
            TokenCountEstimator estimator,
            ChatModel compressorModel) {

        ChatMemoryStore store;
        if (redis != null) {
            log.info("ChatMemory: Redis (TTL={}h) + TokenWindow({} tokens) + Auto-compress({:.0%})",
                    ttlHours, maxTokens, compressThreshold);
            store = new RedisChatMemoryStore(redis, mapper, Duration.ofHours(ttlHours));
        } else {
            log.info("ChatMemory: InMemory + TokenWindow({} tokens) + Auto-compress({:.0%})",
                    maxTokens, compressThreshold);
            store = new InMemoryChatMemoryStore();
        }

        return new CompressingChatMemoryProvider(store, estimator, maxTokens, compressThreshold, compressorModel);
    }
}
