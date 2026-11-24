import i18n from '@/providers/i18n'
import type { UploadFailure } from '@/types/file'

/**
 * The text that names every refused file with its reason, to sit under the "these could not be
 * uploaded" notice that each flow words in its own way.
 *
 * @param failed the files the server refused, as a staged-list upload reports them
 * @returns one «name»: reason entry per file, in the order they were picked, joined with a space
 */
export function describeUploadFailures(failed: UploadFailure[]): string {
  return failed.map(({ name, reason }) => i18n.t('dropzone.refusedFile', { ns: 'files', name, reason })).join(' ')
}
