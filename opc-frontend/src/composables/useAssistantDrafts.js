const PREFIX = 'opc_assistant_draft:'
const NEW_RESEARCH = 'new'
const MAX_DRAFT_LENGTH = 8000

export function createAssistantDraftStore(storage = globalThis.localStorage) {
  return {
    load(sessionId) {
      return storage?.getItem(key(sessionId)) || ''
    },
    save(sessionId, value) {
      if (!storage) return
      const draft = String(value || '').slice(0, MAX_DRAFT_LENGTH)
      if (draft) storage.setItem(key(sessionId), draft)
      else storage.removeItem(key(sessionId))
    },
    clear(sessionId) {
      storage?.removeItem(key(sessionId))
    },
  }
}

function key(sessionId) {
  return `${PREFIX}${sessionId == null || sessionId === '' ? NEW_RESEARCH : sessionId}`
}
