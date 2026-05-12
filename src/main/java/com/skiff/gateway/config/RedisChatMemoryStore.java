package com.skiff.gateway.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 持久化的 ChatMemoryStore，TTL 可配置
 */
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private static final String KEY_PREFIX = "skiff:memory:";

    public RedisChatMemoryStore(StringRedisTemplate redis, ObjectMapper mapper, Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redis.opsForValue().get(KEY_PREFIX + memoryId);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, String>> raw = mapper.readValue(json, new TypeReference<>() {});
            return raw.stream().map(this::toMessage).toList();
        } catch (Exception e) {
            log.error("Failed to deserialize session {}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            List<Map<String, String>> raw = messages.stream().map(this::toMap).toList();
            redis.opsForValue().set(KEY_PREFIX + memoryId, mapper.writeValueAsString(raw), ttl);
        } catch (Exception e) {
            log.error("Failed to serialize session {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redis.delete(KEY_PREFIX + memoryId);
    }

    private Map<String, String> toMap(ChatMessage msg) {
        if (msg instanceof SystemMessage sm) {
            return Map.of("type", "SYSTEM", "text", sm.text());
        }
        if (msg instanceof UserMessage um) {
            return Map.of("type", "USER", "text", um.singleText());
        }
        if (msg instanceof AiMessage am) {
            return Map.of("type", "AI", "text", am.text() != null ? am.text() : "");
        }
        throw new IllegalArgumentException("Unsupported type: " + msg.type());
    }

    private ChatMessage toMessage(Map<String, String> map) {
        return switch (map.get("type")) {
            case "SYSTEM" -> SystemMessage.from(map.get("text"));
            case "USER"   -> UserMessage.from(map.get("text"));
            case "AI"     -> AiMessage.from(map.get("text"));
            default       -> throw new IllegalArgumentException("Unknown type: " + map.get("type"));
        };
    }
}
