package com.skiff.gateway.service.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CompressingChatMemory 的 Provider — 按 memoryId 缓存实例，确保 AiServices 和
 * ChatMemoryService 获取同一对象
 */
public class CompressingChatMemoryProvider implements ChatMemoryProvider {

    private final ChatMemoryStore store;
    private final TokenCountEstimator estimator;
    private final int maxTokens;
    private final double compressThreshold;
    private final ChatModel compressModel;
    private final Map<Object, ChatMemory> cache = new ConcurrentHashMap<>();

    public CompressingChatMemoryProvider(ChatMemoryStore store,
                                          TokenCountEstimator estimator,
                                          int maxTokens,
                                          double compressThreshold,
                                          ChatModel compressModel) {
        this.store = store;
        this.estimator = estimator;
        this.maxTokens = maxTokens;
        this.compressThreshold = compressThreshold;
        this.compressModel = compressModel;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return cache.computeIfAbsent(memoryId, id ->
                new CompressingChatMemory(id, store, estimator, maxTokens, compressThreshold, compressModel));
    }
}
