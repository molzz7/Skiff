package com.skiff.gateway.service.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skiff.gateway.model.dto.SettingsDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 设置服务 — 持久化在 Redis（有则用）/ 内存（无 Redis 降级）
 */
@Slf4j
@Service
public class SettingsService {

    private static final String REDIS_KEY = "skiff:settings";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, Object> memory = new ConcurrentHashMap<>();

    // 启动默认值来自 application.yml
    @Value("${skiff.model.base-url}")
    private String defaultBaseUrl;
    @Value("${skiff.model.api-key}")
    private String defaultApiKey;
    @Value("${skiff.model.name}")
    private String defaultModel;
    @Value("#{'${skiff.model.available:}'.split(',')}")
    private List<String> defaultModels;

    private volatile SettingsDto cached;

    public SettingsService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @PostConstruct
    void init() {
        SettingsDto saved = load();
        if (saved != null) {
            cached = saved;
            log.info("Settings loaded from store (apiKey={}... baseUrl={} models={})",
                    mask(saved.getApiKey()), saved.getBaseUrl(), saved.getModels().size());
        } else {
            cached = buildDefaults();
            log.info("Settings initialized from defaults (models={})", cached.getModels());
        }
    }

    public SettingsDto get() {
        return cached;
    }

    public synchronized void save(SettingsDto dto) {
        if (dto.getModels() == null) dto.setModels(Collections.emptyList());
        persist(dto);
        cached = dto;
        log.info("Settings saved (models={})", dto.getModels().size());
    }

    public String getApiKey() { return cached.getApiKey(); }
    public String getBaseUrl() { return cached.getBaseUrl(); }
    public List<String> getModels() { return cached.getModels(); }
    public void setModels(List<String> models) {
        SettingsDto dto = cached;
        dto.setModels(models);
        save(dto);
    }

    private SettingsDto buildDefaults() {
        List<String> models = defaultModels != null ? defaultModels.stream()
                .filter(s -> !s.isBlank()).toList() : List.of(defaultModel);
        if (models.isEmpty()) models = List.of("deepseek-v3-1");
        return SettingsDto.builder()
                .apiKey(defaultApiKey).baseUrl(defaultBaseUrl).models(models).build();
    }

    private void persist(SettingsDto dto) {
        try {
            String json = mapper.writeValueAsString(dto);
            if (redis != null) {
                redis.opsForValue().set(REDIS_KEY, json);
            } else {
                memory.put(REDIS_KEY, json);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize settings", e);
        }
    }

    private SettingsDto load() {
        try {
            String json;
            if (redis != null) {
                json = redis.opsForValue().get(REDIS_KEY);
            } else {
                json = (String) memory.get(REDIS_KEY);
            }
            if (json != null && !json.isEmpty()) {
                return mapper.readValue(json, SettingsDto.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load settings, using defaults", e);
        }
        return null;
    }

    private String mask(String key) {
        if (key == null || key.length() <= 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
