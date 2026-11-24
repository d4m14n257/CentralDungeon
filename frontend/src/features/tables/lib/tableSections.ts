import type { TableSectionKey, TableSections } from '../types'

/** The four optional sections, in the order the form offers them and the detail shows them. */
export const TABLE_SECTION_KEYS = ['characterCreation', 'whatToExpect', 'equipment', 'safety'] as const satisfies readonly TableSectionKey[]

/**
 * The sections as the request sends them, from what the form holds: an empty editor is a section the
 * master did not write, so it travels as null rather than as an empty string.
 *
 * @param values the form's values, with each section as the editor left it
 * @returns the four sections, ready for the create or edit request
 */
export function sectionsToRequest(values: Partial<Record<TableSectionKey, string | undefined>>): TableSections {
  return {
    characterCreation: values.characterCreation ? values.characterCreation : null,
    whatToExpect: values.whatToExpect ? values.whatToExpect : null,
    equipment: values.equipment ? values.equipment : null,
    safety: values.safety ? values.safety : null,
  }
}
