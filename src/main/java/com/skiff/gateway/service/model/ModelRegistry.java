package com.skiff.gateway.service.model;

import com.skiff.gateway.config.ModelProperties;
import com.skiff.gateway.service.chat.ChatAssistant;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型注册中心 — 按模型名懒加载 ChatAssistant 实例
 * <p>
 * 每个模型名对应独立的 ChatModel/StreamingChatModel 和 ChatAssistant，
 * 但 ChatMemoryProvider 共享，保证切换模型后对话历史不丢失。
 */
@Slf4j
@Service
public class ModelRegistry {

    private final ModelProperties props;
    private final ChatMemoryProvider memoryProvider;
    private final Map<String, ChatAssistant> assistants = new ConcurrentHashMap<>();

    public ModelRegistry(ModelProperties props, ChatMemoryProvider memoryProvider) {
        this.props = props;
        this.memoryProvider = memoryProvider;
    }

    /** 获取可用模型列表 */
    public List<String> getAvailableModels() {
        return props.getAvailable();
    }

    /** 获取默认模型名 */
    public String getDefaultModel() {
        return props.getName();
    }

    /** 解析请求中的模型名，未指定则返回默认值 */
    public String resolveModel(String requested) {
        if (requested == null || requested.isBlank()) {
            return props.getName();
        }
        if (props.getAvailable().contains(requested)) {
            return requested;
        }
        log.warn("Requested model '{}' not in available list, falling back to default '{}'", requested, props.getName());
        return props.getName();
    }

    /** 按模型名获取 ChatAssistant（懒加载 + 缓存） */
    public ChatAssistant getAssistant(String modelName) {
        return assistants.computeIfAbsent(modelName, this::createAssistant);
    }

    private ChatAssistant createAssistant(String modelName) {
        log.info("Creating ChatAssistant for model: {}", modelName);

        ChatModel chatModel = OpenAiChatModel.builder()
                .httpClientBuilder(createHttpBuilder())
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .modelName(modelName)
                .temperature(props.getTemperature())
                .timeout(props.getTimeout())
                .maxRetries(1)
                .build();

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(createHttpBuilder())
                .baseUrl(props.getBaseUrl())
                .apiKey(props.getApiKey())
                .modelName(modelName)
                .temperature(props.getTemperature())
                .timeout(props.getTimeout())
                .build();

        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryProvider)
                .build();
    }

    private JdkHttpClientBuilder createHttpBuilder() {
        return new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(props.getTimeout());
    }
}
