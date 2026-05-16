package com.skiff.gateway.controller;

import com.skiff.gateway.common.ApiResponse;
import com.skiff.gateway.model.dto.SettingsDto;
import com.skiff.gateway.service.model.ModelRegistry;
import com.skiff.gateway.service.settings.ModelCacheService;
import com.skiff.gateway.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 用户设置接口 — API Key、模型管理
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final ModelCacheService modelCacheService;
    private final ModelRegistry modelRegistry;

    /** 获取当前设置 */
    @GetMapping
    public Mono<ApiResponse<SettingsDto>> getSettings() {
        return Mono.fromCallable(settingsService::get)
                .map(ApiResponse::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 保存设置 */
    @PutMapping
    public Mono<ApiResponse<Void>> saveSettings(@RequestBody SettingsDto dto) {
        return Mono.fromRunnable(() -> {
            settingsService.save(dto);
            modelRegistry.refresh(); // 清空旧模型实例，下次请求重新创建
        }).subscribeOn(Schedulers.boundedElastic())
          .thenReturn(ApiResponse.success(null));
    }

    /** 搜索 n1n 可用模型 */
    @GetMapping("/models/search")
    public Mono<ApiResponse<List<String>>> searchModels(@RequestParam(defaultValue = "") String q) {
        return Mono.fromCallable(() -> modelCacheService.search(q))
                .map(ApiResponse::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 测试 API 连通性 */
    @GetMapping("/test-connection")
    public Mono<ApiResponse<Map<String, Object>>> testConnection() {
        return Mono.fromCallable(() -> {
            List<String> models = modelCacheService.refresh();
            boolean ok = !models.isEmpty();
            return ApiResponse.<Map<String, Object>>success(Map.of(
                    "ok", ok,
                    "modelCount", models.size(),
                    "message", ok ? "连接成功，获取到 " + models.size() + " 个模型" : "连接失败，请检查 API Key 和 URL"
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
