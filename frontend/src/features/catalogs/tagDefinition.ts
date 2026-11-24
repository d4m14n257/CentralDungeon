import { z } from 'zod'

import { TAG_CATEGORIES } from './tagCategories'
import type { DefineTagInput } from './types'

/** The column is 500 characters wide; the backend refuses anything longer. */
export const MAX_TAG_DESCRIPTION_LENGTH = 500

/** What the two fields hold while they are being edited. The description is the raw text typed. */
export type TagDefinitionDraft = Pick<DefineTagInput, 'category'> & { description: string }

/**
 * Turns a draft into what defining a tag sends: the description trimmed, and an empty one as none.
 * Over-long text fails, so the form can say so instead of the server.
 */
export const tagDefinitionSchema = z.object({
  category: z.enum(TAG_CATEGORIES).nullable(),
  description: z
    .string()
    .trim()
    .max(MAX_TAG_DESCRIPTION_LENGTH)
    .transform((text) => (text === '' ? null : text)),
})

/**
 * The draft a tag starts from: what its group already says.
 *
 * @param value the tag, or nothing for a blank draft
 * @returns the draft to edit
 */
export function draftOf(value?: Pick<DefineTagInput, 'category' | 'description'>): TagDefinitionDraft {
  return { category: value?.category ?? null, description: value?.description ?? '' }
}
