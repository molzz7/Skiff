package com.skiff.gateway.service.chat;

import com.skiff.gateway.model.dto.ChatRequest;
import com.skiff.gateway.model.dto.ChatResponse;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * 聊天编排服务 — 轻量层，核心逻辑全部委托给 LangChain4j AiServices
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatAssistant assistant;

    /** 非流式对话 */
    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            String conversationId = resolveConversationId(request);
            log.debug("Chat, session={}", conversationId);
            String result = assistant.chat(request.getMessage(), conversationId);
            return ChatResponse.builder()
                    .content(result)
                    .conversationId(conversationId)
                    .model("default")
                    .timestamp(System.currentTimeMillis())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** SSE 流式对话 — TokenStream → Flux 桥接 */
    public Flux<String> streamChat(ChatRequest request) {
        String conversationId = resolveConversationId(request);
        log.debug("Stream, session={}", conversationId);
        TokenStream tokenStream = assistant.stream(request.getMessage(), conversationId);
        return Flux.create((FluxSink<String> sink) ->
                tokenStream.onPartialResponse(sink::next)
                           .onCompleteResponse(resp -> sink.complete())
                           .onError(sink::error)
                           .start());
    }

    private String resolveConversationId(ChatRequest request) {
        return StringUtils.hasText(request.getConversationId())
                ? request.getConversationId()
                : UUID.randomUUID().toString().substring(0, 8);
    }
}
