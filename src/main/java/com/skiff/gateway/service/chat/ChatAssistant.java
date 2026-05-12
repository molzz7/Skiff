package com.skiff.gateway.service.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 声明式 AI 助理接口 — 由 LangChain4j AiServices 自动生成实现
 * <pre>
 *   chat()    → 非流式对话，框架自动注入 ChatModel
 *   stream()  → 流式对话，框架自动注入 StreamingChatModel
 *   @MemoryId → 框架按 conversationId 自动分配独立的 ChatMemory
 * </pre>
 */
public interface ChatAssistant {

    @SystemMessage("你是一个有帮助的AI助手，请用简洁专业的语言回答用户问题")
    String chat(@UserMessage String userMessage, @MemoryId String conversationId);

    @SystemMessage("你是一个有帮助的AI助手，请用简洁专业的语言回答用户问题")
    TokenStream stream(@UserMessage String userMessage, @MemoryId String conversationId);
}
