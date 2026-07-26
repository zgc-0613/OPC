const PREFIX = 'opc_assistant:'
const NEW_RESEARCH = 'new'
const MAX_DRAFT_LENGTH = 8000

export function createAssistantDraftStore(storage = globalThis.localStorage, namespace = 'anonymous') {
  const storageNamespace = normalizeNamespace(namespace)
  return {
    load(sessionId) {
      return storage?.getItem(key(storageNamespace, sessionId)) || ''
    },
    save(sessionId, value) {
      if (!storage) return
      const draft = String(value || '').slice(0, MAX_DRAFT_LENGTH)
      if (draft) storage.setItem(key(storageNamespace, sessionId), draft)
      else storage.removeItem(key(storageNamespace, sessionId))
    },
    clear(sessionId) {
      storage?.removeItem(key(storageNamespace, sessionId))
    },
    loadPendingStart() {
      try {
        return JSON.parse(storage?.getItem(pendingStartKey(storageNamespace)) || 'null')
      } catch {
        storage?.removeItem(pendingStartKey(storageNamespace))
        return null
      }
    },
    savePendingStart(value) {
      if (!storage || !value?.idempotencyKey || !value?.fingerprint) return
      storage.setItem(pendingStartKey(storageNamespace), JSON.stringify({
        idempotencyKey: String(value.idempotencyKey),
        fingerprint: String(value.fingerprint),
      }))
    },
    clearPendingStart() {
      storage?.removeItem(pendingStartKey(storageNamespace))
    },
    loadPendingMessage(sessionId) {
      try {
        return JSON.parse(storage?.getItem(pendingMessageKey(storageNamespace, sessionId)) || 'null')
      } catch {
        storage?.removeItem(pendingMessageKey(storageNamespace, sessionId))
        return null
      }
    },
    savePendingMessage(sessionId, value) {
      if (!storage || !value?.idempotencyKey || !value?.fingerprint) return
      storage.setItem(pendingMessageKey(storageNamespace, sessionId), JSON.stringify({
        idempotencyKey: String(value.idempotencyKey),
        fingerprint: String(value.fingerprint),
      }))
    },
    clearPendingMessage(sessionId) {
      storage?.removeItem(pendingMessageKey(storageNamespace, sessionId))
    },
  }
}

function key(namespace, sessionId) {
  return `${PREFIX}${namespace}:draft:${sessionId == null || sessionId === '' ? NEW_RESEARCH : sessionId}`
}

function pendingStartKey(namespace) {
  return `${PREFIX}${namespace}:pending-start`
}

function pendingMessageKey(namespace, sessionId) {
  return `${PREFIX}${namespace}:pending-message:${sessionId}`
}

function normalizeNamespace(value) {
  return String(value || 'anonymous').replace(/[^A-Za-z0-9:_-]/g, '_')
}
