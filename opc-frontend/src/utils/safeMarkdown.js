import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

const ALLOWED_TAGS = [
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'ul', 'ol', 'li', 'blockquote',
  'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'a', 'hr',
  'strong', 'em', 's', 'br',
]

export function renderSafeMarkdown(value, evidenceContext = {}) {
  const allowedRunId = normalizedId(evidenceContext.runId)
  const allowedSourceIds = new Set((evidenceContext.sourceIds || []).map(normalizedId).filter(Boolean))
  const source = linkEvidenceReferences(DOMPurify.sanitize(String(value || ''), {
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
  }).replace(/\]\(\s*(?:javascript|data):[^)]*\)/gi, '](#blocked)'), allowedRunId, allowedSourceIds)
  const rendered = markdown.render(source)
  const sanitized = DOMPurify.sanitize(rendered, {
    ALLOWED_TAGS,
    ALLOWED_ATTR: ['href', 'title', 'scope', 'align'],
    ALLOW_DATA_ATTR: false,
  })
  const template = document.createElement('template')
  template.innerHTML = sanitized
  template.content.querySelectorAll('a').forEach((link) => {
    const href = link.getAttribute('href') || ''
    const evidence = /^#evidence-([1-9]\d*)-([1-9]\d*)$/.exec(href)
    if (evidence && evidence[1] === allowedRunId && allowedSourceIds.has(evidence[2])) {
      link.removeAttribute('target')
      link.removeAttribute('rel')
      return
    }
    if (!/^https?:\/\//i.test(href)) {
      link.removeAttribute('href')
      link.removeAttribute('target')
      link.removeAttribute('rel')
      return
    }
    link.setAttribute('target', '_blank')
    link.setAttribute('rel', 'noopener noreferrer')
  })
  return template.innerHTML
}

function linkEvidenceReferences(source, runId, allowedSourceIds) {
  if (!runId || !allowedSourceIds.size) return source
  return source.replace(/\[来源\s+(\d+(?:\s*[、,，]\s*\d+)*)\]/g, (_match, values) => {
    const links = values.split(/[、,，]/).map((value) => value.trim()).filter(Boolean).map((sourceId) => (
      allowedSourceIds.has(sourceId) ? `[${sourceId}](#evidence-${runId}-${sourceId})` : sourceId
    ))
    return `来源 ${links.join('、')}`
  })
}

function normalizedId(value) {
  const text = String(value ?? '').trim()
  return /^[1-9]\d*$/.test(text) ? text : ''
}
