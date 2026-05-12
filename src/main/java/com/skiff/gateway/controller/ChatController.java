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

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMemoryService memoryService;

    /** 非流式对话 */
    @PostMapping
    public Mono<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return chatService.chat(request)
                .map(ApiResponse::success);
    }

    /** SSE 流式对话 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }

    /** 获取会话 token 统计 */
    @GetMapping("/session/{conversationId}/tokens")
    public Mono<ApiResponse<TokenStats>> getTokens(@PathVariable String conversationId) {
        return Mono.fromCallable(() -> memoryService.getTokenStats(conversationId))
                .map(ApiResponse::success)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
