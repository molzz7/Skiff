package com.skiff.gateway.service.chat;

import com.skiff.gateway.model.dto.ChatRequest;
import com.skiff.gateway.model.dto.ChatResponse;
import com.skiff.gateway.service.model.ModelRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天编排 — ModelRegistry 路由 + Token 统计 + RAG
 */
@Slf4j
@Service
public class ChatService {

    private final ModelRegistry modelRegistry;
    private final ChatMemoryService memoryService;
    private final ContentRetriever contentRetriever;

    public ChatService(ModelRegistry modelRegistry, ChatMemoryService memoryService,
                        ContentRetriever contentRetriever) {
        this.modelRegistry = modelRegistry;
        this.memoryService = memoryService;
        this.contentRetriever = contentRetriever;
    }

    /** 非流式对话 */
    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            String model = modelRegistry.resolveModel(request.getModel());
            String conversationId = resolveConversationId(request);
            log.info("Chat model={} session={}", model, conversationId);

            ChatAssistant assistant = modelRegistry.getAssistant(model);
            String result = assistant.chat(request.getMessage(), conversationId);

            int msgTokens = memoryService.estimateTokens(AiMessage.from(result));
            var stats = memoryService.getTokenStats(conversationId);

            return ChatResponse.builder()
                    .content(result).model(model).conversationId(conversationId)
                    .tokenCount(msgTokens).totalTokens(stats.getTotalTokens())
                    .timestamp(System.currentTimeMillis()).build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** RAG 增强对话 — 检索知识库内容作为上下文注入 */
    public Mono<ChatResponse> ragChat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            String model = modelRegistry.resolveModel(request.getModel());
            String conversationId = resolveConversationId(request);

            // 检索相关文档片段
            List<Content> contents = contentRetriever.retrieve(Query.from(request.getMessage()));
            log.info("RAG retrieve query={} found={} results", request.getMessage(), contents.size());
            for (int i = 0; i < contents.size(); i++) {
                String snippet = contents.get(i).textSegment().text();
                log.debug("RAG result[{}] text={}", i,
                        snippet != null ? snippet.substring(0, Math.min(80, snippet.length())) : "null");
            }
            String context = contents.stream()
                    .map(Content::textSegment)
                    .map(TextSegment::text)
                    .collect(Collectors.joining("\n---\n"));

            // 组装增强 prompt
            String augmentedMessage;
            if (!context.isEmpty()) {
                log.info("RAG chat model={} session={} retrieved={} chunks", model, conversationId, contents.size());
                augmentedMessage = "参考资料：\n" + context + "\n\n用户问题：" + request.getMessage();
            } else {
                log.info("RAG chat model={} session={} no relevant docs found", model, conversationId);
                augmentedMessage = request.getMessage();
            }

            ChatAssistant assistant = modelRegistry.getAssistant(model);
            String result = assistant.chat(augmentedMessage, conversationId);

            int msgTokens = memoryService.estimateTokens(AiMessage.from(result));
            var stats = memoryService.getTokenStats(conversationId);

            return ChatResponse.builder()
                    .content(result).model(model).conversationId(conversationId)
                    .tokenCount(msgTokens).totalTokens(stats.getTotalTokens())
                    .timestamp(System.currentTimeMillis()).build();
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

    public int estimateTokens(String text) {
        return memoryService.estimateTokens(AiMessage.from(text));
    }

    private String resolveConversationId(ChatRequest request) {
        return StringUtils.hasText(request.getConversationId())
                ? request.getConversationId()
                : UUID.randomUUID().toString().substring(0, 8);
    }
}
