package com.skiff.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import com.skiff.gateway.service.chat.ChatAssistant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${skiff.model.base-url}")
    private String baseUrl;
    @Value("${skiff.model.api-key}")
    private String apiKey;
    @Value("${skiff.model.name}")
    private String modelName;
    @Value("${skiff.model.temperature}")
    private Double temperature;
    @Value("${skiff.model.timeout}")
    private Duration timeout;

    /** 同步聊天模型 */
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(temperature).timeout(timeout)
                .build();
    }

    /** 流式聊天模型 */
    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(temperature).timeout(timeout)
                .build();
    }

    /** Redis 持久化 ChatMemoryProvider — 有 Redis 时优先使用 */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public ChatMemoryProvider chatMemoryProvider(StringRedisTemplate redis, ObjectMapper mapper) {
        RedisChatMemoryStore store = new RedisChatMemoryStore(redis, mapper);
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryStore(store)
                .id(memoryId)
                .build();
    }

    /** 内存降级 ChatMemoryProvider — 无 Redis 时自动切换 */
    @Bean
    @ConditionalOnMissingBean(ChatMemoryProvider.class)
    public ChatMemoryProvider inMemoryChatMemoryProvider() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryStore(store)
                .id(memoryId)
                .build();
    }

    /** AiServices 自动生成的 ChatAssistant 实现 */
    @Bean
    public ChatAssistant chatAssistant(ChatModel chatModel,
                                        StreamingChatModel streamingChatModel,
                                        ChatMemoryProvider memoryProvider) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryProvider)
                .build();
    }
}
