package com.skiff.gateway.service.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 带自动摘要压缩的 ChatMemory
 * <p>
 * 流程：消息累积 → token 超 80% 阈值 → 调用模型摘要前半段 → 替换为 SystemMessage 摘要
 * 压缩后的消息数始终控制在 maxTokens 以内。
 */
@Slf4j
public class CompressingChatMemory implements ChatMemory {

    private final Object id;
    private final ChatMemoryStore store;
    private final TokenCountEstimator estimator;
    private final int maxTokens;
    private final double threshold;
    private final ChatModel compressModel;
    private List<ChatMessage> messages;
    private boolean hasSummary;

    public CompressingChatMemory(Object id, ChatMemoryStore store,
                                  TokenCountEstimator estimator,
                                  int maxTokens, double threshold,
                                  ChatModel compressModel) {
        this.id = id;
        this.store = store;
        this.estimator = estimator;
        this.maxTokens = maxTokens;
        this.threshold = threshold;
        this.compressModel = compressModel;
        this.messages = new ArrayList<>(store.getMessages(id));
        this.hasSummary = messages.stream().anyMatch(m -> m instanceof SystemMessage
                && ((SystemMessage) m).text().startsWith("[对话摘要]"));
    }

    @Override
    public Object id() { return id; }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        int total = estimator.estimateTokenCountInMessages(messages);

        // 达到压缩阈值时触发摘要压缩
        if (total > (int) (threshold * maxTokens) && !hasSummary && messages.size() > 4) {
            compress();
        }

        // 压缩后再次超限则硬截断最旧消息
        while (estimator.estimateTokenCountInMessages(messages) > maxTokens && messages.size() > 1) {
            ChatMessage removed = messages.remove(0);
            log.debug("Trimming message from session {} (tokens still over limit)", id);
            if (removed instanceof SystemMessage s && s.text().startsWith("[对话摘要]")) {
                hasSummary = false;
            }
        }

        store.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    @Override
    public void clear() {
        messages.clear();
        hasSummary = false;
        store.deleteMessages(id);
    }

    /** 获取当前 token 用量 */
    public int currentTokens() {
        return estimator.estimateTokenCountInMessages(messages);
    }

    /** 最大 token 窗口 */
    public int maxTokens() {
        return maxTokens;
    }

    /** 以 AI 消息为边界估算每条消息的 token */
    public int estimateMessageTokens(ChatMessage msg) {
        return estimator.estimateTokenCountInMessage(msg);
    }

    private void compress() {
        int split = messages.size() / 2;
        List<ChatMessage> oldMessages = new ArrayList<>(messages.subList(0, split));
        List<ChatMessage> recentMessages = new ArrayList<>(messages.subList(split, messages.size()));

        StringBuilder prompt = new StringBuilder();
        prompt.append("请用1-3句话总结以下对话内容，保留其中的人名、关键事实和决定：\n\n");
        for (ChatMessage m : oldMessages) {
            String role = switch (m.type()) {
                case USER -> "用户";
                case AI -> "助手";
                case SYSTEM -> "系统";
                default -> "" + m.type();
            };
            String text = extractText(m);
            if (text.length() > 200) text = text.substring(0, 200) + "...";
            prompt.append("[").append(role).append("]: ").append(text).append("\n");
        }
        prompt.append("\n总结：");

        try {
            String summary = compressModel.chat(prompt.toString());
            log.info("Compressed session {}: {} msgs → summary ({} tokens)",
                    id, oldMessages.size(), estimator.estimateTokenCountInText(summary));

            messages = new ArrayList<>();
            messages.add(SystemMessage.from("[对话摘要] " + summary));
            messages.addAll(recentMessages);
            hasSummary = true;
        } catch (Exception e) {
            log.warn("Compression failed for session {}, skipping", id, e);
        }
    }

    private String extractText(ChatMessage m) {
        return switch (m.type()) {
            case USER -> ((dev.langchain4j.data.message.UserMessage) m).singleText();
            case AI -> {
                String t = ((dev.langchain4j.data.message.AiMessage) m).text();
                yield t != null ? t : "";
            }
            case SYSTEM -> ((SystemMessage) m).text();
            default -> "";
        };
    }
}
