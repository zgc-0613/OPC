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

export function renderSafeMarkdown(value) {
  const source = DOMPurify.sanitize(String(value || ''), {
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
  }).replace(/\]\(\s*(?:javascript|data):[^)]*\)/gi, '](#blocked)')
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
