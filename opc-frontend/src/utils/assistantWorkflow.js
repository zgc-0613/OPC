export function createLatestRequestGate() {
  let current = 0
  return {
    begin() {
      current += 1
      return current
    },
    isCurrent(requestId) {
      return requestId === current
    },
  }
}

export function decideIndustryResolution(resolution, originalText, selectedTagId, rejectedKey) {
  if (!resolution?.tagId) {
    return { action: 'unresolved', selection: null, suggestion: null }
  }
  if (String(selectedTagId || '') === String(resolution.tagId)) {
    return { action: 'selected', selection: null, suggestion: null }
  }
  const suggestion = {
    tagId: resolution.tagId,
    name: resolution.name,
    method: resolution.method || 'unresolved',
    confidence: Number(resolution.confidence || 0),
    requiresConfirmation: Boolean(resolution.requiresConfirmation),
    originalText: String(originalText || '').trim(),
  }
  const mustConfirm = suggestion.requiresConfirmation || suggestion.method === 'fuzzy'
  if (mustConfirm) {
    if (rejectedKey && rejectedKey === industrySuggestionKey(suggestion)) {
      return { action: 'rejected', selection: null, suggestion: null }
    }
    return { action: 'confirm', selection: null, suggestion }
  }
  return { action: 'accept', selection: confirmIndustrySuggestion(suggestion), suggestion: null }
}

export function confirmIndustrySuggestion(suggestion) {
  return {
    industryTagId: String(suggestion.tagId),
    industry: suggestion.name,
    query: suggestion.name,
  }
}

export function industrySuggestionKey(suggestion) {
  return `${normalize(suggestion.originalText)}:${String(suggestion.tagId)}`
}

export function readinessPresentation(status, flags = {}) {
  if (flags.loading || flags.error) {
    return { canSubmit: false, warning: false }
  }
  return {
    canSubmit: status === 'sufficient' || status === 'partial',
    warning: status === 'partial',
  }
}

function normalize(value) {
  return String(value || '')
    .normalize('NFKC')
    .toLocaleLowerCase()
    .replace(/[\s\p{P}\p{S}]+/gu, '')
}
