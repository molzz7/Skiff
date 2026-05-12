package com.skiff.gateway.service.chat;

import com.skiff.gateway.model.dto.ChatRequest;
import com.skiff.gateway.model.dto.ChatResponse;
import com.skiff.gateway.service.model.ModelRegistry;
import dev.langchain4j.data.message.AiMessage;
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
 * 聊天编排 — ModelRegistry 路由 + Token 统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ModelRegistry modelRegistry;
    private final ChatMemoryService memoryService;

    /** 非流式对话 */
    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            String model = modelRegistry.resolveModel(request.getModel());
            String conversationId = resolveConversationId(request);
            log.info("Chat model={} session={}", model, conversationId);

            ChatAssistant assistant = modelRegistry.getAssistant(model);
            String result = assistant.chat(request.getMessage(), conversationId);

            // 计算本条消息 token 和会话总量
            int msgTokens = memoryService.estimateTokens(
                    AiMessage.from(result));
            var stats = memoryService.getTokenStats(conversationId);

            return ChatResponse.builder()
                    .content(result)
                    .model(model)
                    .conversationId(conversationId)
                    .tokenCount(msgTokens)
                    .totalTokens(stats.getTotalTokens())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** SSE 流式对话 */
    public Flux<String> streamChat(ChatRequest request) {
        String model = modelRegistry.resolveModel(request.getModel());
        String conversationId = resolveConversationId(request);
        log.info("Stream model={} session={}", model, conversationId);

        ChatAssistant assistant = modelRegistry.getAssistant(model);
        TokenStream tokenStream = assistant.stream(request.getMessage(), conversationId);

        return Flux.create((FluxSink<String> sink) ->
                tokenStream.onPartialResponse(sink::next)
                           .onCompleteResponse(resp -> sink.complete())
                           .onError(sink::error)
                           .start());
    }

    /** 计算单条消息 token 数 */
    public int estimateTokens(String text) {
        return memoryService.estimateTokens(AiMessage.from(text));
    }

    private String resolveConversationId(ChatRequest request) {
        return StringUtils.hasText(request.getConversationId())
                ? request.getConversationId()
                : UUID.randomUUID().toString().substring(0, 8);
    }
}
