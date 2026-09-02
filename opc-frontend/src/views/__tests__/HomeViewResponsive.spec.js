import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const readSource = (relativePath) => readFileSync(new URL(relativePath, import.meta.url), 'utf8')

const homeSource = readSource('../HomeView.vue')
const globalStyles = readSource('../../styles/global.css')
const prismaStyles = readSource('../../styles/prisma.css')
const styles = `${globalStyles}\n${prismaStyles}`

const cssRules = (source) => {
  const rules = []
  const pattern = /([^{}]+)\{([^{}]*)\}/g
  let match

  while ((match = pattern.exec(source))) {
    rules.push({
      selector: match[1].replace(/\/\*[\s\S]*?\*\//g, '').replace(/\s+/g, ' ').trim(),
      declarations: match[2].replace(/\s+/g, ' ').trim(),
    })
  }

  return rules
}

const rulesMatching = (selectorPattern) =>
  cssRules(styles).filter(({ selector }) => selectorPattern.test(selector))

describe('HomeView responsive layout contract', () => {
  it('keeps the home shell fluid instead of reusing the 1680px archive cap', () => {
    const archiveShellRule = cssRules(styles).find(({ selector }) => selector === '.archive-shell')
    expect(archiveShellRule?.declarations).toMatch(/max-width\s*:\s*1680px/i)

    const homeShellOverride = cssRules(styles).find(({ selector }) => /\.archive-shell\.home-shell\s*$/.test(selector))
    expect(homeShellOverride).toBeDefined()
    expect(homeShellOverride.declarations).toMatch(/max-width\s*:\s*none/i)
    expect(styles).toMatch(
      /\.home-shell\s+\.(?:archive-main|content-shell)\s*\{[^}]*max-width\s*:\s*(?:none|100%)/is,
    )
  })

  it('uses stable minmax tracks for the desktop footer and reserves one-column layout for narrow screens', () => {
    const desktopFooterRules = rulesMatching(/\.home-shell\s+\.home-site-footer\b/).filter(
      ({ declarations }) => /grid-template-columns\s*:/i.test(declarations),
    )

    expect(desktopFooterRules.some(({ declarations }) =>
      /grid-template-columns\s*:\s*minmax\(\s*0\s*,[^)]+\)\s+minmax\(/i.test(declarations),
    )).toBe(true)

    expect(styles).toMatch(
      /@media\s*\(\s*max-width\s*:\s*(?:860|760)px\s*\)[\s\S]*?\.home-site-footer\s*\{[\s\S]*?grid-template-columns\s*:\s*1fr/is,
    )
  })

  it('allows the footer summary to wrap on wide and intermediate viewports', () => {
    const summaryRules = rulesMatching(/\.home-shell\s+\.home-footer-summary\b/)

    expect(summaryRules.some(({ declarations }) => /white-space\s*:\s*normal/i.test(declarations))).toBe(true)
    expect(summaryRules.some(({ declarations }) =>
      /overflow-wrap\s*:\s*(?:anywhere|break-word|normal)/i.test(declarations),
    )).toBe(true)
  })

  it('keeps the contact email as a real link without character-by-character compression', () => {
    expect(homeSource).toMatch(
      /<div\s+class="home-footer-contact"[\s\S]*?<a\s+href="mailto:[^"]+"/i,
    )

    const contactLinkRules = rulesMatching(/\.home-footer-contact[^{}]*\ba\b/i)
    expect(contactLinkRules.some(({ declarations }) => /word-break\s*:\s*normal/i.test(declarations))).toBe(true)
    expect(contactLinkRules.some(({ declarations }) =>
      /overflow-wrap\s*:\s*(?:normal|break-word)/i.test(declarations),
    )).toBe(true)
  })
})
