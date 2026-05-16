package com.skiff.gateway.service.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * n1n 模型列表缓存服务
 * <p>
 * 首次请求从 n1n API 拉取并缓存 (Redis 1h / 内存)，后续模糊搜索命中缓存。
 */
@Slf4j
@Service
public class ModelCacheService {

    private static final String REDIS_KEY = "skiff:models:n1n";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SettingsService settingsService;
    private final ConcurrentMap<String, String> memory = new ConcurrentHashMap<>();
    private final RestClient restClient;

    public ModelCacheService(StringRedisTemplate redis, ObjectMapper mapper,
                              SettingsService settingsService) {
        this.redis = redis;
        this.mapper = mapper;
        this.settingsService = settingsService;
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()))
                .build();
    }

    /**
     * 模糊搜索模型名，返回匹配的前 20 条
     */
    public List<String> search(String keyword) {
        List<String> all = getCachedModels();
        if (all.isEmpty()) return Collections.emptyList();

        String kw = keyword.toLowerCase();
        return all.stream()
                .filter(m -> m.toLowerCase().contains(kw))
                .limit(20)
                .toList();
    }

    /**
     * 获取缓存的全部模型列表，缓存未命中则从 n1n 拉取
     */
    private synchronized List<String> getCachedModels() {
        List<String> cached = loadFromCache();
        if (!cached.isEmpty()) return cached;

        List<String> fetched = fetchFromApi();
        if (!fetched.isEmpty()) {
            storeToCache(fetched);
        }
        return fetched;
    }

    /**
     * 强制刷新缓存
     */
    public List<String> refresh() {
        List<String> fetched = fetchFromApi();
        if (!fetched.isEmpty()) {
            storeToCache(fetched);
            log.info("Model cache refreshed ({} models)", fetched.size());
        }
        return fetched;
    }

    private List<String> fetchFromApi() {
        String baseUrl = settingsService.getBaseUrl();
        String apiKey = settingsService.getApiKey();
        log.debug("Fetching models from {}", baseUrl);

        try {
            String resp = restClient.get()
                    .uri(baseUrl + "/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(resp);
            List<String> models = new ArrayList<>();
            for (JsonNode node : root.path("data")) {
                String id = node.path("id").asText();
                if (!id.isBlank()) models.add(id);
            }
            log.info("Fetched {} models from API", models.size());
            return models;
        } catch (Exception e) {
            log.warn("Failed to fetch models from API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> loadFromCache() {
        try {
            String json;
            if (redis != null) {
                json = redis.opsForValue().get(REDIS_KEY);
            } else {
                json = (String) memory.get(REDIS_KEY);
            }
            if (json != null) {
                return mapper.readValue(json,
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            log.warn("Failed to load model cache", e);
        }
        return Collections.emptyList();
    }

    private void storeToCache(List<String> models) {
        try {
            String json = mapper.writeValueAsString(models);
            if (redis != null) {
                redis.opsForValue().set(REDIS_KEY, json, CACHE_TTL);
            } else {
                memory.put(REDIS_KEY, json);
            }
        } catch (Exception e) {
            log.warn("Failed to store model cache", e);
        }
    }
}
