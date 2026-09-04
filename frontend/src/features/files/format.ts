/** The thresholds a size is expressed in. Binary units, because that is what a filesystem counts in. */
const KIB = 1024
const MIB = KIB * 1024

/**
 * A file size and the unit it should be read in.
 *
 * Returned as two pieces rather than one string so the caller renders the unit through `t()`: "KB"
 * and "MB" are text on screen like any other, and building the sentence here would put a hardcoded
 * word in a module that has no business writing one (regla dura 18).
 */
export interface FormattedSize {
  /** The number, already rounded and formatted for the reader's locale. */
  value: string
  /** Which unit key to render it with: `bytes`, `kilobytes` or `megabytes`. */
  unit: 'bytes' | 'kilobytes' | 'megabytes'
}

/**
 * Turns a byte count into something a person reads.
 *
 * **The locale is a parameter, never a constant read from inside** — same rule `lib/date.ts` follows
 * and the same reason (#111, #192): a module that reaches for the browser's locale on its own cannot
 * be tested and cannot be reused for anybody but the current reader.
 *
 * The number it is given is the size **as uploaded**, before compression (#75): what is shown has to
 * be what the person recognises, not what the disk happens to hold.
 *
 * @param sizeBytes the size in bytes
 * @param locale    the reader's locale, e.g. `es` or `en`
 * @returns the number and the unit key to render it with
 */
export function formatFileSize(sizeBytes: number, locale: string): FormattedSize {
  if (sizeBytes < KIB) {
    return { value: new Intl.NumberFormat(locale).format(sizeBytes), unit: 'bytes' }
  }
  if (sizeBytes < MIB) {
    return { value: new Intl.NumberFormat(locale).format(Math.round(sizeBytes / KIB)), unit: 'kilobytes' }
  }
  return {
    value: new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(sizeBytes / MIB),
    unit: 'megabytes',
  }
}
