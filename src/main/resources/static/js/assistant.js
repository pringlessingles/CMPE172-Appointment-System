(() => {
    'use strict';
    const byId = id => document.getElementById(id);
    const form = byId('chat-form');
    const csrf = form.querySelector('input[type="hidden"]');
    let conversationId = null;
    let pending = null;
    let busy = false;
    function append(role, text) {
        const paragraph = document.createElement('p');
        const label = document.createElement('strong');
        label.textContent = role + ': ';
        paragraph.append(label, document.createTextNode(text));
        paragraph.style.whiteSpace = 'pre-wrap';
        byId('transcript').append(paragraph);
    }
    function setBusy(value) {
        busy = value;
        document.querySelectorAll('main button').forEach(button => { button.disabled = value; });
        byId('assistant-status').textContent = value ? 'Working…' : '';
    }
    function renderProposal(action) {
        pending = action;
        byId('proposal').hidden = !action;
        if (action) {
            byId('proposal-summary').textContent = action.summary;
            byId('proposal-expiry').textContent = 'Expires at ' + action.expiresAt + ' (server local time).';
        }
        byId('current-conversation').textContent = conversationId ? 'Conversation ID: ' + conversationId : '';
    }
    async function request(url, body) {
        const options = { credentials: 'same-origin', headers: {} };
        if (body !== undefined) {
            options.method = 'POST';
            options.headers['Content-Type'] = 'application/json';
            options.headers['X-CSRF-TOKEN'] = csrf.value;
            options.body = JSON.stringify(body);
        }
        const response = await fetch(url, options);
        const text = await response.text();
        let result;
        try { result = text ? JSON.parse(text) : null; } catch (_) { result = null; }
        if (!response.ok) throw new Error(result?.message || 'Request failed. Please log in again or retry.');
        return result;
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy) return;
        const message = byId('chat-message').value.trim();
        if (!message) return;
        setBusy(true);
        renderProposal(null);
        append('You', message);
        byId('chat-message').value = '';
        try {
            const reply = await request(form.action, { conversationId, message });
            conversationId = reply.conversationId;
            append('Assistant', reply.answer);
            renderProposal(reply.pendingAction);
        } catch (error) { append('Notice', error.message); }
        finally { setBusy(false); }
    });
    byId('confirm-action').addEventListener('click', async () => {
        if (busy || !pending) return;
        const confirmationId = pending.id;
        setBusy(true);
        try {
            const reply = await request(`/api/ai/conversations/${encodeURIComponent(conversationId)}/confirm`, { confirmationId });
            append('Clinic', reply.answer);
        } catch (error) { append('Notice', error.message + ' Refresh your appointments before trying again.'); }
        finally { renderProposal(null); setBusy(false); }
    });
    byId('discard-action').addEventListener('click', async () => {
        if (busy || !pending) return;
        setBusy(true);
        try {
            await request(`/api/ai/conversations/${encodeURIComponent(conversationId)}/discard`, {});
            renderProposal(null);
            append('Clinic', 'Proposal discarded.');
        } catch (error) { append('Notice', error.message); }
        finally { setBusy(false); }
    });
    byId('new-conversation').addEventListener('click', async () => {
        if (busy) return;
        setBusy(true);
        try {
            if (conversationId) await request(`/api/ai/conversations/${encodeURIComponent(conversationId)}/discard`, {});
            conversationId = null;
            byId('transcript').replaceChildren();
            renderProposal(null);
        } catch (error) { append('Notice', error.message); }
        finally { setBusy(false); }
    });
    byId('restore-form').addEventListener('submit', async event => {
        event.preventDefault();
        if (busy) return;
        const id = byId('conversation-id').value.trim();
        if (!id) return;
        setBusy(true);
        try {
            const history = await request(`/api/ai/conversations/${encodeURIComponent(id)}`);
            conversationId = id;
            byId('transcript').replaceChildren();
            history.messages.forEach(turn => append(turn.role === 'USER' ? 'You' : 'Assistant', turn.content));
            renderProposal(history.pendingAction);
        } catch (error) { append('Notice', error.message); }
        finally { setBusy(false); }
    });
})();
