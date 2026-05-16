package com.skiff.gateway.controller;

import com.skiff.gateway.common.ApiResponse;
import com.skiff.gateway.model.dto.DocumentInfo;
import com.skiff.gateway.service.rag.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口 — 上传 / 列表 / 单删 / 清空
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentService documentService;

    /** 上传文档 */
    @PostMapping("/upload")
    public Mono<ApiResponse<Map<String, Object>>> upload(@RequestPart("file") FilePart file) {
        return Mono.fromCallable(() -> {
            Path tempFile = Files.createTempFile("skiff-upload-", ".tmp");
            file.transferTo(tempFile).block();
            try {
                String docId = documentService.processDocument(tempFile, file.filename());
                Map<String, Object> result = new HashMap<>();
                result.put("documentId", docId);
                result.put("fileName", file.filename());
                result.put("message", "上传成功，文档ID: " + docId);
                return ApiResponse.success(result);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 获取文档列表 */
    @GetMapping
    public Mono<ApiResponse<List<DocumentInfo>>> listDocuments() {
        return Mono.fromCallable(() ->
                ApiResponse.success(documentService.listDocuments())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除指定文档 */
    @DeleteMapping("/{documentId}")
    public Mono<ApiResponse<Void>> deleteDocument(@PathVariable String documentId) {
        return Mono.fromRunnable(() -> documentService.deleteDocument(documentId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ApiResponse.success(null));
    }

    /** 清空知识库 */
    @DeleteMapping
    public Mono<ApiResponse<Void>> clear() {
        return Mono.fromRunnable(documentService::clearStore)
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ApiResponse.success(null));
    }
}
