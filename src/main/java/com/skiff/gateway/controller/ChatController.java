package com.skiff.gateway.controller;

import com.skiff.gateway.common.ApiResponse;
import com.skiff.gateway.model.dto.ChatRequest;
import com.skiff.gateway.model.dto.ChatResponse;
import com.skiff.gateway.model.dto.TokenStats;
import com.skiff.gateway.service.chat.ChatMemoryService;
import com.skiff.gateway.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMemoryService memoryService;

    @PostMapping
    public Mono<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return chatService.chat(request).map(ApiResponse::success);
    }

    /** RAG 知识库增强对话 */
    @PostMapping("/rag")
    public Mono<ApiResponse<ChatResponse>> ragChat(@RequestBody ChatRequest request) {
        return chatService.ragChat(request).map(ApiResponse::success);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }

    @GetMapping("/session/{conversationId}/tokens")
    public Mono<ApiResponse<TokenStats>> getTokens(@PathVariable String conversationId) {
        return Mono.fromCallable(() -> memoryService.getTokenStats(conversationId))
                .map(ApiResponse::success).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/session/{conversationId}")
    public Mono<ApiResponse<Void>> deleteSession(@PathVariable String conversationId) {
        return Mono.fromRunnable(() -> memoryService.clearSession(conversationId))
                .subscribeOn(Schedulers.boundedElastic()).thenReturn(ApiResponse.success(null));
    }
}
