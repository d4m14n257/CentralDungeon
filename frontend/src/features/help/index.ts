/**
 * The help of the system, as dialogs raised from the screen that needs them (#231).
 *
 * A screen names a section and nothing else: `<HelpLink section="masters.schedule">`. The catalogue
 * of what exists lives in `sections/registry.tsx`.
 */
export { HelpDialog } from './components/HelpDialog'
export { HelpLink } from './components/HelpLink'
export { HelpList, HelpSteps, HelpTerms } from './components/HelpBlocks'
export { HELP_SECTIONS } from './sections/registry'

export type { HelpDialogProps } from './components/HelpDialog'
export type { HelpLinkProps } from './components/HelpLink'
export type { HelpSectionDefinition, HelpSectionId } from './sections/registry'
