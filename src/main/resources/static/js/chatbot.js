// Uses the global `username` from dashboard.js (loaded before this file) and `token`.
// Do not redeclare them here.

const CHAT_MAX_MESSAGES = 50;

function chatStorageKey() {
    let who = 'guest';
    try { if (typeof username !== 'undefined' && username) who = username; } catch (e) { /* ignore */ }
    return 'portalChat_' + who;
}

function chatOpenKey() {
    return chatStorageKey() + '_open';
}

function loadChatHistory() {
    try {
        const raw = sessionStorage.getItem(chatStorageKey());
        return raw ? JSON.parse(raw) : [];
    } catch (e) {
        return [];
    }
}

function saveChatMessage(text, sender) {
    try {
        const history = loadChatHistory();
        history.push({ text, sender });
        while (history.length > CHAT_MAX_MESSAGES) history.shift();
        sessionStorage.setItem(chatStorageKey(), JSON.stringify(history));
    } catch (e) {
        console.error('Could not save chat history:', e);
    }
}

function clearAllChatStorage() {
    try {
        Object.keys(sessionStorage)
            .filter(k => k.startsWith('portalChat_'))
            .forEach(k => sessionStorage.removeItem(k));
    } catch (e) { /* ignore */ }
}

function setChatWindowOpen(isOpen) {
    document.getElementById('chatbotWindow').style.display = isOpen ? 'flex' : 'none';
    try { sessionStorage.setItem(chatOpenKey(), isOpen ? '1' : '0'); } catch (e) { /* ignore */ }
}

function injectChatbotWidget() {
    const widgetHtml = `
        <div id="chatbotToggle" style="position: fixed; bottom: 20px; right: 20px; width: 56px; height: 56px; border-radius: 50%; background: #0d6efd; color: white; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 2px 8px rgba(0,0,0,0.3); z-index: 1000; font-size: 24px;">
            💬
        </div>
        <div id="chatbotWindow" style="display: none; position: fixed; bottom: 90px; right: 20px; width: 340px; height: 460px; background: white; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.3); z-index: 1000; flex-direction: column; overflow: hidden;">
            <div style="background: #0d6efd; color: white; padding: 12px 16px; font-weight: bold; display: flex; justify-content: space-between; align-items: center;">
                Portal Assistant
                <span>
                    <span id="chatbotClear" title="Clear chat" style="cursor: pointer; font-size: 12px; font-weight: normal; margin-right: 12px;">Clear</span>
                    <span id="chatbotClose" style="cursor: pointer;">&times;</span>
                </span>
            </div>
            <div id="chatbotMessages" style="flex: 1; overflow-y: auto; padding: 12px; font-size: 14px;"></div>
            <form id="chatbotForm" style="display: flex; border-top: 1px solid #ddd;">
                <input type="text" id="chatbotInput" placeholder="Ask something..." style="flex: 1; border: none; padding: 10px; outline: none;" required>
                <button type="submit" style="border: none; background: #0d6efd; color: white; padding: 0 16px;">Send</button>
            </form>
        </div>
    `;
    document.body.insertAdjacentHTML('beforeend', widgetHtml);

    // Restore the saved conversation and the open/closed state
    loadChatHistory().forEach(m => addChatMessage(m.text, m.sender, false));
    let wasOpen = false;
    try { wasOpen = sessionStorage.getItem(chatOpenKey()) === '1'; } catch (e) { /* ignore */ }
    if (wasOpen) setChatWindowOpen(true);

    document.getElementById('chatbotToggle').addEventListener('click', () => {
        const win = document.getElementById('chatbotWindow');
        setChatWindowOpen(win.style.display === 'none');
    });

    document.getElementById('chatbotClose').addEventListener('click', () => {
        setChatWindowOpen(false);
    });

    document.getElementById('chatbotClear').addEventListener('click', () => {
        try { sessionStorage.removeItem(chatStorageKey()); } catch (e) { /* ignore */ }
        document.getElementById('chatbotMessages').innerHTML = '';
    });

    // Wipe saved chats on logout so the next person on this computer never sees them
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', clearAllChatStorage);
    }

    document.getElementById('chatbotForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        const input = document.getElementById('chatbotInput');
        const message = input.value.trim();
        if (!message) return;

        addChatMessage(message, 'user');
        input.value = '';

        const loadingId = addChatMessage('Thinking...', 'bot', false);

        try {
            const response = await fetch('/api/chatbot/ask', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ message })
            });

            let data = null;
            try { data = await response.json(); } catch (ignored) { /* no JSON body */ }

            if (response.ok && data && data.reply) {
                document.getElementById(loadingId).textContent = data.reply;
                saveChatMessage(data.reply, 'bot');
            } else {
                document.getElementById(loadingId).textContent =
                    'Sorry, the assistant could not answer right now. Please try again in a moment.';
            }
        } catch (err) {
            document.getElementById(loadingId).textContent = 'Sorry, something went wrong.';
            console.error('Chatbot error:', err);
        }
    });
}

// save = false is used when restoring old messages and for the "Thinking..." placeholder
function addChatMessage(text, sender, save = true) {
    const messagesDiv = document.getElementById('chatbotMessages');
    const id = 'msg-' + Date.now() + Math.random().toString(36).substring(2, 7);

    const bubble = document.createElement('div');
    bubble.id = id;
    bubble.style.margin = '6px 0';
    bubble.style.padding = '8px 12px';
    bubble.style.borderRadius = '10px';
    bubble.style.maxWidth = '80%';
    bubble.style.whiteSpace = 'pre-wrap';

    if (sender === 'user') {
        bubble.style.background = '#e7f1ff';
        bubble.style.marginLeft = 'auto';
        bubble.style.textAlign = 'right';
    } else {
        bubble.style.background = '#f1f1f1';
    }

    bubble.textContent = text;
    messagesDiv.appendChild(bubble);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;

    if (save && sender === 'user') {
        saveChatMessage(text, sender);
    }

    return id;
}

// Auto-inject on every page that loads this script, once the DOM is ready
document.addEventListener('DOMContentLoaded', injectChatbotWidget);