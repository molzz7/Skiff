/**
 * Skiff AI Chat - 对话管理 & API 调用
 */
(function () {
    const STORAGE_KEY = 'skiff_conversations';

    // DOM
    const btnNew = document.getElementById('btn-new-chat');
    const convList = document.getElementById('conversation-list');
    const messagesEl = document.getElementById('messages');
    const userInput = document.getElementById('user-input');
    const btnSend = document.getElementById('btn-send');
    const toggleStream = document.getElementById('toggle-stream');
    const modelSpan = document.getElementById('current-model');

    // 状态
    let conversations = [];
    let activeConvId = null;
    let isStreaming = false;

    // ==================== 初始化 ====================
    function init() {
        loadConversations();
        if (conversations.length === 0) {
            newConversation();
        } else {
            switchConversation(conversations[0].id);
        }
        renderConversationList();
    }

    // ==================== 对话管理 ====================
    function newConversation() {
        const id = 'conv_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8);
        const conv = { id, title: '新对话', messages: [] };
        conversations.unshift(conv);
        activeConvId = id;
        saveConversations();
        renderConversationList();
        renderMessages();
        userInput.focus();
    }

    function switchConversation(id) {
        activeConvId = id;
        renderConversationList();
        renderMessages();
        userInput.focus();
    }

    function loadConversations() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            conversations = raw ? JSON.parse(raw) : [];
        } catch (e) {
            conversations = [];
        }
    }

    function saveConversations() {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
    }

    function getActiveConv() {
        return conversations.find(c => c.id === activeConvId);
    }

    function renderConversationList() {
        convList.innerHTML = '';
        conversations.forEach(conv => {
            const div = document.createElement('div');
            div.className = 'conv-item' + (conv.id === activeConvId ? ' active' : '');
            div.textContent = conv.title || '新对话';
            div.onclick = () => switchConversation(conv.id);
            convList.appendChild(div);
        });
    }

    // ==================== 消息渲染 ====================
    function renderMessages() {
        const conv = getActiveConv();
        messagesEl.innerHTML = '';
        if (conv && conv.messages.length > 0) {
            conv.messages.forEach(msg => appendMessageBubble(msg.role, msg.content));
        }
        scrollBottom();
    }

    function appendMessageBubble(role, content) {
        const row = document.createElement('div');
        row.className = 'message-row ' + role;

        const avatar = document.createElement('div');
        avatar.className = 'msg-avatar';
        avatar.textContent = role === 'user' ? 'U' : 'A';

        const text = document.createElement('div');
        text.className = 'msg-content';
        text.textContent = content;

        row.appendChild(avatar);
        row.appendChild(text);
        messagesEl.appendChild(row);
        scrollBottom();
        return text;
    }

    function setStreamingCursor(el, active) {
        if (active) el.classList.add('streaming');
        else el.classList.remove('streaming');
    }

    function scrollBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    // ==================== 发送消息 ====================
    async function sendMessage() {
        if (isStreaming) return;
        const message = userInput.value.trim();
        if (!message) return;

        const conv = getActiveConv();
        if (!conv) return;

        // 更新标题
        if (conv.messages.length === 0) {
            conv.title = message.substring(0, 30);
            saveConversations();
            renderConversationList();
        }

        // 显示用户消息
        conv.messages.push({ role: 'user', content: message });
        appendMessageBubble('user', message);
        userInput.value = '';
        userInput.style.height = 'auto';
        saveConversations();

        // 空占位用于流式填充
        const assistantMsg = { role: 'assistant', content: '' };
        conv.messages.push(assistantMsg);
        saveConversations();
        const assistantEl = appendMessageBubble('assistant', '');

        setSending(true);

        if (toggleStream.checked) {
            await streamChat(message, conv.id, assistantMsg, assistantEl);
        } else {
            await normalChat(message, conv.id, assistantMsg, assistantEl);
        }

        setSending(false);
        userInput.focus();
    }

    function setSending(sending) {
        isStreaming = sending;
        btnSend.disabled = sending;
        userInput.disabled = sending;
    }

    // ==================== 非流式 API ====================
    async function normalChat(message, conversationId, assistantMsg, assistantEl) {
        try {
            const resp = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message, conversationId })
            });
            const json = await resp.json();
            if (json.code === 200 && json.data) {
                assistantMsg.content = json.data.content;
                assistantEl.textContent = json.data.content;
                saveConversations();
            } else {
                assistantMsg.content = '[错误] ' + (json.message || '未知错误');
                assistantEl.textContent = assistantMsg.content;
                saveConversations();
            }
        } catch (e) {
            assistantMsg.content = '[网络错误] ' + e.message;
            assistantEl.textContent = assistantMsg.content;
            saveConversations();
        }
    }

    // ==================== 流式 SSE API ====================
    async function streamChat(message, conversationId, assistantMsg, assistantEl) {
        setStreamingCursor(assistantEl, true);
        try {
            const resp = await fetch('/api/chat/stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message, conversationId })
            });

            const reader = resp.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        const token = line.substring(5).trim();
                        if (token) {
                            assistantMsg.content += token;
                            assistantEl.textContent = assistantMsg.content;
                            scrollBottom();
                        }
                    }
                }
            }

            // 处理剩余 buffer
            if (buffer.startsWith('data:')) {
                const token = buffer.substring(5).trim();
                if (token) {
                    assistantMsg.content += token;
                    assistantEl.textContent = assistantMsg.content;
                }
            }
        } catch (e) {
            assistantMsg.content += '\n[流式中断] ' + e.message;
            assistantEl.textContent = assistantMsg.content;
        } finally {
            setStreamingCursor(assistantEl, false);
            saveConversations();
        }
    }

    // ==================== 事件绑定 ====================
    btnNew.onclick = newConversation;

    btnSend.onclick = sendMessage;

    userInput.onkeydown = function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    userInput.oninput = function () {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 160) + 'px';
    };

    toggleStream.onchange = function () {
        const hint = document.querySelector('.input-hint');
        hint.textContent = this.checked ? '流式模式 · 对话自动记忆' : '普通模式 · 对话自动记忆';
    };

    // 启动
    init();
})();
