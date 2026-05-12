package com.skiff.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import com.skiff.gateway.service.chat.ChatAssistant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Slf4j
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
        JdkHttpClientBuilder httpBuilder = new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(timeout);
        return OpenAiChatModel.builder()
                .httpClientBuilder(httpBuilder)
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(temperature).timeout(timeout).maxRetries(1)
                .build();
    }

    /** 流式聊天模型 */
    @Bean
    public StreamingChatModel streamingChatModel() {
        JdkHttpClientBuilder httpBuilder = new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(timeout);
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(httpBuilder)
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(temperature).timeout(timeout)
                .build();
    }

    /** ChatMemoryProvider — 有 Redis 则持久化，无则内存降级 */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            @Autowired(required = false) StringRedisTemplate redis,
            ObjectMapper mapper) {
        if (redis != null) {
            log.info("ChatMemory mode: Redis (persistent, 30min TTL)");
            RedisChatMemoryStore store = new RedisChatMemoryStore(redis, mapper);
            return memoryId -> MessageWindowChatMemory.builder()
                    .maxMessages(20).chatMemoryStore(store).id(memoryId).build();
        }
        log.info("ChatMemory mode: InMemory (no Redis, lost on restart)");
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(20).chatMemoryStore(store).id(memoryId).build();
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
