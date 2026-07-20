---
name: SoloFirm Prisma Light
description: A cinematic evidence index with an ink-and-paper product interface.
colors:
  paper-canvas: "#F2F3EF"
  paper-surface: "#FBFBF8"
  paper-card: "#ECEEEB"
  ink: "#181A18"
  ink-muted: "#515752"
  ink-quiet: "#747B75"
  state-green: "#4F6F58"
  hero-cream: "#E1E0CC"
typography:
  display:
    fontFamily: "Bookman Old Style, URW Bookman, Georgia, serif"
    fontSize: "13.25rem"
    fontWeight: 500
    lineHeight: 0.85
    letterSpacing: "0"
  headline:
    fontFamily: "ZCOOL XiaoWei, STKaiti, KaiTi, serif"
    fontSize: "4rem"
    fontWeight: 400
    lineHeight: 0.95
    letterSpacing: "0"
  body:
    fontFamily: "Noto Serif SC, Songti SC, STSong, SimSun, serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0"
  label:
    fontFamily: "Noto Serif SC, Songti SC, STSong, SimSun, serif"
    fontSize: "0.75rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "0"
rounded:
  sm: "6px"
  md: "8px"
  hero: "32px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "40px"
  section: "96px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper-surface}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "12px 18px"
  button-secondary:
    backgroundColor: "{colors.paper-surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "12px 18px"
  input:
    backgroundColor: "{colors.paper-canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "14px 16px"
  data-surface:
    backgroundColor: "{colors.paper-card}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "24px"
---

# Design System: SoloFirm Prisma Light

## Overview

**Creative North Star: "The Cinematic Paper Index"**

The public home opens with Prisma's inset cinematic Hero: the supplied video keeps playing inside the rounded frame, while a processed still from 5.5 seconds extends atmosphere into the narrow outer Hero background. Every section below returns to a quiet paper surface so research content remains readable and work-focused.

The interface combines Prisma's composition, grain, staggered reveals, four-column feature rhythm, and pill controls with a restrained light product system. All public routes use the same token layer. No route may fall back to the old blue gradients, glows, shadows, or mismatched component styles.

**Key Characteristics:**
- Playing inset Hero video with a locally optimized outer-frame still.
- Pure light paper surfaces below the Hero, with low-contrast grain only where Prisma requires texture.
- Bookman Old Style for English display, Songti for reading, and Kaiti/Xiaowei for expressive Chinese headings.
- Grayscale buttons, ink typography, quiet green checks and state feedback.
- One Prisma component vocabulary across home, directories, lists, details, sources, and login.

## Colors

The system uses pale neutral paper layers and ink-dark text. Green is semantic, not decorative.

### Primary
- **Ink:** Primary text, active navigation, primary buttons, chart lines, and selected pagination.

### Secondary
- **Paper Surface:** Main content surface, secondary buttons, inputs, inner data rows, and table bodies.

### Tertiary
- **State Green:** Checks, focus feedback, success messages, and selected chart signals only.

### Neutral
- **Paper Canvas:** Public route background and page gutters.
- **Paper Card:** Feature cards, analytics cards, summary blocks, and inactive navigation hover.
- **Muted Ink:** Descriptions, metadata, and secondary labels.
- **Hero Cream:** Text and controls placed directly over the cinematic Hero.

**The Grayscale Command Rule.** Commands are black, white, or gray. Green never fills a button.

**The White Below Rule.** About, features, analytics, contact, and footer remain on light paper. Hero imagery never leaks into lower sections.

## Typography

**English Display Font:** Bookman Old Style (with URW Bookman and Georgia fallbacks)

**Chinese Display Font:** ZCOOL XiaoWei or Kaiti

**Chinese Body Font:** Noto Serif SC or system Songti

**Character:** Display type has sharp serif authority and clear stroke contrast. Product copy stays calm and readable. Rounded geometric type is prohibited.

### Hierarchy
- **Hero Display** (500, fixed breakpoint scale, 0.85): SoloFirm wordmark only.
- **Editorial Headline** (400, 2rem to 4rem, 0.95): About and section statements.
- **Product Title** (700, 1.25rem to 2rem, 1.1): Routes and card headings.
- **Body** (400, 1rem, 1.5): Descriptions and records, normally limited to 65 to 75 characters.
- **Label** (700, 0.75rem, 1.2): Metadata, filters, navigation, and state text.

**The No Rounded Type Rule.** Almarai and other rounded geometric display faces are forbidden in the public redesign.

## Elevation

The public system is flat. Paper layers, one-pixel borders, spacing, and media overlays create depth. Persistent drop shadows, text shadows, gradient text, glow, and glass effects are prohibited.

**The Flat Archive Rule.** A resting component has no shadow. Hover uses a small transform or tonal shift, never a glow.

## Components

### Buttons
- **Primary:** Ink background, paper text, full pill, no gradient, no shadow.
- **Secondary:** Paper background, ink text, gray border, full pill.
- **Tertiary:** Transparent background, ink text, no colored fill.
- **States:** Hover adjusts tone or gap; focus uses a clear ink outline; disabled preserves readable labels at lower opacity.

### Chips
- Paper background, ink text, gray border, explicit text state. Capability pills must never fade below readable contrast.

### Cards / Containers
- Feature and analytics cards share an 8px corner, paper-card background, gray border, no shadow, and consistent padding.
- Analytics use four columns on desktop, two on tablet, and one on mobile. Internal rows use paper-surface rather than dark nested panels.

### Inputs / Fields
- Pale paper fill, ink text, visible gray border, 6px corners, and an ink focus ring. Placeholder text remains clearly readable.

### Navigation
- Hero uses Prisma's hanging dark pill with five public destinations.
- Working routes keep the existing sidebar and mobile drawer behavior, restyled with ink active state and paper inactive states.

### SoloFirm Hero
- The supplied Hero video remains autoplaying, looped, muted, and inline.
- A local 5.5-second still is used only on the outer Hero background.
- `SoloFirm*` is a single cream color with no gradient, text shadow, filter, or background plate.

### Public Data Components
- Buttons, filters, custom selects, chips, pagination, tables, list rows, badges, empty states, errors, detail metadata, and login controls all use the same Prisma token and state vocabulary.
- Every pre-existing workflow, route, field, source link, export, analytics block, and contact action remains present.

## Do's and Don'ts

### Do:
- **Do** preserve every existing SoloFirm function, route, filter, control, API call, source link, content block, authentication action, and SVG brand mark.
- **Do** keep Hero video playback and the lower feature-card video unchanged.
- **Do** use the local Hero still only on the outer Hero background.
- **Do** maintain readable ink contrast on every light card and control.
- **Do** use black, white, and gray for all command buttons.
- **Do** verify public routes at desktop, tablet, and mobile widths.

### Don't:
- **Don't** remove a feature because it does not fit the new composition.
- **Don't** use blue or multicolor button fills, gradient text, glow, text shadows, or legacy dashboard surfaces.
- **Don't** allow one route or micro-component to retain the old visual system.
- **Don't** use green as a decorative button fill.
- **Don't** put the Hero image behind About, features, analytics, contact, or footer.
- **Don't** hide content through low-opacity reveal states or insufficient text contrast.
