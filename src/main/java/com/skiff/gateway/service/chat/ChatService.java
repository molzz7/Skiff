package com.skiff.gateway.service.chat;

import com.skiff.gateway.model.dto.ChatRequest;
import com.skiff.gateway.model.dto.ChatResponse;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;

    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            log.debug("Processing chat, model={}", request.getModel());
            String result = chatModel.chat(request.getMessage());
            return ChatResponse.builder()
                    .content(result)
                    .model(request.getModel() != null ? request.getModel() : "default")
                    .timestamp(System.currentTimeMillis())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
