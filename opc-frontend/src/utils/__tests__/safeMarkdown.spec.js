import { describe, expect, it } from 'vitest'

import { renderSafeMarkdown } from '@/utils/safeMarkdown'

describe('safe assistant Markdown', () => {
  it('renders useful research structures', () => {
    const html = renderSafeMarkdown('# 结论\n\n- 政策一\n- 政策二\n\n| 地区 | 数量 |\n| --- | ---: |\n| 湖北 | 3 |')

    expect(html).toContain('<h1>结论</h1>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<table>')
  })

  it('removes raw HTML, event handlers and executable URLs', () => {
    const html = renderSafeMarkdown(`
      <script>alert(1)</script>
      <iframe src="https://evil.example"></iframe>
      [脚本](javascript:alert(1))
      [数据](data:text/html;base64,PHNjcmlwdD4=)
      <a href="https://safe.example" onclick="alert(1)">safe</a>
    `)

    expect(html).not.toMatch(/script|iframe|onclick|javascript:|data:/i)
  })

  it('hardens external http links', () => {
    const html = renderSafeMarkdown('[官方来源](https://example.gov.cn/policy)')

    expect(html).toContain('href="https://example.gov.cn/policy"')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })

  it('links only authorized evidence references within the current run', () => {
    const html = renderSafeMarkdown(
      '事实结论 [来源 2、9]',
      { runId: 301, sourceIds: [2] },
    )

    expect(html).toContain('href="#evidence-301-2"')
    expect(html).not.toContain('href="#evidence-301-9"')
  })
})
