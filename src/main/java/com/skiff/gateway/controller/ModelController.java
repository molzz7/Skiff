package com.skiff.gateway.controller;

import com.skiff.gateway.common.ApiResponse;
import com.skiff.gateway.service.model.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 模型管理接口
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelRegistry modelRegistry;

    /** 获取可用模型列表及默认模型 */
    @GetMapping
    public Mono<ApiResponse<Map<String, Object>>> listModels() {
        return Mono.just(ApiResponse.success(Map.of(
                "available", modelRegistry.getAvailableModels(),
                "default", modelRegistry.getDefaultModel()
        )));
    }
}
