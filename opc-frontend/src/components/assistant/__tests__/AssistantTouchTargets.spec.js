import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantCitationDrawer from '@/components/assistant/AssistantCitationDrawer.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantResearchProfile from '@/components/assistant/AssistantResearchProfile.vue'
import AssistantSessionMenu from '@/components/assistant/AssistantSessionMenu.vue'
import drawerSource from '@/components/assistant/AssistantCitationDrawer.vue?raw'
import profileSource from '@/components/assistant/AssistantResearchProfile.vue?raw'
import sessionMenuSource from '@/components/assistant/AssistantSessionMenu.vue?raw'

const profile = {
  ventureType: 'solo_company', regionId: '42', industryTagId: '7', industry: '人工智能应用',
  stage: 'validation', budgetRange: 'under_100k', goal: '', existingResources: '',
}

describe('Assistant tablet coarse-pointer controls', () => {
  it('keeps the key Assistant controls operable with 44px tablet targets', () => {
    const research = mount(AssistantResearchProfile, {
      props: { modelValue: profile, editable: true, industries: [{ tagId: 7, name: '人工智能应用' }] },
    })
    const menu = mount(AssistantSessionMenu, {
      props: { session: { sessionId: 1, title: '研究' }, scope: 'active' },
    })
    const composer = mount(AssistantComposer, { props: { modelValue: '研究问题' } })
    const drawer = mount(AssistantCitationDrawer, {
      attachTo: document.body,
      props: { open: true, mode: 'citations', citations: [] },
    })

    expect(research.get('summary').exists()).toBe(true)
    expect(research.get('.industry-combobox-toggle').attributes('aria-label')).toBeTruthy()
    expect(menu.get('summary').attributes('aria-label')).toBeTruthy()
    expect(composer.get('.send-command').attributes('aria-label')).toBeTruthy()
    expect(document.querySelector('.citation-drawer header button').getAttribute('aria-label')).toBeTruthy()

    const coarseQuery = /@media\s*\(min-width:641px\)\s*and\s*\(max-width:1023px\)\s*and\s*\(pointer:coarse\)/
    expect(profileSource).toMatch(coarseQuery)
    expect(sessionMenuSource).toMatch(coarseQuery)
    expect(drawerSource).toMatch(coarseQuery)
    drawer.unmount()
  })
})
