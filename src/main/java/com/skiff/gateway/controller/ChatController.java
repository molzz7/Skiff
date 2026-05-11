package com.skiff.gateway.controller;

import com.skiff.gateway.common.ApiResponse;
import com.skiff.gateway.model.dto.ChatRequest;
import com.skiff.gateway.model.dto.ChatResponse;
import com.skiff.gateway.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 非流式对话 — POST /api/chat */
    @PostMapping
    public Mono<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return chatService.chat(request)
                .map(ApiResponse::success);
    }

    /** SSE 流式对话 — POST /api/chat/stream */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }
}
