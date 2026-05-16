/**
 * 轻舟 Skiff AI Chat
 */
console.log('[Skiff] JS loaded');
(function () {
    var STORAGE_KEY = 'skiff_conversations';

    var btnNew, convList, messagesEl, userInput, btnSend, toggleStream,
        modelSpan, modelSelect, tokenBar,
        btnSettings, overlay, btnClose,
        setApiKey, setBaseUrl, setSearch, myModels, searchResults,
        btnTest, testResult, btnSave,
        btnFile, fileInput, btnRagToggle, btnClearKnowledge, docList;

    var conversations = [], activeConvId = null, isStreaming = false, ragMode = false,
        selectedModel = '', maxTokens = 8000, userModels = [], searchTimer = null;

    // ==================== DOM 查询 ====================
    function $id(id) { return document.getElementById(id); }

    function queryDom() {
        btnNew = $id('btn-new-chat'); convList = $id('conversation-list');
        messagesEl = $id('messages'); userInput = $id('user-input');
        btnSend = $id('btn-send'); toggleStream = $id('toggle-stream');
        modelSpan = $id('current-model'); modelSelect = $id('model-select');
        tokenBar = $id('token-bar');
        btnSettings = $id('btn-settings'); overlay = $id('settings-overlay');
        btnClose = $id('btn-close-settings');
        setApiKey = $id('set-apikey'); setBaseUrl = $id('set-baseurl');
        setSearch = $id('set-model-search'); myModels = $id('my-models');
        searchResults = $id('search-results');
        btnTest = $id('btn-test-conn'); testResult = $id('test-result');
        btnSave = $id('btn-save-settings');
        btnFile = $id('btn-file'); fileInput = $id('file-input');
        btnRagToggle = $id('btn-rag-toggle'); btnClearKnowledge = $id('btn-clear-knowledge');
        docList = $id('document-list');
    }

    // ==================== 事件绑定（立即生效） ====================
    function bindEvents() {
        if (btnSettings) btnSettings.onclick = function () { overlay && overlay.classList.remove('hidden'); };
        if (btnClose) btnClose.onclick = function () { overlay && overlay.classList.add('hidden'); };
        if (btnNew) btnNew.onclick = newConversation;
        if (btnSend) btnSend.onclick = sendMessage;
        if (userInput) {
            userInput.onkeydown = function (e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } };
            userInput.oninput = function () { this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 160) + 'px'; };
        }
        if (toggleStream) toggleStream.onchange = function () {
            var h = $id('stream-hint'); if (h) h.textContent = this.checked ? '流式模式 · 对话自动记忆' : '普通模式 · 对话自动记忆';
        };
        if (modelSelect) modelSelect.onchange = function () { selectedModel = this.value; updateModelDisplay(); };
        if (setSearch) setSearch.oninput = function () {
            clearTimeout(searchTimer);
            var q = this.value.trim();
            if (q.length < 1 && searchResults) { searchResults.innerHTML = ''; return; }
            searchTimer = setTimeout(function () { doSearch(q); }, 300);
        };
        if (btnTest) btnTest.onclick = testConnection;
        if (btnSave) btnSave.onclick = saveSettings;
        if (btnFile && fileInput) {
            btnFile.onclick = function () { fileInput.click(); };
            fileInput.onchange = uploadFile;
        }
        if (btnRagToggle) btnRagToggle.onclick = function () {
            ragMode = !ragMode;
            btnRagToggle.classList.toggle('active', ragMode);
            updateModelDisplay();
        };
        if (btnClearKnowledge) btnClearKnowledge.onclick = async function () {
            await fetch('/api/knowledge', { method: 'DELETE' });
            ragMode = false;
            if (btnRagToggle) btnRagToggle.classList.remove('active');
            updateModelDisplay();
            refreshDocList();
        };
        refreshDocList();
    }

    // ==================== 初始化 ====================
    async function init() {
        queryDom(); bindEvents();
        await loadSettings();
        loadConversations();
        if (conversations.length === 0) newConversation();
        else switchConversation(conversations[0].id);
        renderConversationList();
        updateModelDisplay();
    }

    // ==================== 设置 ====================
    async function loadSettings() {
        try {
            var r = await fetch('/api/settings'), j = await r.json();
            if (j.code === 200 && j.data) {
                userModels = j.data.models || [];
                if (setApiKey) setApiKey.value = j.data.apiKey || '';
                if (setBaseUrl) setBaseUrl.value = j.data.baseUrl || '';
            }
        } catch (e) { console.error('loadSettings:', e); }
        refreshModelSelect(); renderMyModels();
    }

    function refreshModelSelect() {
        if (!modelSelect) return;
        modelSelect.innerHTML = '';
        if (userModels.length === 0) { modelSelect.innerHTML = '<option value="">无可用模型</option>'; return; }
        userModels.forEach(function (m) {
            var o = document.createElement('option'); o.value = m; o.textContent = m;
            if (m === selectedModel || (userModels.length === 1 && !selectedModel)) o.selected = true;
            modelSelect.appendChild(o);
        });
        selectedModel = modelSelect.value || userModels[0];
        updateModelDisplay();
    }

    function renderMyModels() {
        if (!myModels) return;
        myModels.innerHTML = '';
        userModels.forEach(function (m) {
            var tag = document.createElement('span'); tag.className = 'model-tag';
            tag.innerHTML = m + '<span class="remove" data-model="' + m + '">×</span>';
            myModels.appendChild(tag);
        });
        myModels.querySelectorAll('.remove').forEach(function (el) {
            el.onclick = function () {
                userModels = userModels.filter(function (x) { return x !== el.dataset.model; });
                renderMyModels();
            };
        });
    }

    async function doSearch(q) {
        if (!searchResults || !q) return;
        try {
            var r = await fetch('/api/settings/models/search?q=' + encodeURIComponent(q)), j = await r.json();
            if (j.code !== 200 || !j.data) return;
            searchResults.innerHTML = '';
            j.data.forEach(function (m) {
                var div = document.createElement('div'); div.className = 'model-item';
                if (userModels.indexOf(m) >= 0) { div.innerHTML = '<span>' + m + '</span><span style="color:#8e8ea0;font-size:12px">已添加</span>'; }
                else {
                    div.innerHTML = '<span>' + m + '</span><span class="add">+</span>';
                    div.querySelector('.add').onclick = function () { if (userModels.indexOf(m) < 0) { userModels.push(m); renderMyModels(); } };
                }
                searchResults.appendChild(div);
            });
        } catch (e) { /* ignore */ }
    }

    async function testConnection() {
        if (!testResult) return;
        testResult.textContent = '测试中...'; testResult.className = '';
        try {
            var r = await fetch('/api/settings/test-connection'), j = await r.json();
            if (testResult) { testResult.textContent = j.data.message; testResult.className = j.data.ok ? 'ok' : 'fail'; }
        } catch (e) { if (testResult) { testResult.textContent = '请求失败: ' + e.message; testResult.className = 'fail'; } }
    }

    async function saveSettings() {
        try {
            var r = await fetch('/api/settings', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ apiKey: setApiKey.value, baseUrl: setBaseUrl.value, models: userModels })
            });
            var j = await r.json();
            if (j.code === 200) { overlay && overlay.classList.add('hidden'); refreshModelSelect(); newConversation(); }
        } catch (e) { alert('保存失败: ' + e.message); }
    }

    function updateModelDisplay() { if (modelSpan) modelSpan.textContent = '模型: ' + selectedModel + (ragMode ? ' [RAG]' : ''); }

    // ==================== 文档管理 ====================
    async function refreshDocList() {
        if (!docList) return;
        try {
            var r = await fetch('/api/knowledge'), j = await r.json();
            if (j.code !== 200 || !j.data || j.data.length === 0) {
                docList.innerHTML = '<div class="doc-empty">暂无文档</div>';
                if (j.data && j.data.length === 0 && ragMode) { ragMode = false; if (btnRagToggle) btnRagToggle.classList.remove('active'); updateModelDisplay(); }
                return;
            }
            docList.innerHTML = '';
            j.data.forEach(function (d) {
                var div = document.createElement('div'); div.className = 'doc-item';
                div.innerHTML = '<span class="doc-name" title="' + d.fileName + '">' + d.fileName + '</span>' +
                    '<span style="font-size:10px;color:#565656">' + d.chunks + '块</span>' +
                    '<span class="doc-del" data-id="' + d.id + '">×</span>';
                div.querySelector('.doc-del').onclick = function (e) {
                    e.stopPropagation();
                    deleteDoc(d.id);
                };
                docList.appendChild(div);
            });
        } catch (e) { docList.innerHTML = '<div class="doc-empty">加载失败</div>'; }
    }

    async function deleteDoc(id) {
        await fetch('/api/knowledge/' + id, { method: 'DELETE' });
        refreshDocList();
    }

    // ==================== 文件上传 ====================
    async function uploadFile() {
        var file = fileInput.files[0]; if (!file) return;
        var formData = new FormData(); formData.append('file', file);
        try {
            var r = await fetch('/api/knowledge/upload', { method: 'POST', body: formData });
            var j = await r.json();
            if (j.code === 200 && j.data) {
                ragMode = true;
                if (btnRagToggle) btnRagToggle.classList.add('active');
                updateModelDisplay();
                refreshDocList();
                appendBubble('system', '已上传: ' + j.data.fileName + '\n文档ID: ' + j.data.documentId + '，RAG 模式已开启');
            }
        } catch (e) { alert('上传失败: ' + e.message); }
        fileInput.value = '';
    }

    // ==================== Token ====================
    function renderTokenBar(totalTokens, maxTk) {
        if (!tokenBar) return;
        if (maxTk) maxTokens = maxTk;
        var pct = Math.min(100, Math.round(totalTokens / maxTokens * 100));
        var cls = pct > 90 ? 'danger' : (pct > 70 ? 'warning' : '');
        var rem = Math.max(0, maxTokens - totalTokens);
        tokenBar.innerHTML = '<span class="token-text">' + totalTokens + '/' + maxTokens + ' tok</span>' +
            '<div class="token-progress"><div class="token-progress-fill ' + cls + '" style="width:' + pct + '%"></div></div>' +
            '<span class="token-text">剩' + rem + '</span>';
    }

    async function refreshTokens() {
        if (!activeConvId) return;
        try {
            var r = await fetch('/api/chat/session/' + activeConvId + '/tokens'), j = await r.json();
            if (j.code === 200 && j.data) {
                renderTokenBar(j.data.totalTokens, j.data.maxTokens);
                if (j.data.messages) j.data.messages.forEach(function (m) {
                    var el = $id('msg-tokens-' + m.index); if (el && m.tokens > 0) el.textContent = m.tokens + ' tok';
                });
            }
        } catch (e) { /* ignore */ }
    }

    // ==================== 对话管理 ====================
    function newConversation() {
        var id = 'conv_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8);
        conversations.unshift({ id: id, title: '新对话', messages: [] });
        activeConvId = id;
        saveConversations(); renderConversationList(); renderMessages();
        renderTokenBar(0, maxTokens); if (userInput) userInput.focus();
    }

    function switchConversation(id) {
        activeConvId = id; renderConversationList(); renderMessages(); refreshTokens();
        if (userInput) userInput.focus();
    }

    function loadConversations() { try { conversations = JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; } catch (e) { conversations = []; } }
    function saveConversations() { localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations)); }
    function getActiveConv() { return conversations.find(function (c) { return c.id === activeConvId; }); }

    function renderConversationList() {
        if (!convList) return;
        convList.innerHTML = '';
        conversations.forEach(function (conv) {
            var div = document.createElement('div');
            div.className = 'conv-item' + (conv.id === activeConvId ? ' active' : '');
            div.onclick = function () { switchConversation(conv.id); };
            var t = document.createElement('span'); t.className = 'conv-title'; t.textContent = conv.title || '新对话';
            var d = document.createElement('button'); d.className = 'conv-delete'; d.textContent = '\u00D7';
            d.onclick = function (e) { e.stopPropagation(); deleteConversation(conv.id); };
            div.appendChild(t); div.appendChild(d); convList.appendChild(div);
        });
    }

    function deleteConversation(id) {
        fetch('/api/chat/session/' + id, { method: 'DELETE' }).catch(function () {});
        conversations = conversations.filter(function (c) { return c.id !== id; });
        if (id === activeConvId) { conversations.length > 0 ? switchConversation(conversations[0].id) : newConversation(); }
        saveConversations(); renderConversationList();
    }

    // ==================== 消息渲染 ====================
    function renderMessages() {
        if (!messagesEl) return;
        var conv = getActiveConv(); messagesEl.innerHTML = '';
        if (conv && conv.messages.length > 0) conv.messages.forEach(function (msg, i) { appendBubble(msg.role, msg.content, msg.model, i, msg.tokens); });
        scrollBottom();
    }

    function appendBubble(role, content, model, index, tokens) {
        var row = document.createElement('div'); row.className = 'message-row ' + role;
        var av = document.createElement('div'); av.className = 'msg-avatar';
        av.textContent = role === 'user' ? 'U' : (role === 'system' ? 'K' : 'A');
        var wrap = document.createElement('div'); wrap.style.flex = '1';
        var txt = document.createElement('div'); txt.className = 'msg-content'; txt.textContent = content || '';
        wrap.appendChild(txt);
        var meta = document.createElement('div'); meta.className = 'msg-meta';
        if (model && role === 'assistant') { var ms = document.createElement('span'); ms.textContent = model; meta.appendChild(ms); }
        var tok = document.createElement('span'); tok.id = 'msg-tokens-' + index; tok.textContent = (tokens > 0) ? tokens + ' tok' : '';
        meta.appendChild(tok); wrap.appendChild(meta); row.appendChild(av); row.appendChild(wrap);
        messagesEl.appendChild(row); scrollBottom();
        return txt;
    }

    function scrollBottom() { if (messagesEl) messagesEl.scrollTop = messagesEl.scrollHeight; }
    function setStreaming(el, on) { if (el) { on ? el.classList.add('streaming') : el.classList.remove('streaming'); } }

    // ==================== 发送消息 ====================
    async function sendMessage() {
        if (isStreaming) return;
        var msg = userInput ? userInput.value.trim() : ''; if (!msg) return;
        var conv = getActiveConv(); if (!conv) return;
        if (conv.messages.length === 0) { conv.title = msg.substring(0, 30); saveConversations(); renderConversationList(); }
        conv.messages.push({ role: 'user', content: msg });
        appendBubble('user', msg);
        if (userInput) { userInput.value = ''; userInput.style.height = 'auto'; }
        saveConversations();
        var aIdx = conv.messages.length, aMsg = { role: 'assistant', content: '', model: selectedModel, tokens: 0 };
        conv.messages.push(aMsg); saveConversations();
        var aEl = appendBubble('assistant', '', selectedModel, aIdx, 0);
        setSending(true);
        if (ragMode) {
            await ragChat(msg, conv.id, aMsg, aEl, aIdx);
        } else if (toggleStream && toggleStream.checked) {
            await streamChat(msg, conv.id, aMsg, aEl, aIdx);
        } else {
            await normalChat(msg, conv.id, aMsg, aEl, aIdx);
        }
        setSending(false); refreshTokens(); if (userInput) userInput.focus();
    }

    function setSending(s) { isStreaming = s; if (btnSend) btnSend.disabled = s; if (userInput) userInput.disabled = s; }

    async function normalChat(msg, cid, aMsg, aEl, idx) {
        try {
            var r = await fetch('/api/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: msg, conversationId: cid, model: selectedModel }) });
            var j = await r.json();
            if (j.code === 200 && j.data) {
                aMsg.content = j.data.content; aMsg.tokens = j.data.tokenCount || 0;
                if (aEl) aEl.textContent = j.data.content;
                updateMsgTokens(idx, j.data.tokenCount || 0);
                renderTokenBar(j.data.totalTokens || 0, maxTokens); saveConversations();
            }
        } catch (e) { aMsg.content = '[错误] ' + e.message; if (aEl) aEl.textContent = aMsg.content; saveConversations(); }
    }

    async function ragChat(msg, cid, aMsg, aEl, idx) {
        try {
            var r = await fetch('/api/chat/rag', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: msg, conversationId: cid, model: selectedModel }) });
            var j = await r.json();
            if (j.code === 200 && j.data) {
                aMsg.content = j.data.content; aMsg.tokens = j.data.tokenCount || 0;
                if (aEl) aEl.textContent = j.data.content;
                updateMsgTokens(idx, j.data.tokenCount || 0);
                renderTokenBar(j.data.totalTokens || 0, maxTokens); saveConversations();
            }
        } catch (e) { aMsg.content = '[错误] ' + e.message; if (aEl) aEl.textContent = aMsg.content; saveConversations(); }
    }

    async function streamChat(msg, cid, aMsg, aEl, idx) {
        setStreaming(aEl, true);
        try {
            var r = await fetch('/api/chat/stream', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: msg, conversationId: cid, model: selectedModel }) });
            var reader = r.body.getReader(), decoder = new TextDecoder(), buf = '';
            while (true) {
                var rd = await reader.read(); if (rd.done) break;
                buf += decoder.decode(rd.value, { stream: true });
                var lines = buf.split('\n'); buf = lines.pop() || '';
                for (var i = 0; i < lines.length; i++) {
                    if (lines[i].indexOf('data:') === 0) {
                        var tok = lines[i].substring(5).trim();
                        if (tok) { aMsg.content += tok; if (aEl) aEl.textContent = aMsg.content; scrollBottom(); }
                    }
                }
            }
            if (buf.indexOf('data:') === 0) { aMsg.content += buf.substring(5).trim(); if (aEl) aEl.textContent = aMsg.content; }
            aMsg.tokens = Math.ceil(aMsg.content.length / 4); updateMsgTokens(idx, aMsg.tokens);
        } catch (e) { aMsg.content += '\n[流式中断] ' + e.message; if (aEl) aEl.textContent = aMsg.content; }
        finally { setStreaming(aEl, false); saveConversations(); }
    }

    function updateMsgTokens(idx, tokens) {
        var el = $id('msg-tokens-' + idx); if (el && tokens > 0) el.textContent = tokens + ' tok';
    }

    init();
})();
