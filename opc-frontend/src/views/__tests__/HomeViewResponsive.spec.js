import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const home = readFileSync('src/views/HomeView.vue', 'utf8')
const styles = readFileSync('src/styles/atlas.css', 'utf8')

describe('atlas homepage contract', () => {
  it('links the destination modules', () => {
    expect(home).toContain('atlas-hero')
    for (const path of ['/policies', '/cases', '/analysis', '/platforms', '/university-opc']) {
      expect(home).toContain('to="' + path + '"')
    }
    expect(styles).toContain("url('/media/opc-atlas-terrain.webp')")
  })

  it('keeps contact and source provenance accessible', () => {
    expect(home).toMatch(/href="mailto:[^"]+"/)
    expect(home).toContain('to="/sources"')
    expect(home).toMatch(/<footer class="atlas-home-footer">[\s\S]*<section class="atlas-home-contact"[\s\S]*<\/footer>/)
  })

  it('stages the first view and respects reduced motion', () => {
    expect(styles).toContain('@keyframes atlas-entry-rise')
    expect(styles).toMatch(/prefers-reduced-motion:\s*reduce[\s\S]*atlas-shell-home \.atlas-hero-image/s)
  })

  it('stacks the content grids on narrow screens', () => {
    expect(styles).toMatch(/@media \(max-width: 720px\)[\s\S]*\.atlas-path-grid, \.atlas-home-secondary \{ grid-template-columns: 1fr;/)
  })
})
