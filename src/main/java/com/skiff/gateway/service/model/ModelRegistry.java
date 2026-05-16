package com.skiff.gateway.service.model;

import com.skiff.gateway.service.chat.ChatAssistant;
import com.skiff.gateway.service.settings.SettingsService;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型注册中心 — 按模型名懒加载 ChatAssistant，设置变更时自动重建
 */
@Slf4j
@Service
public class ModelRegistry {

    private final SettingsService settingsService;
    private final ChatMemoryProvider memoryProvider;
    private volatile Map<String, ChatAssistant> assistants = new ConcurrentHashMap<>();

    public ModelRegistry(SettingsService settingsService, ChatMemoryProvider memoryProvider) {
        this.settingsService = settingsService;
        this.memoryProvider = memoryProvider;
    }

    /** 获取可用模型列表 */
    public List<String> getAvailableModels() {
        return settingsService.getModels();
    }

    /** 获取默认模型名 */
    public String getDefaultModel() {
        List<String> models = settingsService.getModels();
        return models.isEmpty() ? "deepseek-v3-1" : models.get(0);
    }

    /** 解析请求中的模型名 */
    public String resolveModel(String requested) {
        if (requested == null || requested.isBlank()) return getDefaultModel();
        if (getAvailableModels().contains(requested)) return requested;
        log.warn("Requested model '{}' not in available list, using default", requested);
        return getDefaultModel();
    }

    /** 获取 ChatAssistant */
    public ChatAssistant getAssistant(String modelName) {
        return assistants.computeIfAbsent(modelName, this::createAssistant);
    }

    /** 设置变更时清空缓存，下次请求自动重建 */
    public synchronized void refresh() {
        assistants.clear();
        log.info("Model registry cleared ({} assistants will be recreated on demand)", assistants.size());
    }

    private ChatAssistant createAssistant(String modelName) {
        log.info("Creating ChatAssistant for model: {}", modelName);
        String baseUrl = settingsService.getBaseUrl();
        String apiKey = settingsService.getApiKey();
        Duration timeout = Duration.ofSeconds(60);

        ChatModel chatModel = OpenAiChatModel.builder()
                .httpClientBuilder(httpBuilder(timeout))
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(0.7).timeout(timeout).maxRetries(1)
                .build();

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(httpBuilder(timeout))
                .baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .temperature(0.7).timeout(timeout)
                .build();

        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryProvider)
                .build();
    }

    private JdkHttpClientBuilder httpBuilder(Duration timeout) {
        return new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(timeout);
    }
}
