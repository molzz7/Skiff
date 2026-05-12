package com.skiff.gateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStats {

    /** 会话ID */
    private String conversationId;

    /** 当前已用 token 数 */
    private int totalTokens;

    /** 最大 token 窗口 */
    private int maxTokens;

    /** 剩余可用 token */
    private int remainingTokens;

    /** 每条消息的 token 明细 */
    private List<MessageTokens> messages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageTokens {
        private int index;
        private String role;
        private String preview;
        private int tokens;
    }

    public static TokenStats empty() {
        return TokenStats.builder()
                .totalTokens(0).maxTokens(8000).remainingTokens(8000)
                .messages(Collections.emptyList())
                .build();
    }
}
