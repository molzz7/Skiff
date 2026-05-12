/**
 * Skiff AI Chat — 多模型 + Token 监控 + 流式/非流式
 */
(function () {
    var STORAGE_KEY = 'skiff_conversations';

    // DOM
    var btnNew = document.getElementById('btn-new-chat');
    var convList = document.getElementById('conversation-list');
    var messagesEl = document.getElementById('messages');
    var userInput = document.getElementById('user-input');
    var btnSend = document.getElementById('btn-send');
    var toggleStream = document.getElementById('toggle-stream');
    var modelSpan = document.getElementById('current-model');
    var modelSelect = document.getElementById('model-select');
    var tokenBar = document.getElementById('token-bar');

    // 状态
    var conversations = [];
    var activeConvId = null;
    var isStreaming = false;
    var selectedModel = '';
    var maxTokens = 8000;

    // ==================== 初始化 ====================
    async function init() {
        await loadModels();
        loadConversations();
        if (conversations.length === 0) { newConversation(); }
        else { switchConversation(conversations[0].id); }
        renderConversationList();
        updateModelDisplay();
    }

    // ==================== 模型加载 ====================
    async function loadModels() {
        try {
            var resp = await fetch('/api/models');
            var json = await resp.json();
            if (json.code === 200 && json.data) {
                var models = json.data.available || [];
                var def = json.data.default || '';
                modelSelect.innerHTML = '';
                models.forEach(function (m) {
                    var opt = document.createElement('option');
                    opt.value = m; opt.textContent = m;
                    if (m === def) opt.selected = true;
                    modelSelect.appendChild(opt);
                });
                selectedModel = modelSelect.value || def;
            }
        } catch (e) { console.error('Failed to load models:', e); }
    }

    modelSelect.onchange = function () { selectedModel = this.value; updateModelDisplay(); };

    function updateModelDisplay() {
        modelSpan.textContent = '模型: ' + selectedModel;
    }

    // ==================== Token 条 ====================
    function renderTokenBar(totalTokens, maxTk) {
        if (maxTk) maxTokens = maxTk;
        var pct = Math.min(100, Math.round((totalTokens / maxTokens) * 100));
        var cls = pct > 90 ? 'danger' : (pct > 70 ? 'warning' : '');
        var rem = Math.max(0, maxTokens - totalTokens);
        tokenBar.innerHTML =
            '<span class="token-text">' + totalTokens + ' / ' + maxTokens + ' tok</span>' +
            '<div class="token-progress">' +
              '<div class="token-progress-fill ' + cls + '" style="width:' + pct + '%"></div>' +
            '</div>' +
            '<span class="token-text">剩' + rem + '</span>';
    }

    async function refreshTokens() {
        if (!activeConvId) return;
        try {
            var resp = await fetch('/api/chat/session/' + activeConvId + '/tokens');
            var json = await resp.json();
            if (json.code === 200 && json.data) {
                renderTokenBar(json.data.totalTokens, json.data.maxTokens);
                // 给已有消息补上 token 数
                if (json.data.messages) {
                    json.data.messages.forEach(function (m) {
                        var el = document.getElementById('msg-tokens-' + m.index);
                        if (el && m.tokens > 0) {
                            el.textContent = m.tokens + ' tok';
                        }
                    });
                }
            }
        } catch (e) { /* ignore */ }
    }

    // ==================== 对话管理 ====================
    function newConversation() {
        var id = 'conv_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8);
        var conv = { id: id, title: '新对话', messages: [] };
        conversations.unshift(conv);
        activeConvId = id;
        saveConversations();
        renderConversationList();
        renderMessages();
        renderTokenBar(0, maxTokens);
        userInput.focus();
    }

    function switchConversation(id) {
        activeConvId = id;
        renderConversationList();
        renderMessages();
        refreshTokens();
        userInput.focus();
    }

    function loadConversations() {
        try { conversations = JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; }
        catch (e) { conversations = []; }
    }

    function saveConversations() {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
    }

    function getActiveConv() {
        return conversations.find(function (c) { return c.id === activeConvId; });
    }

    function renderConversationList() {
        convList.innerHTML = '';
        conversations.forEach(function (conv) {
            var div = document.createElement('div');
            div.className = 'conv-item' + (conv.id === activeConvId ? ' active' : '');
            div.textContent = conv.title || '新对话';
            div.onclick = function () { switchConversation(conv.id); };
            convList.appendChild(div);
        });
    }

    // ==================== 消息渲染 ====================
    function renderMessages() {
        var conv = getActiveConv();
        messagesEl.innerHTML = '';
        if (conv && conv.messages.length > 0) {
            conv.messages.forEach(function (msg, i) {
                appendMessageBubble(msg.role, msg.content, msg.model, i, msg.tokens);
            });
        }
        scrollBottom();
    }

    function appendMessageBubble(role, content, model, index, tokens) {
        var row = document.createElement('div');
        row.className = 'message-row ' + role;
        var avatar = document.createElement('div');
        avatar.className = 'msg-avatar';
        avatar.textContent = role === 'user' ? 'U' : 'A';
        var wrap = document.createElement('div');
        wrap.style.flex = '1';
        var text = document.createElement('div');
        text.className = 'msg-content';
        text.textContent = content || '';
        wrap.appendChild(text);

        // 元信息：模型 + token
        var meta = document.createElement('div');
        meta.className = 'msg-meta';
        if (model && role === 'assistant') {
            var m = document.createElement('span');
            m.textContent = model;
            meta.appendChild(m);
        }
        var tok = document.createElement('span');
        tok.id = 'msg-tokens-' + (typeof index !== 'undefined' ? index : '');
        tok.textContent = (tokens && tokens > 0) ? tokens + ' tok' : '';
        meta.appendChild(tok);
        wrap.appendChild(meta);

        row.appendChild(avatar);
        row.appendChild(wrap);
        messagesEl.appendChild(row);
        scrollBottom();
        return text;
    }

    function setStreamingCursor(el, active) {
        if (active) el.classList.add('streaming');
        else el.classList.remove('streaming');
    }

    function scrollBottom() { messagesEl.scrollTop = messagesEl.scrollHeight; }

    // ==================== 发送消息 ====================
    async function sendMessage() {
        if (isStreaming) return;
        var message = userInput.value.trim();
        if (!message) return;
        var conv = getActiveConv();
        if (!conv) return;

        if (conv.messages.length === 0) {
            conv.title = message.substring(0, 30);
            saveConversations();
            renderConversationList();
        }

        var msgIndex = conv.messages.length;
        conv.messages.push({ role: 'user', content: message });
        appendMessageBubble('user', message, null, msgIndex, null);
        userInput.value = ''; userInput.style.height = 'auto';
        saveConversations();

        var asstIndex = conv.messages.length;
        var assistantMsg = { role: 'assistant', content: '', model: selectedModel, tokens: 0 };
        conv.messages.push(assistantMsg);
        saveConversations();
        var assistantEl = appendMessageBubble('assistant', '', selectedModel, asstIndex, 0);

        setSending(true);
        if (toggleStream.checked) {
            await streamChat(message, conv.id, assistantMsg, assistantEl, asstIndex);
        } else {
            await normalChat(message, conv.id, assistantMsg, assistantEl, asstIndex);
        }
        setSending(false);
        refreshTokens();
        userInput.focus();
    }

    function setSending(sending) {
        isStreaming = sending;
        btnSend.disabled = sending;
        userInput.disabled = sending;
    }

    // ==================== 非流式 ====================
    async function normalChat(message, conversationId, assistantMsg, assistantEl, index) {
        try {
            var resp = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: message, conversationId: conversationId, model: selectedModel })
            });
            var json = await resp.json();
            if (json.code === 200 && json.data) {
                assistantMsg.content = json.data.content;
                assistantMsg.tokens = json.data.tokenCount || 0;
                assistantEl.textContent = json.data.content;
                updateMsgTokens(index, json.data.tokenCount || 0);
                renderTokenBar(json.data.totalTokens || 0, maxTokens);
                saveConversations();
            } else {
                assistantMsg.content = '[错误] ' + (json.message || '未知');
                assistantEl.textContent = assistantMsg.content;
                saveConversations();
            }
        } catch (e) {
            assistantMsg.content = '[网络错误] ' + e.message;
            assistantEl.textContent = assistantMsg.content;
            saveConversations();
        }
    }

    // ==================== 流式 ====================
    async function streamChat(message, conversationId, assistantMsg, assistantEl, index) {
        setStreamingCursor(assistantEl, true);
        try {
            var resp = await fetch('/api/chat/stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: message, conversationId: conversationId, model: selectedModel })
            });
            var reader = resp.body.getReader();
            var decoder = new TextDecoder();
            var buffer = '';
            while (true) {
                var r = await reader.read();
                if (r.done) break;
                buffer += decoder.decode(r.value, { stream: true });
                var lines = buffer.split('\n');
                buffer = lines.pop() || '';
                for (var i = 0; i < lines.length; i++) {
                    if (lines[i].indexOf('data:') === 0) {
                        var token = lines[i].substring(5).trim();
                        if (token) {
                            assistantMsg.content += token;
                            assistantEl.textContent = assistantMsg.content;
                            scrollBottom();
                        }
                    }
                }
            }
            if (buffer.indexOf('data:') === 0) {
                var token = buffer.substring(5).trim();
                if (token) { assistantMsg.content += token; assistantEl.textContent = assistantMsg.content; }
            }
            // 估算 token 数 (~4 char/token)
            assistantMsg.tokens = Math.ceil(assistantMsg.content.length / 4);
            updateMsgTokens(index, assistantMsg.tokens);
        } catch (e) {
            assistantMsg.content += '\n[流式中断] ' + e.message;
            assistantEl.textContent = assistantMsg.content;
        } finally {
            setStreamingCursor(assistantEl, false);
            saveConversations();
        }
    }

    function updateMsgTokens(index, tokens) {
        var el = document.getElementById('msg-tokens-' + index);
        if (el && tokens > 0) el.textContent = tokens + ' tok';
    }

    // ==================== 事件 ====================
    btnNew.onclick = newConversation;
    btnSend.onclick = sendMessage;
    userInput.onkeydown = function (e) {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
    };
    userInput.oninput = function () {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 160) + 'px';
    };
    toggleStream.onchange = function () {
        document.getElementById('stream-hint').textContent =
            this.checked ? '流式模式 · 对话自动记忆' : '普通模式 · 对话自动记忆';
    };

    init();
})();
