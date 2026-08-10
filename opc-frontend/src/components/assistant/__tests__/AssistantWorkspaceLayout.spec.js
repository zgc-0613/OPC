import { describe, expect, it } from 'vitest'

import workspaceSource from '@/views/AssistantView.vue?raw'
import layoutSource from '@/layouts/AssistantLayout.vue?raw'
import inspectorSource from '@/components/assistant/AssistantInspector.vue?raw'
import composerSource from '@/components/assistant/AssistantComposer.vue?raw'
import progressSource from '@/components/assistant/AssistantRunProgress.vue?raw'
import citationDrawerSource from '@/components/assistant/AssistantCitationDrawer.vue?raw'
import evidencePanelSource from '@/components/assistant/AssistantEvidencePanel.vue?raw'
import historySidebarSource from '@/components/assistant/AssistantHistorySidebar.vue?raw'
import sessionMenuSource from '@/components/assistant/AssistantSessionMenu.vue?raw'
import profileSource from '@/components/assistant/AssistantResearchProfile.vue?raw'

describe('Assistant workspace layout contract', () => {
  it('allocates a bounded desktop inspector column only when the inspector is open', () => {
    expect(workspaceSource).toMatch(/\.assistant-workspace\.inspector-open\s*\{[^}]*grid-template-columns:\s*276px\s+minmax\(0,1fr\)\s+minmax\(300px,360px\)/)
    expect(workspaceSource).toMatch(/\.assistant-workspace\.history-collapsed\.inspector-open\s*\{[^}]*grid-template-columns:\s*64px\s+minmax\(0,1fr\)\s+minmax\(300px,360px\)/)
  })

  it('leaves the inspector body as the only scroll owner', () => {
    expect(inspectorSource).toMatch(/:deep\(\.research-profile\)[^{]*\{[^}]*overflow:\s*visible/)
    expect(inspectorSource).toMatch(/:deep\(\.research-task\)[^{]*\{[^}]*overflow:\s*visible/)
    expect(inspectorSource).toMatch(/\.assistant-inspector-body\s*\{[^}]*overflow-x:\s*hidden[^}]*overflow-y:\s*auto/)
  })

  it('keeps the new-research starter scrollable inside the bounded desk', async () => {
    const starterSource = (await import('@/components/assistant/AssistantResearchStarter.vue?raw')).default

    expect(starterSource).toMatch(/\.research-starter\s*\{[^}]*flex:\s*1\s+1\s+auto/)
    expect(starterSource).toMatch(/\.research-starter\s*\{[^}]*min-height:\s*0/)
    expect(starterSource).toMatch(/\.research-starter\s*\{[^}]*overflow-y:\s*auto/)
  })

  it('uses static status cues instead of perpetual workspace motion', () => {
    for (const source of [workspaceSource, composerSource, progressSource, citationDrawerSource, evidencePanelSource, historySidebarSource]) {
      expect(source).not.toMatch(/animation:[^;]*\binfinite\b/)
      expect(source).not.toMatch(/transition:\s*all\b/)
    }
  })

  it('does not delay keyboard-operated disclosures, while pointer-owned drawers retain a bounded transition', () => {
    for (const source of [citationDrawerSource, evidencePanelSource]) {
      expect(source).not.toMatch(/animation:\s*(?!none)/)
      expect(source).not.toMatch(/transition:\s+(?!none\b)[^;]+/)
    }
    expect(historySidebarSource).toMatch(/\.history-sidebar\.is-mobile-motion\s*\{[^}]*transition:\s*transform/)
    expect(inspectorSource).toMatch(/<Transition :name="motion \? 'assistant-inspector' : ''">/)
    expect(inspectorSource).toMatch(/\.assistant-inspector-enter-active \.assistant-inspector/)
    expect(inspectorSource).toMatch(/@media\(prefers-reduced-motion:reduce\)/)
    expect(sessionMenuSource).toMatch(/\.session-menu\.motion-enabled \.session-menu-popover\{[^}]*transition:/)
  })

  it('carries pointer intent from layout commands through the workspace before starting a drawer transition', () => {
    expect(layoutSource).toMatch(/@pointerdown="markHistoryPointer"/)
    expect(layoutSource).toMatch(/@keydown="clearHistoryMotion"/)
    expect(layoutSource).toMatch(/@pointerdown="markEvidencePointer"/)
    expect(layoutSource).toMatch(/@keydown="clearEvidenceMotion"/)
    expect(workspaceSource).toMatch(/:mobile-motion="mobileHistoryMotion"/)
    expect(workspaceSource).toMatch(/:motion="inspectorMotion"/)
    expect(workspaceSource).toMatch(/@close="closeInspector\(true, \$event\)"/)
    expect(workspaceSource).toMatch(/function shouldAnimateWorkspaceMotion\(event\)/)
    expect(workspaceSource).toMatch(/<Transition name="assistant-toast">/)
  })

  it('coordinates the desktop history rail and its content as one pointer-owned transition', () => {
    expect(workspaceSource).toMatch(/'history-motion': historyMotionPhase/)
    expect(workspaceSource).toMatch(/--assistant-drawer-duration:\s*500ms/)
    expect(workspaceSource).toMatch(/\.assistant-workspace\.history-motion\s*\{[^}]*transition:\s*grid-template-columns/)
    expect(workspaceSource).not.toMatch(/history-motion-(?:collapsing|expanding) \.research-desk/)
    expect(workspaceSource).not.toMatch(/transform:\s*translateX\(212px\)/)
    expect(workspaceSource).toMatch(/function toggleHistory\(event = null\)/)
    expect(historySidebarSource).toMatch(/motionPhase: \{ type: String, default: '' \}/)
    expect(historySidebarSource).toMatch(/motionPhase === 'collapsing'/)
  })

  it('keeps the native rail border visible while the grid track moves', () => {
    expect(historySidebarSource).not.toMatch(/\.history-sidebar::after/)
    expect(historySidebarSource).not.toMatch(/border-right-color:\s*transparent/)
    expect(historySidebarSource).toMatch(/border-right:\s*1px solid #c9cdc7/)
    expect(historySidebarSource).not.toMatch(/\.history-sidebar:is\(\.is-motion-collapsing,\s*\.is-motion-expanding\)\s*\{[^}]*width:\s*276px/)
    expect(historySidebarSource).not.toMatch(/\.history-sidebar\.is-motion-collapsing\s*>\s*\.history-extended-content\s*\{[^}]*width:\s*276px/)
  })

  it('settles the new-research icon before the collapsing rail begins', () => {
    expect(workspaceSource).toMatch(/\.assistant-workspace\.history-motion-collapsing\s*\{[^}]*transition:\s*grid-template-columns[^;]*120ms/)
    expect(historySidebarSource).toMatch(/\.history-sidebar\.is-motion-collapsing\s*>\s*\.new-research\s*\{[^}]*width:\s*44px[^}]*margin:\s*12px 0 12px 10px/)
    expect(historySidebarSource).toMatch(/\.history-sidebar\.is-motion-collapsing\s+\.new-research span\s*\{[^}]*display:\s*none/)
  })

  it('restores a full-width new-research command inside mobile and tablet history drawers', () => {
    expect(workspaceSource).toMatch(/@media\(max-width:840px\)\{\.assistant-workspace,\.assistant-workspace\.history-collapsed\{grid-template-columns:1fr/)
    expect(historySidebarSource).toMatch(/@media \(max-width: 840px\)[\s\S]*?\.history-sidebar\.is-collapsed:not\(\.is-motion-collapsing\):not\(\.is-motion-expanding\) \.new-research\s*\{[^}]*width:\s*calc\(100% - 24px\)/)
    expect(inspectorSource).toMatch(/@media\(max-width:1023px\)\{\.assistant-inspector-layer\{position:fixed/)
  })

  it('keeps the research-conditions fork command on one intentional line at compact inspector widths', () => {
    expect(profileSource).toMatch(/\.secondary-command\s*\{[^}]*white-space:\s*nowrap/)
    expect(profileSource).toMatch(/@container research-profile \(max-width:540px\)[\s\S]*?\.secondary-command\s*\{[^}]*font-size:/)
  })
})
