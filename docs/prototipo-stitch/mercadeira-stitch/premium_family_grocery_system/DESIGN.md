---
name: Premium Family Grocery System
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#3d4a3d'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#6d7b6c'
  outline-variant: '#bccbb9'
  surface-tint: '#006e2f'
  primary: '#006e2f'
  on-primary: '#ffffff'
  primary-container: '#22c55e'
  on-primary-container: '#004b1e'
  inverse-primary: '#4ae176'
  secondary: '#0058be'
  on-secondary: '#ffffff'
  secondary-container: '#2170e4'
  on-secondary-container: '#fefcff'
  tertiary: '#855300'
  on-tertiary: '#ffffff'
  tertiary-container: '#ef9900'
  on-tertiary-container: '#5c3800'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#6bff8f'
  primary-fixed-dim: '#4ae176'
  on-primary-fixed: '#002109'
  on-primary-fixed-variant: '#005321'
  secondary-fixed: '#d8e2ff'
  secondary-fixed-dim: '#adc6ff'
  on-secondary-fixed: '#001a42'
  on-secondary-fixed-variant: '#004395'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.05em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin: 20px
  touch_target_min: 56px
---

## Brand & Style

This design system is built to facilitate domestic harmony through a shared, effortless shopping experience. The brand personality is **approachable, organized, and refreshing**. It targets busy households that require a high degree of legibility and a low cognitive load during high-stress activities like grocery shopping.

The visual style is a blend of **Premium Minimalism** and **Modern Corporate** aesthetics. It prioritizes high-quality whitespace to create an "airy" feel, ensuring that even long lists do not feel cluttered. By utilizing soft geometric curves and a restricted, meaningful color palette, the system evokes a sense of reliability and freshness. The emotional goal is to transform a chore into a streamlined, satisfying ritual.

## Colors

The color strategy is functional and semiotic. The **Primary Green** (#22C55E) is the hero of the experience, symbolizing freshness and the "completed" state of an item in the cart. The **Secondary Blue** (#3B82F6) is reserved for collaborative features—indicating which family member added an item or highlighting shared comments.

The interface relies heavily on a **Light Mode** foundation using pure whites and subtle gray surfaces (#F9FAFB) to define hierarchy without heavy borders. Status colors are high-chroma but balanced, ensuring that "Pending" (Orange) and "Removal Requested" (Rose) are immediately distinguishable in a high-glare environment like a supermarket.

## Typography

The design system utilizes **Plus Jakarta Sans** for all typographic roles. This choice provides a friendly, contemporary geometric feel that aligns with the "premium" identity while remaining highly legible at small sizes.

Hierarchy is established through significant weight contrast. Headlines use a Bold weight (700) with slight negative letter spacing to feel compact and authoritative. Body text is set at 16px (Medium) or 18px (Large) to accommodate users of all ages, ensuring that list items are readable at arm's length while pushing a cart. Labels use a semi-bold weight and increased tracking to differentiate metadata from primary content.

## Layout & Spacing

The layout follows a **fluid grid** logic designed specifically for mobile-first consumption. A standard 20px side margin provides a generous safe area, while a 16px gutter separates cards and list elements.

The spacing rhythm is built on a 4px baseline, but emphasizes **large touch targets**. No interactive element should be smaller than 56px in height, ensuring ease of use for family members who may be multitasking. Vertical spacing between different categories of items is kept at 32px (xl) to maintain the airy, premium feel of the design system.

## Elevation & Depth

This design system uses **Tonal Layering** combined with **Ambient Shadows** to create a sense of organized depth. 

- **Level 0 (Background):** Pure white or #F9FAFB.
- **Level 1 (Cards):** Raised with a very soft, diffused shadow (Blur: 20px, Y: 4px, Opacity: 4% Black).
- **Level 2 (Modals/Overlays):** Elevated with a more pronounced shadow (Blur: 30px, Y: 8px, Opacity: 8% Primary Tint) to focus user attention.

Depth is also communicated through "Surface Containers"—subtle gray backgrounds for inactive states or grouped items—rather than harsh lines. This creates a tactile feel where elements seem to float gently above the base canvas.

## Shapes

The design system utilizes a **Pill-shaped (Level 3)** roundedness strategy to maximize the "friendly" and "family-oriented" brand perception.

All primary containers, such as list cards and input fields, utilize a `rounded-lg` (2rem) or `rounded-xl` (3rem) corner radius. This aggressive rounding removes any "sharp" or "industrial" feel, replacing it with a soft, organic aesthetic. Secondary elements like badges and checkboxes are fully rounded (pill-shaped) to reinforce the system's playful yet premium nature.

## Components

### Buttons
Primary buttons are high-contrast green (#22C55E) with a minimum height of 56px. They use a fully rounded (pill) shape and Bold typography. Secondary buttons use a soft blue tint with 10% opacity and blue text.

### Cards
Cards are the primary organizational unit. They use a 2xl corner radius, a subtle 1px border (#F1F5F9), and ambient shadows. Padding inside cards is a consistent 20px to maintain the airy layout.

### Lists & Items
List items feature a large checkbox on the left (fully rounded) and a "collaborator" avatar on the right if the item was added by someone else. Swiping an item reveals a "Removal Requested" state in Rose (#F43F5E).

### Status Badges
Badges are small, pill-shaped indicators with low-opacity backgrounds and high-opacity text.
- **Pending:** Amber background, Dark Amber text.
- **In Cart:** Green background, White text.

### Bottom Navigation
A modern, floating-style navigation bar with a high backdrop blur (Glassmorphism). The active state is indicated by a Primary Green icon and a subtle dot underneath, avoiding heavy background fills to keep the bottom of the screen light.

### Inputs
Search and "Add Item" fields are large, pill-shaped containers with a light gray surface (#F1F5F9) and placeholder text in a soft neutral gray.