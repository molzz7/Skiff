package com.skiff.gateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 用户消息内容 */
    private String message;

    /** 可选模型名称，不传使用默认模型 */
    private String model;

    /** 可选对话ID，用于多轮对话 */
    private String conversationId;
}
