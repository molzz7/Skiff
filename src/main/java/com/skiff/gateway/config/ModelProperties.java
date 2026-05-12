package com.skiff.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置 — 支持多模型列表，application.yml 中可配置
 *
 * <pre>
 * skiff:
 *   model:
 *     base-url: https://api.n1n.ai/v1
 *     api-key: ${N1N_API_KEY:}
 *     name: deepseek-v3-1          # 默认模型
 *     available:                   # 可选模型列表
 *       - deepseek-v3-1
 *       - gpt-4o
 *       - claude-3-5-sonnet
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "skiff.model")
public class ModelProperties {

    /** n1n API 地址 */
    private String baseUrl;

    /** API Key (由 application-local.yml 注入) */
    private String apiKey;

    /** 默认模型名称 */
    private String name;

    /** 温度参数 */
    private Double temperature = 0.7;

    /** 超时时间 */
    private Duration timeout = Duration.ofSeconds(60);

    /** 可选模型列表 */
    private List<String> available = new ArrayList<>();
}
