package com.skiff.gateway.service.chat;

import com.skiff.gateway.model.dto.TokenStats;
import com.skiff.gateway.service.memory.CompressingChatMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话记忆统计服务 — 从共享的 ChatMemoryProvider 读取 token 信息
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final ChatMemoryProvider memoryProvider;
    private final TokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o");

    /** 获取会话 token 统计 */
    public TokenStats getTokenStats(String conversationId) {
        ChatMemory mem = memoryProvider.get(conversationId);
        List<ChatMessage> messages = mem.messages();
        int total = estimator.estimateTokenCountInMessages(messages);

        List<TokenStats.MessageTokens> msgTokens = new ArrayList<>();
        int totalFromMsgs = 0;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            int tokens = estimator.estimateTokenCountInMessage(msg);
            totalFromMsgs += tokens;
            String role = msg instanceof UserMessage ? "user" : "assistant";
            String preview = extractPreview(msg, 50);
            msgTokens.add(new TokenStats.MessageTokens(i, role, preview, tokens));
        }

        int maxTk = 8000;
        if (mem instanceof CompressingChatMemory cm) {
            maxTk = cm.maxTokens();
        }

        return TokenStats.builder()
                .conversationId(conversationId)
                .totalTokens(Math.max(total, totalFromMsgs))
                .maxTokens(maxTk)
                .remainingTokens(Math.max(0, maxTk - total))
                .messages(msgTokens)
                .build();
    }

    /** 估算单条消息 token */
    public int estimateTokens(ChatMessage msg) {
        return estimator.estimateTokenCountInMessage(msg);
    }

    private String extractPreview(ChatMessage msg, int maxLen) {
        String text = null;
        if (msg instanceof UserMessage um) text = um.singleText();
        else if (msg instanceof AiMessage am) text = am.text();
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
