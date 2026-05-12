package com.skiff.gateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** AI回复内容 */
    private String content;

    /** 实际使用的模型名称 */
    private String model;

    /** 会话ID，用于多轮对话 */
    private String conversationId;

    /** 本条回复消耗的 token 数 */
    private Integer tokenCount;

    /** 当前会话已用 token 总数 */
    private Integer totalTokens;

    /** 响应时间戳（毫秒） */
    private long timestamp;
}
