const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'summary',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function trapFocus(event, container) {
  if (event.key !== 'Tab' || !container) return
  const focusable = [...container.querySelectorAll(FOCUSABLE)]
    .filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true')
  if (!focusable.length) {
    event.preventDefault()
    container.focus?.()
    return
  }
  const first = focusable[0]
  const last = focusable.at(-1)
  const active = document.activeElement
  if (event.shiftKey && (active === first || !container.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

export function isolateDialogBranch(branch, exempt = []) {
  if (!branch?.parentElement) return () => {}
  const exemptions = new Set(exempt.filter(Boolean))
  const changed = []
  let current = branch

  while (current?.parentElement) {
    const parent = current.parentElement
    for (const sibling of parent.children) {
      if (sibling === current || exemptions.has(sibling)) continue
      changed.push({
        element: sibling,
        hadAttribute: sibling.hasAttribute('inert'),
        value: sibling.getAttribute('inert'),
      })
      sibling.setAttribute('inert', '')
    }
    if (parent === document.body) break
    current = parent
  }

  let released = false
  return () => {
    if (released) return
    released = true
    for (const { element, hadAttribute, value } of changed.reverse()) {
      if (hadAttribute) element.setAttribute('inert', value ?? '')
      else element.removeAttribute('inert')
    }
  }
}
