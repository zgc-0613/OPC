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
import taskSource from '@/components/assistant/AssistantResearchTask.vue?raw'
import preferencesSource from '@/components/assistant/AssistantResearchPreferences.vue?raw'
import reportsSource from '@/components/assistant/AssistantReportsPanel.vue?raw'

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

  it('starts the native rail and one clipped new-research command together', () => {
    expect(workspaceSource).toMatch(/historyCollapsed\.value\s*=\s*targetCollapsed/)
    expect(workspaceSource).not.toMatch(/HISTORY_BUTTON_STAGE_MS/)
    expect(historySidebarSource).toMatch(/class="new-research-slot"/)
    expect(historySidebarSource).toMatch(/class="new-research-label"/)
    expect(historySidebarSource).not.toMatch(/new-research-(?:wide|compact)/)
    expect(historySidebarSource).toMatch(/\.new-research\s*\{[^}]*width:\s*100%[^}]*overflow:\s*hidden/)
    expect(historySidebarSource).not.toMatch(/\.history-sidebar\.is-motion-(?:collapsing|expanding)[^{]*\{[^}]*width:\s*44px/)
  })

  it('holds extended history content in the shrinking rail until the transition tail', () => {
    expect(historySidebarSource).toMatch(/\.history-sidebar\.is-motion-collapsing \.history-extended-content-inner\s*\{[^}]*transition:\s*opacity 100ms var\(--ease-out\) 390ms/)
    expect(historySidebarSource).not.toMatch(/\.history-sidebar\.is-motion-collapsing \.history-extended-content-inner\s*\{[^}]*210ms/)
  })

  it('uses the wide new-research command inside mobile and tablet history drawers', () => {
    expect(workspaceSource).toMatch(/@media\(max-width:840px\)\{\.assistant-workspace,\.assistant-workspace\.history-collapsed\{grid-template-columns:1fr/)
    expect(historySidebarSource).toMatch(/\.new-research-slot\s*\{[^}]*padding:\s*12px 10px/)
    expect(historySidebarSource).toMatch(/\.new-research\s*\{[^}]*width:\s*100%/)
    expect(historySidebarSource).toMatch(/@media \(max-width: 840px\)[\s\S]*?\.history-sidebar\.is-collapsed:not\(\.is-motion-collapsing\):not\(\.is-motion-expanding\)\s*\{[^}]*align-items:\s*stretch/)
    expect(inspectorSource).toMatch(/@media\(max-width:1023px\)\{\.assistant-inspector-layer\{position:fixed/)
  })

  it('keeps the collapsed command as a 44px target without swapping button surfaces', () => {
    expect(historySidebarSource).toMatch(/class="history-sidebar-toggle"/)
    expect(historySidebarSource).not.toMatch(/class="sidebar-toggle"/)
    expect(historySidebarSource).toMatch(/\.history-sidebar-head\s*\{[^}]*flex:\s*0 0 64px[^}]*box-sizing:\s*border-box/)
    expect(historySidebarSource).toMatch(/\.new-research-slot\s*\{[^}]*flex:\s*0 0 68px[^}]*height:\s*68px/)
    expect(historySidebarSource).toMatch(/\.new-research\s*\{[^}]*height:\s*44px/)
    expect(historySidebarSource).toMatch(/\.new-research-content\s*\{[^}]*transition:\s*transform/)
  })

  it('uses inspector-width rules instead of only viewport rules for compact inspector content', () => {
    expect(inspectorSource).toMatch(/\.assistant-inspector-body\s*\{[^}]*container-type:\s*inline-size[^}]*container-name:\s*assistant-inspector/)
    expect(taskSource).toMatch(/@container assistant-inspector \(max-width:\s*540px\)/)
    expect(preferencesSource).toMatch(/@container assistant-inspector \(max-width:\s*540px\)/)
    expect(reportsSource).toMatch(/@container assistant-inspector \(max-width:\s*540px\)/)
  })

  it('keeps phone history controls at touch target size without relying on a tablet-only query', () => {
    expect(historySidebarSource).toMatch(/@media \(max-width: 640px\)[\s\S]*?\.history-search input,[\s\S]*?\.history-scopes button,[\s\S]*?\.history-rename,[\s\S]*?\.load-more\s*\{[^}]*min-height:\s*44px/)
  })

  it('keeps the research-conditions fork command on one intentional line at compact inspector widths', () => {
    expect(profileSource).toMatch(/\.secondary-command\s*\{[^}]*white-space:\s*nowrap/)
    expect(profileSource).toMatch(/@container research-profile \(max-width:540px\)[\s\S]*?\.secondary-command\s*\{[^}]*font-size:/)
  })
})
