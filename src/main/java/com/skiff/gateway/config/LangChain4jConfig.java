package com.skiff.gateway.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();
    }
}
